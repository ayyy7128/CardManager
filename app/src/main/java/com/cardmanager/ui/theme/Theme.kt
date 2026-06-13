package com.cardmanager.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

// ── Dark palette ───────────────────────────────────────────
val DarkBackground   = Color(0xFF0B0F17)
val DarkSurface      = Color(0xFF121824)
val DarkSurface2     = Color(0xFF0F1520)
val DarkSurface3     = Color(0xFF1A2230)
val DarkBorder       = Color(0xFF293548)
val DarkAccent       = Color(0xFF2563EB)
val DarkAccent2      = Color(0xFF93C5FD)
val DarkAccent3      = Color(0xFF60A5FA)
val DarkText         = Color(0xFFF1F5F9)
val DarkText2        = Color(0xFFB7C2D2)
val DarkText3        = Color(0xFF64748B)

// ── Light palette ──────────────────────────────────────────
val LightBackground  = Color(0xFFF3F6FA)
val LightSurface     = Color(0xFFFFFFFF)
val LightSurface3    = Color(0xFFE8EEF7)
val LightBorder      = Color(0xFFC5D0DE)
val LightAccent      = Color(0xFF1D4ED8)
val LightText        = Color(0xFF172033)
val LightText2       = Color(0xFF556276)

// ── Status colors ──────────────────────────────────────────
val ColorActive  = Color(0xFF16A34A)
val ColorFrozen  = Color(0xFF64748B)
val ColorPending = Color(0xFF0284C7)
val ColorRed     = Color(0xFFDC2626)
val ColorGold    = Color(0xFFD97706)

// ── Network colors（精准品牌配色）─────────────────────────
// 银联：深红底+银色文字（银联官方红+银）
// Visa：深蓝底+亮蓝文字
// Mastercard：深橙底+金黄文字
// AMEX：深靛蓝底+冰蓝文字
// JCB：深灰底+白文字
// Discover：深橙棕底+橙色文字
// 账户/其他：深灰底+中灰文字
data class NetworkColor(val bg: Color, val text: Color)
val networkColors = mapOf(
    "银联"        to NetworkColor(Color(0xFF8B1A1A), Color(0xFFD4D4D4)),  // 银联红+银色
    "Visa"        to NetworkColor(Color(0xFF0C2B6E), Color(0xFF60A5FA)),  // Visa深蓝+亮蓝
    "Mastercard"  to NetworkColor(Color(0xFFB34700), Color(0xFFFFD580)),  // 橙底+金黄
    "AMEX"        to NetworkColor(Color(0xFF0F2557), Color(0xFF93C5FD)),  // 深靛蓝+冰蓝
    "JCB"         to NetworkColor(Color(0xFF1E2D3D), Color(0xFFF1F5F9)),  // 深灰+白
    "Discover"    to NetworkColor(Color(0xFF7C3A00), Color(0xFFFB923C)),  // 棕橙底+橙色
)
fun networkColor(n: String) = networkColors[n]
    ?: NetworkColor(Color(0xFF2D3748), Color(0xFF94A3B8))  // 账户/其他：灰

// ── Material3 schemes ──────────────────────────────────────
private val darkScheme = darkColorScheme(
    primary          = DarkAccent3,
    onPrimary        = Color.White,
    primaryContainer = DarkAccent,
    background       = DarkBackground,
    surface          = DarkSurface,
    surfaceVariant   = DarkSurface3,
    onBackground     = DarkText,
    onSurface        = DarkText,
    onSurfaceVariant = DarkText2,
    outline          = DarkBorder,
    secondary        = DarkAccent2,
    onSecondary      = DarkBackground,
)

private val lightScheme = lightColorScheme(
    primary          = LightAccent,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFBFD4FF),
    background       = LightBackground,
    surface          = LightSurface,
    surfaceVariant   = LightSurface3,
    onBackground     = LightText,
    onSurface        = LightText,
    onSurfaceVariant = LightText2,
    outline          = LightBorder,
    secondary        = LightAccent,
    onSecondary      = Color.White,
)

@Composable
fun CardManagerTheme(isDark: Boolean = true, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val customFontFamily = remember {
        try {
            val prefs = ctx.getSharedPreferences("cm_font", android.content.Context.MODE_PRIVATE)
            val path = prefs.getString("font_path", "") ?: ""
            if (path.isNotEmpty()) {
                val file = File(path)
                if (file.exists()) FontFamily(Font(file)) else FontFamily.Default
            } else FontFamily.Default
        } catch (e: Exception) { FontFamily.Default }
    }
    val typography = if (customFontFamily != FontFamily.Default) {
        Typography(
            bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = customFontFamily),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = customFontFamily),
            bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = customFontFamily),
            titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = customFontFamily),
            titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = customFontFamily),
            titleSmall = androidx.compose.ui.text.TextStyle(fontFamily = customFontFamily),
            labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = customFontFamily),
            labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = customFontFamily),
        )
    } else Typography()

    MaterialTheme(
        colorScheme = if (isDark) darkScheme else lightScheme,
        typography = typography,
        content = content
    )
}
