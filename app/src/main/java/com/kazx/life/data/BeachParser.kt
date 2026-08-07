package com.kazx.life.data

import com.kazx.life.net.Net
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 解析 beach.kazx.top 必吃榜页面。
 *
 * 店铺列表 a.card href="/shop/{id}"：
 *   - .card-img > img（封面）或 .card-img-ph（首字占位）
 *   - h3 店名
 *   - .rating > .stars-fg style="width:96%" + .rating-num (评分) + .muted (X人)
 *   - 地址文本：最后一段
 *   - .badge-claim: 已认领
 *
 * 店铺详情 /shop/{id}：
 *   - .detail-hero: 封面大图 + h1 + 评分 + 地址 + 电话/QQ
 *   - .menu-grid img 菜单图
 *   - .review-list .review-item 评价列表
 */
object BeachParser {

    private const val BASE = Net.BEACH_BASE

    fun parseShopList(html: String): List<BeachShop> {
        val doc = Jsoup.parse(html, BASE)
        return doc.select("a.card[href^=/shop/]").toList().map { parseShopCard(it) }
    }

    private fun parseShopCard(el: Element): BeachShop {
        val href = el.attr("href")
        val id = extractShopId(href)
        val coverImg = el.selectFirst(".card-img img")
        val cover = coverImg?.absUrl("src")?.ifEmpty { coverImg.attr("src") } ?: ""
        val coverText = el.selectFirst(".card-img-ph")?.text()?.trim()
            ?: id.getOrNull(0)?.toString() ?: ""
        val name = el.selectFirst("h3")?.text()?.trim() ?: ""
        val ratingNum = el.selectFirst(".rating-num:not(.muted)")?.text()?.trim()
        val rating = ratingNum?.toFloatOrNull() ?: -1f
        val countMatch = Regex("(\\d+)人").find(el.text())
        val rateCount = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        // 地址：取 card-body 中除店名/评分外的剩余文本末段
        val body = el.selectFirst(".card-body")
        val text = body?.text()?.replace(name, "")?.replace(ratingNum ?: "", "")
            ?.replace(Regex("\\(\\d+人\\)|$rateCount 人评分|期待评价"), " ")
            ?.replace("·", " ")
            ?.replace("已认领", " ")
            ?.replace("\\s+".toRegex(), " ")?.trim() ?: ""

        val claimed = el.selectFirst(".badge-claim, .claimed, [class*=claim]") != null ||
                el.text().contains("已认领")

        return BeachShop(
            id = id,
            name = name,
            cover = resolveUrl(cover),
            coverText = coverText,
            rating = rating,
            rateCount = rateCount,
            address = text,
            claimed = claimed
        )
    }

    fun parseShopDetail(html: String, shopId: String): BeachShopDetail? {
        val doc = Jsoup.parse(html, BASE)

        val name = doc.selectFirst("h1, .detail-title h1")?.text()?.trim() ?: ""
        if (name.isEmpty()) return null

        val hero = doc.selectFirst(".detail-hero")
        val coverImg = hero?.selectFirst("img#mainSlide, .slide-wrap img, img")
        val cover = coverImg?.absUrl("src")?.ifEmpty { coverImg.attr("src") } ?: ""
        val coverText = name.firstOrNull()?.toString() ?: ""

        val ratingNum = doc.selectFirst(".rating-num:not(.muted), #avgScore")?.text()?.trim()
        val rating = ratingNum?.toFloatOrNull() ?: -1f
        val countMatch = doc.selectFirst("#rateCount, .rating-num.muted")?.text()?.let {
            Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull()
        } ?: doc.select(".review-item, .review-list > div").size.let { if (it > 0) it else 0 }

        val address = extractInfo(doc, listOf("地址", "位置", "loc"),
            "出门|校门|巷子|马路|右转|左转|对面|直走|隔壁|向前|米|m").trim()

        val phoneMatch = Regex("电话[：:]\\s*([0-9\\-]{6,})").find(doc.body().wholeText())
        val qqMatch = Regex("QQ[：:]\\s*([0-9]{5,})").find(doc.body().wholeText())

        val claimed = doc.selectFirst(".badge-claim, [class*=claim]") != null ||
                doc.body().text().contains("已认领")

        val menus = doc.select(".menu-grid img, [class*=menu] img, .zoomable").toList()
            .mapNotNull { e ->
                val src = e.absUrl("src").ifEmpty { e.attr("src") }
                if (src.isNotEmpty()) resolveUrl(src) else null
            }.filter { it.contains("menu") || it.contains("upload") }

        val reviews = parseReviews(doc)

        return BeachShopDetail(
            id = shopId,
            name = name,
            cover = resolveUrl(cover),
            coverText = coverText,
            rating = rating,
            rateCount = countMatch,
            address = address,
            phone = phoneMatch?.groupValues?.get(1) ?: "",
            qq = qqMatch?.groupValues?.get(1) ?: "",
            claimed = claimed,
            menuImages = menus,
            reviews = reviews
        )
    }

    private fun parseReviews(doc: Document): List<BeachReview> {
        val items = doc.select(".review-item, .review-list > div").toList()
            .filter { it.selectFirst(".muted#noReviewTip") == null && it.text().isNotBlank() }
        if (items.isEmpty()) return emptyList()
        return items.mapIndexedNotNull { i, el ->
            val nick = el.selectFirst(".review-author, [class*=author], .font-medium")?.text()?.trim()
                ?: Regex("^([^\\s]{1,10})").find(el.text())?.groupValues?.get(1)
                ?: return@mapIndexedNotNull null
            val content = el.selectFirst(".review-content, p, [class*=content]")?.text()?.trim()
                ?: el.text().replace(nick, "").let { t ->
                    Regex("\\d{4}-\\d{2}-\\d{2}|\\d+\\s*(分钟|小时|天)前").find(t)?.range?.let { r ->
                        t.substring(0, r.first).trim()
                    } ?: t.trim()
                }
            val ratingEl = el.selectFirst(".stars-fg")
            val rating = ratingEl?.attr("style")?.let { s ->
                Regex("width:\\s*([\\d.]+)").find(s)?.groupValues?.get(1)?.toFloatOrNull()?.div(20f)
            } ?: 0f
            val time = el.selectFirst(".review-time, .text-xs, [class*=time]")?.text()?.trim()
                ?: Regex("\\d{4}-\\d{2}-\\d{2}").find(el.text())?.groupValues?.get(0) ?: ""
            BeachReview(
                id = el.attr("data-id").ifEmpty { "r$i" },
                nickname = nick,
                avatarText = nick.firstOrNull()?.toString() ?: "匿",
                rating = rating,
                content = content,
                time = time
            )
        }
    }

    private fun extractInfo(doc: Document, keywords: List<String>, hintKeywords: String): String {
        // 找含地址信息的元素：.loc 或含电话/QQ 外的位置类文本
        val loc = doc.selectFirst(".loc, .address, [class*=loc]")
        if (loc != null) {
            val t = loc.text().trim()
            if (t.isNotEmpty()) return t
        }
        // 用正则从整页提取电话/QQ之间的内容
        val text = doc.body().wholeText()
        val phoneIdx = text.indexOf("电话").let { if (it < 0) text.length else it }
        val qqIdx = text.indexOf("QQ").let { if (it < 0) text.length else it }
        val end = minOf(phoneIdx, qqIdx)
        // 从评分后开始找，直到电话/QQ
        val afterRate = Regex("\\(\\d+人\\)\\s*(.*)").find(text)?.groupValues?.get(1) ?: ""
        if (afterRate.isNotEmpty()) {
            val idx = afterRate.indexOfAny(listOf("电话", "QQ"))
            val sub = if (idx >= 0) afterRate.substring(0, idx) else afterRate
            return sub.trim().trim('·', ' ', '\n')
        }
        if (end < text.length) {
            val idx = text.indexOf(".muted").let { if (it < 0) 0 else it }
            val part = text.substring(idx, end)
            val m = Regex("([$hintKeywords][^\\n]+)").find(part)
            if (m != null) return m.groupValues[1].trim()
        }
        return ""
    }

    private fun extractShopId(href: String): String {
        val m = Regex("/shop/(\\d+)").find(href)
        return m?.groupValues?.get(1) ?: ""
    }

    private fun resolveUrl(u: String): String {
        if (u.isEmpty()) return ""
        if (u.startsWith("http")) return u
        return if (u.startsWith("/")) BASE + u else "$BASE/$u"
    }
}
