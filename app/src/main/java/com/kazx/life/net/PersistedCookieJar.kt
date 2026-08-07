package com.kazx.life.net

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 持久化 CookieJar：把登录态 Cookie 存到 SharedPreferences，
 * 保证 App 重启后仍保持登录（与网站 session 共用）。
 */
class PersistedCookieJar private constructor(
    private val prefs: SharedPreferences
) : CookieJar {

    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        loadAll()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val list = store.getOrPut(host) { mutableListOf() }
        // 同名覆盖
        for (c in cookies) {
            list.removeAll { it.name == c.name }
            list.add(c)
        }
        persist(host)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val list = store[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return list.filter { it.expiresAt > now }
    }

    fun clear() {
        store.clear()
        prefs.edit().clear().apply()
    }

    private fun persist(host: String) {
        val list = store[host] ?: return
        val joined = list.joinToString("\u0001") { it.toString() }
        prefs.edit().putString(host, joined).apply()
    }

    private fun loadAll() {
        for ((host, value) in prefs.all) {
            if (host.contains('.')) {
                val cookies = (value as? String)
                    ?.split('\u0001')
                    ?.mapNotNull {
                        runCatching { Cookie.parse(HttpUrl.Builder().host(host).scheme("https").build(), it) }.getOrNull()
                    } ?: emptyList()
                if (cookies.isNotEmpty()) store[host] = cookies.toMutableList()
            }
        }
    }

    companion object {
        @Volatile private var instance: PersistedCookieJar? = null
        fun get(context: Context): PersistedCookieJar {
            return instance ?: synchronized(this) {
                instance ?: PersistedCookieJar(
                    context.getSharedPreferences("kazx_cookies", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
        }
    }
}
