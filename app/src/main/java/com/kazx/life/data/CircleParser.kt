package com.kazx.life.data

import com.kazx.life.net.Net
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 解析 quanzi.kazx.top 的圈子页面。
 *
 * 圈子列表：index.php 首页里的 a.card.circle-card
 *   - .circle-icon > img (圈子头像，可为 svg)
 *   - .circle-info > .circle-name / .circle-desc / .circle-meta ("X 成员 / Y 帖子")
 *   - href: index.php?p=circle&id=N
 *
 * 圈子详情：index.php?p=circle&id=N
 *   - 顶部：圈名(h1)、描述、成员数、帖子数、圈主
 *   - .post-item > .avatar + .post-title>a + .post-summary + .post-meta (作者/时间/X条评论)
 *
 * 帖子详情：index.php?p=post&id=N
 *   - 作者头像/昵称/时间/所属圈子
 *   - h1 标题 + 正文内容 + 评论区
 */
object CircleParser {

    private const val BASE = Net.CIRCLE_BASE

    /** 解析圈子首页 → 圈子列表。 */
    fun parseCircleList(html: String): List<Circle> {
        val doc = Jsoup.parse(html, BASE)
        val cards = doc.select("a.circle-card, a.card")
            .toList()
            .filter { it.attr("href").contains("circle&id=") }
        if (cards.isEmpty()) return emptyList()
        return cards.map { parseCircleCard(it) }
    }

    private fun parseCircleCard(el: Element): Circle {
        val href = el.attr("href")
        val id = extractId(href, "circle&id=")

        val iconEl = el.selectFirst(".circle-icon img")
        val icon = iconEl?.absUrl("src")?.ifEmpty { iconEl.attr("src") } ?: ""

        val name = el.selectFirst(".circle-name")?.text()?.trim()
            ?: el.selectFirst("h3, .font-bold")?.text()?.trim() ?: ""
        val desc = el.selectFirst(".circle-desc")?.text()?.trim()
            ?: el.selectFirst(".text-gray-600")?.text()?.trim() ?: ""

        val meta = el.selectFirst(".circle-meta")?.text() ?: el.text()
        val memberMatch = Regex("(\\d+)\\s*成员").find(meta)
        val postMatch = Regex("(\\d+)\\s*帖子").find(meta)

        return Circle(
            id = id,
            name = name,
            desc = desc,
            icon = resolveUrl(icon),
            memberCount = memberMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            postCount = postMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        )
    }

    /** 解析圈子详情页（头部信息 + 帖子列表）。 */
    fun parseCircleDetail(html: String, circleId: String): CircleDetail? {
        val doc = Jsoup.parse(html, BASE)

        // 头部信息：找 h1 作为圈名
        val name = doc.selectFirst("h1, .text-2xl.font-bold, .font-bold.text-2xl")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim() ?: ""
        if (name.isEmpty()) return null

        // 找 icon（如果有的话，从头部找 img[alt] 或 .circle-icon）
        val icon = doc.selectFirst(".flex.items-start.gap-4 img, .circle-header img, .w-20 img")
            ?.let { it.absUrl("src").ifEmpty { it.attr("src") } } ?: ""

        // 描述：圈名后面的 paragraph
        val desc = doc.selectFirst("h1 + p, .leading-relaxed, .text-gray-600.mb-4")?.text()?.trim()
            ?: run {
                // 在整页文本里找位于圈名之后、成员数之前的那句
                val allText = doc.body().wholeText()
                val idxName = allText.indexOf(name)
                val idxMember = allText.indexOf("成员", idxName + name.length).let { if (it < 0) allText.length else it }
                if (idxName >= 0 && idxMember > idxName) {
                    allText.substring(idxName + name.length, idxMember)
                        .replace("\\s+".toRegex(), " ").trim().trim('·', ' ', '/')
                } else ""
            }

        val allText = doc.body().wholeText()
        val memberMatch = Regex("(\\d+)\\s*成员").find(allText)
        val postMatch = Regex("(\\d+)\\s*帖子").find(allText)
        val ownerMatch = Regex("圈主[：:]\\s*([^\\n\\r]+)").find(allText)

        val posts = parseCirclePosts(doc, circleId)

        return CircleDetail(
            id = circleId,
            name = name,
            desc = desc,
            icon = resolveUrl(icon),
            memberCount = memberMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
            postCount = postMatch?.groupValues?.get(1)?.toIntOrNull() ?: posts.size,
            owner = ownerMatch?.groupValues?.get(1)?.trim() ?: "",
            posts = posts
        )
    }

    private fun parseCirclePosts(doc: Document, circleId: String): List<CirclePost> {
        return doc.select(".post-item").mapIndexed { i, el ->
            val avatarEl = el.selectFirst(".avatar, .avatar-placeholder")
            val avatarText = avatarEl?.text()?.trim()?.ifEmpty { "匿" } ?: "匿"

            val link = el.selectFirst("a[href*=post&id=]")
            val href = link?.attr("href") ?: ""
            val id = extractId(href, "post&id=")
            val title = link?.text()?.trim() ?: ""

            val summary = el.selectFirst(".post-summary")?.text()?.trim() ?: ""

            val metaSpans = el.select(".post-meta span")
            val author = metaSpans.getOrNull(0)?.text()?.trim() ?: ""
            val time = metaSpans.getOrNull(1)?.text()?.trim() ?: ""
            val commentText = metaSpans.getOrNull(2)?.text()?.trim() ?: ""
            val commentCount = Regex("(\\d+)").find(commentText)?.groupValues?.get(1)?.toIntOrNull() ?: 0

            CirclePost(
                id = id.ifEmpty { "p$i" },
                circleId = circleId,
                avatarText = avatarText,
                title = title,
                summary = summary,
                author = author,
                time = time,
                commentCount = commentCount
            )
        }
    }

    /** 解析圈子帖子详情页。 */
    fun parseCirclePostDetail(html: String, postId: String, circleId: String = ""): CirclePostDetail? {
        val doc = Jsoup.parse(html, BASE)

        // 标题：h1
        val title = doc.selectFirst("h1")?.text()?.trim() ?: ""
        if (title.isEmpty()) return null

        // 作者头像/昵称/时间：帖子头部
        val header = doc.selectFirst(".flex.items-center.gap-3, .flex.items-center.gap-4")
        val avatarText = header?.selectFirst(".avatar, .avatar-placeholder")?.text()?.trim()?.ifEmpty { "匿" }
            ?: doc.selectFirst(".avatar-placeholder, .avatar")?.text()?.trim()?.ifEmpty { "匿" } ?: "匿"

        val metaSpans = header?.select("span") ?: doc.select(".text-sm.text-gray-500, .text-gray-500.text-sm")
        val author = metaSpans.getOrNull(0)?.text()?.trim()?.let { it.split('/').first().trim() } ?: ""
        val time = metaSpans.text().let {
            val m = Regex("(\\d{4}-\\d{2}-\\d{2})").find(it)
            m?.groupValues?.get(1) ?: ""
        }

        // 所属圈子链接
        val circleLink = doc.selectFirst("a[href*=circle&id=]")
        val circleName = circleLink?.text()?.trim() ?: ""
        val realCircleId = if (circleId.isNotEmpty()) circleId else extractId(circleLink?.attr("href") ?: "", "circle&id=")

        // 正文：h1 之后的内容容器
        val contentEl = doc.selectFirst("h1 ~ .whitespace-pre-wrap, h1 + div, .prose, .mb-8.whitespace-pre-wrap")
            ?: doc.selectFirst(".text-gray-800.whitespace-pre-wrap")
        val content = contentEl?.text()?.trim()
            ?: doc.selectFirst("h1")?.parent()?.parent()?.selectFirst("p, .leading-relaxed")?.text()?.trim()
            ?: ""

        // 图片
        val images = mutableListOf<String>()
        for (img in contentEl?.select("img") ?: doc.select("img")) {
            val src = img.absUrl("src").ifEmpty { img.attr("src") }
            if (src.isNotEmpty() && !src.contains("avatar") && !src.contains("placeholder")) {
                images.add(resolveUrl(src))
            }
        }

        // 评论区
        val comments = parseCircleComments(doc)

        val basePost = CirclePost(
            id = postId,
            circleId = realCircleId,
            avatarText = avatarText,
            title = title,
            summary = content.take(60),
            author = author,
            time = time,
            commentCount = comments.size
        )

        return CirclePostDetail(
            post = basePost,
            content = content,
            circleName = circleName,
            images = images,
            comments = comments
        )
    }

    private fun parseCircleComments(doc: Document): List<CircleComment> {
        val items = doc.select(".comment-item, .space-y-3 > div, [class*=comment]")
            .toList()
            .filter { it.text().isNotBlank() && !it.selectFirst(".avatar, .font-medium, span")?.text().isNullOrBlank() }
        if (items.isEmpty()) return emptyList()
        return items.mapIndexedNotNull { i, el ->
            val nickname = el.selectFirst(".font-medium, .font-semibold")?.text()?.trim()
                ?: el.selectFirst("span")?.text()?.trim() ?: return@mapIndexedNotNull null
            val avatarText = el.selectFirst(".avatar, .avatar-placeholder")?.text()?.trim()?.ifEmpty {
                nickname.firstOrNull()?.toString() ?: "匿"
            } ?: nickname.firstOrNull()?.toString() ?: "匿"
            val content = el.selectFirst(".text-gray-700, .text-gray-800, p, .leading-relaxed")?.text()?.trim()
                ?: el.text().replace(nickname, "").trim()
            val time = el.selectFirst(".text-xs, .text-sm.text-gray-500")?.text()?.trim()
                ?: Regex("(\\d{4}-\\d{2}-\\d{2}|\\d+\\s*(分钟|小时|天)前)").find(el.text())?.groupValues?.get(0) ?: ""
            CircleComment(
                id = el.attr("data-comment-id").ifEmpty { "c$i" },
                nickname = nickname,
                avatarText = avatarText,
                content = content,
                time = time
            )
        }
    }

    // —— 辅助 ——
    private fun extractId(href: String, key: String): String {
        val idx = href.indexOf(key)
        if (idx < 0) return ""
        var end = idx + key.length
        while (end < href.length && href[end].isDigit()) end++
        return href.substring(idx + key.length, end)
    }

    private fun resolveUrl(u: String): String {
        if (u.isEmpty()) return ""
        if (u.startsWith("http")) return u
        return if (u.startsWith("/")) BASE + u else "$BASE/$u"
    }
}
