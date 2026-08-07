package com.kazx.life.net

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * 主题（深色模式）切换。支持：跟随系统、浅色、深色。
 * 持久化到 SharedPreferences，进程启动时应用。
 *
 * 同时支持主题颜色（accent color）选择：用户可从预设色板中选取主色调，
 * 运行时通过 Resources Theme 动态覆盖 colorPrimary 等属性。
 */
object ThemeManager {
    const val KEY_THEME = "theme_mode"
    const val KEY_ACCENT = "accent_color_index"
    const val MODE_FOLLOW_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private lateinit var prefs: SharedPreferences

    /** 预设主题色板（主色, 深色变体, 次色）。 */
    val accents = listOf(
        AccentColor("紫粉", 0xFF9C27B0.toInt(), 0xFF7B1FA2.toInt(), 0xFFE91E63.toInt()),
        AccentColor("靛蓝", 0xFF3F51B5.toInt(), 0xFF303F9F.toInt(), 0xFF536DFE.toInt()),
        AccentColor("青色", 0xFF009688.toInt(), 0xFF00796B.toInt(), 0xFF26A69A.toInt()),
        AccentColor("橙色", 0xFFFF9800.toInt(), 0xFFF57C00.toInt(), 0xFFFFB74D.toInt()),
        AccentColor("红色", 0xFFF44336.toInt(), 0xFFD32F2F.toInt(), 0xFFFF7043.toInt()),
        AccentColor("蓝色", 0xFF2196F3.toInt(), 0xFF1976D2.toInt(), 0xFF42A5F5.toInt()),
        AccentColor("绿色", 0xFF4CAF50.toInt(), 0xFF388E3C.toInt(), 0xFF66BB6A.toInt()),
        AccentColor("粉色", 0xFFE91E63.toInt(), 0xFFC2185B.toInt(), 0xFFF06292.toInt())
    )

    data class AccentColor(
        val name: String,
        val primary: Int,
        val primaryDark: Int,
        val secondary: Int
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences("kazx_prefs", Context.MODE_PRIVATE)
        apply(getMode())
    }

    fun getMode(): Int = prefs.getInt(KEY_THEME, MODE_FOLLOW_SYSTEM)

    fun setMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME, mode).apply()
        apply(mode)
    }

    fun getAccentIndex(): Int = prefs.getInt(KEY_ACCENT, 0)

    fun getAccent(): AccentColor = accents[getAccentIndex().coerceIn(0, accents.size - 1)]

    fun setAccent(index: Int) {
        prefs.edit().putInt(KEY_ACCENT, index).apply()
    }

    fun label(mode: Int): String = when (mode) {
        MODE_LIGHT -> "浅色模式"
        MODE_DARK -> "深色模式"
        else -> "跟随系统"
    }

    private fun apply(mode: Int) {
        val delegate = when (mode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(delegate)
    }
}
