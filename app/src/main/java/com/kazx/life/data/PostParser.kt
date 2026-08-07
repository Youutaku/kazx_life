package com.kazx.life.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 用 Jsoup 解析表白墙返回的 HTML 片段 / 页面。
 *
 * 列表：api.php?action=load_more_posts 返回 {success, html, loaded, has_more}，
 *       html 是若干 <div class="post-card" data-post-id=...> 卡片。
 * 详情：detail.php?id=N 整页 HTML，帖子主体结构与卡片一致，
 *       评论为 .comment-item（服务端渲染，无则空）。
 */
object PostParser {

    private const val BASE = "https://newbbq.kazx.top/"

    /** 解析 load_more_posts 返回的 html 片段。 */
    fun parseListHtml(html: String): List<Post> {
        val doc = Jsoup.parse(html, BASE)
        return doc.select("div.post-card[data-post-id]").map { parseCard(it) }
    }

    /** 解析首页 index.php 整页（取其中的帖子卡片）。 */
    fun parseIndexPage(html: String): List<Post> {
        val doc = Jsoup.parse(html, BASE)
        // 首页卡片可能没有 data-post-id（取决于版本），同时兼容带与不带
        val cards = doc.select("div.post-card[data-post-id]")
        return if (cards.isNotEmpty()) cards.map { parseCard(it) }
        else doc.select("div.post-card").map { parseCard(it) }
    }

    /** 解析 detail.php?id=N 整页，得到帖子主体 + 评论。 */
    fun parseDetailPage(html: String): PostDetail? {
        val doc = Jsoup.parse(html, BASE)
        // 详情页正文卡片：首选带 data-post-id 的 like-btn 反推，否则取第一个 .post-card
        val card = doc.selectFirst("div.post-card")
            ?: return null
        val postId = doc.selectFirst(".like-btn[data-post-id]")?.attr("data-post-id")
            ?: doc.selectFirst(".report-btn[data-post-id]")?.attr("data-post-id")
            ?: ""
        val post = parseCard(card, fallbackId = postId)
        val comments = parseComments(doc)
        return PostDetail(post = post.copy(id = postId.ifEmpty { post.id }), comments = comments)
    }

    private fun parseCard(el: Element, fallbackId: String = ""): Post {
        val id = el.attr("data-post-id").ifEmpty { fallbackId }
        val isPinned = !el.select(".fa-thumbtack").isEmpty() ||
            el.text().contains("置顶")
        val avatarEl = el.selectFirst(".avatar")
        val avatarColor = parseAvatarColor(avatarEl?.attr("style")) ?: "#9C27B0"
        val avatarText = avatarEl?.text()?.trim()?.ifEmpty { "匿" } ?: "匿"

        val nameEl = el.selectFirst(".font-semibold.text-gray-800")
            ?: el.selectFirst(".font-semibold")
        val nickname = nameEl?.ownText()?.trim()?.ifEmpty { "匿名用户" } ?: "匿名用户"
        val isAdmin = !el.select(".fa-user-shield").isEmpty()

        val timeEl = el.selectFirst(".text-sm.text-gray-500")
        val time = timeEl?.text()?.trim() ?: ""

        val contentEl = el.selectFirst(".post-content")
            ?: el.selectFirst(".text-gray-700.whitespace-pre-wrap")
            ?: el.selectFirst(".text-gray-700")
        val content = contentEl?.text()?.trim() ?: ""

        val images = mutableListOf<String>()
        val videos = mutableListOf<String>()
        for (img in el.select(".media-grid img")) {
            // 优先懒加载属性 data-src，回退 src；Jsoup 的 absUrl 基于 baseUri 解析绝对地址
            val src = img.absUrl("data-src").ifEmpty { img.absUrl("src") }
            if (src.isNotEmpty()) images.add(src)
        }
        for (v in el.select(".media-grid video")) {
            val direct = v.absUrl("data-src").ifEmpty { v.absUrl("src") }
            val source = v.selectFirst("source")?.absUrl("src").orEmpty()
            val final = source.ifEmpty { direct }
            if (final.isNotEmpty()) videos.add(final)
        }

        val likeCount = el.selectFirst(".like-btn .like-count")?.text()?.trim()?.toIntOrNull() ?: 0
        val liked = el.selectFirst(".like-btn i")?.classNames()?.contains("text-pink-500") == true

        // 评论数：comment-entry 内 span，或评论图标后第一个 span
        val commentCount = el.selectFirst(".comment-entry span")?.text()?.trim()?.toIntOrNull()
            ?: run {
                val commentIcon = el.selectFirst(".fa-comment") ?: return@run 0
                val parent = commentIcon.parent()
                parent?.selectFirst("span")?.text()?.trim()?.toIntOrNull() ?: 0
            }

        val topic = el.selectFirst(".tag")?.text()?.trim()?.ifEmpty { null }

        return Post(
            id = id,
            nickname = nickname,
            avatarColor = avatarColor,
            avatarText = avatarText,
            isAdmin = isAdmin,
            isPinned = isPinned,
            time = time,
            content = content,
            images = images,
            videos = videos,
            likeCount = likeCount,
            commentCount = commentCount,
            liked = liked,
            topic = topic
        )
    }

    private fun parseComments(doc: Document): List<Comment> {
        val items = doc.select(".comment-item")
        if (items.isEmpty()) return emptyList()
        return items.map { el ->
            val avatarEl = el.selectFirst(".avatar")
            val color = parseAvatarColor(avatarEl?.attr("style")) ?: "#7E57C2"
            val text = avatarEl?.text()?.trim()?.ifEmpty { "匿" } ?: "匿"
            val name = el.selectFirst(".font-semibold")?.ownText()?.trim()
                ?: el.selectFirst(".font-medium")?.ownText()?.trim()
                ?: "匿名用户"
            val isAdmin = !el.select(".fa-user-shield").isEmpty()
            // 内容：.comment-content 或 .text-gray-600（注意 .text-gray-700 是昵称的类，不能用作内容选择器）
            val content = el.selectFirst(".comment-content")?.text()?.trim()
                ?: el.selectFirst(".text-gray-600")?.text()?.trim()
                ?: el.text().trim()
            val time = el.selectFirst(".text-gray-400.text-sm")?.text()?.trim()
                ?: el.selectFirst(".text-sm.text-gray-500")?.text()?.trim()
                ?: el.selectFirst(".text-xs")?.text()?.trim() ?: ""
            Comment(
                id = el.attr("data-comment-id").ifEmpty { el.attr("id") },
                nickname = name,
                avatarColor = color,
                avatarText = text,
                content = content,
                time = time,
                isAdmin = isAdmin
            )
        }
    }

    /** 从 style="background: #42a5f5;" 提取颜色。 */
    private fun parseAvatarColor(style: String?): String? {
        if (style.isNullOrBlank()) return null
        val m = Regex("#([0-9A-Fa-f]{3,8})").find(style) ?: return null
        return m.value
    }
}
