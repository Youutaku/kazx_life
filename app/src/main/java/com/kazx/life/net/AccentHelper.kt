package com.kazx.life.net

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.util.TypedValue

/**
 * 运行时动态覆盖主题颜色。
 * 在 Activity.onCreate 的 super.onCreate 之前调用 applyAccent(theme)。
 */
object AccentHelper {

    /**
     * 获取当前应使用的主色，用于代码中设置背景渐变等 XML 无法动态覆盖的场景。
     */
    fun primaryColor(): Int = ThemeManager.getAccent().primary
    fun primaryDarkColor(): Int = ThemeManager.getAccent().primaryDark
    fun secondaryColor(): Int = ThemeManager.getAccent().secondary

    /**
     * 将渐变 drawable 的两个颜色作为 int 数组返回 [start, end]。
     */
    fun gradientColors(): IntArray = intArrayOf(primaryColor(), secondaryColor())

    /**
     * 对 Resources.Theme 动态应用 accent 颜色覆盖。
     * 使用 theme.applyStyle 叠加一个动态生成的 style overlay。
     *
     * 由于运行时无法动态创建 Style 资源，这里改为直接设置各 View 的颜色。
     * 此函数保留供未来使用。
     */
    fun applyAccent(@Suppress("UNUSED_PARAMETER") theme: Resources.Theme) {
        // Material 主题的 colorPrimary 等属性无法在运行时通过代码直接修改，
        // 颜色覆盖在 BaseActivity / 各 Activity 中通过直接设置 View 属性实现。
    }
}
