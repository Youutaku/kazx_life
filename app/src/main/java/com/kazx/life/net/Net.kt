package com.kazx.life.net

import android.content.Context
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * 全局网络组件。封装 OkHttp 客户端与对 kazx.top 表白墙 api.php 的请求。
 *
 * 表白墙接口（newbbq.kazx.top）：
 *  - GET  api.php?action=load_more_posts&offset=N&limit=10  → {success, html, loaded, has_more}
 *  - POST api.php?action=login        body: username, password
 *  - POST api.php?action=register     body: username, password, confirm_password, email?
 *  - POST api.php?action=like         body: post_id
 *  - POST api.php?action=report_post  body: post_id, reason, detail, fingerprint
 *  - GET  api.php?action=captcha      → {success, image(base64)}
 *  - POST api.php?action=verify_captcha body: code
 *  - POST api.php?action=upload       multipart: file
 *  - POST post.php                    表单: nickname, content, media(JSON), fingerprint, topic
 *  - GET  detail.php?id=N             帖子详情 HTML 页
 *  - GET  index.php                   首页 HTML（含初始 10 条）
 */
object Net {

    private const val BASE = "https://newbbq.kazx.top"
    const val CIRCLE_BASE = "https://quanzi.kazx.top"
    const val BEACH_BASE = "https://beach.kazx.top"

    lateinit var client: OkHttpClient
        private set
    lateinit var cookieJar: PersistedCookieJar
        private set

    fun init(context: Context) {
        cookieJar = PersistedCookieJar.get(context)
        client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** 退出登录：清空 Cookie。 */
    fun logout() = cookieJar.clear()

    // —— 基础请求 ——
    fun get(path: String): Response {
        val req = Request.Builder().url("$BASE/$path").get().build()
        return client.newCall(req).execute()
    }

    fun postForm(path: String, params: Map<String, String>): Response {
        val body = okhttp3.FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()
        val req = Request.Builder().url("$BASE/$path").post(body).build()
        return client.newCall(req).execute()
    }

    fun postMultipart(path: String, fieldName: String, part: MultipartBody.Part, extra: Map<String, String> = emptyMap()): Response {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            extra.forEach { (k, v) -> addFormDataPart(k, v) }
            addPart(part)
        }.build()
        val req = Request.Builder().url("$BASE/$path").post(body).build()
        return client.newCall(req).execute()
    }

    // —— 圈子（quanzi.kazx.top）专用 ——
    fun circleGet(path: String): Response {
        val url = if (path.startsWith("http")) path else "$CIRCLE_BASE/$path"
        val req = Request.Builder().url(url).get().build()
        return client.newCall(req).execute()
    }

    fun circlePostForm(path: String, params: Map<String, String>): Response {
        val body = okhttp3.FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()
        val url = if (path.startsWith("http")) path else "$CIRCLE_BASE/$path"
        val req = Request.Builder().url(url).post(body).build()
        return client.newCall(req).execute()
    }

    // —— 必吃榜（beach.kazx.top）专用 ——
    fun beachGet(path: String): Response {
        val url = if (path.startsWith("http")) path else "$BEACH_BASE/$path"
        val req = Request.Builder().url(url).get().build()
        return client.newCall(req).execute()
    }

    fun beachPostForm(path: String, params: Map<String, String>): Response {
        val body = okhttp3.FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()
        val url = if (path.startsWith("http")) path else "$BEACH_BASE/$path"
        val req = Request.Builder().url(url).post(body).build()
        return client.newCall(req).execute()
    }
}
