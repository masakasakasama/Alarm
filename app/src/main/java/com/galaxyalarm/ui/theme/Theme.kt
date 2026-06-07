package com.galaxyalarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 黒基調 + アクセントブルー。安っぽくならない高コントラスト配色。
val Accent = Color(0xFF4F8CFF)
val AccentDim = Color(0xFF2A4A82)
val Danger = Color(0xFFFF4D5E)
val Warn = Color(0xFFFFC53D)
val Ok = Color(0xFF38D39F)
val Bg = Color(0xFF07070A)
val Surface1 = Color(0xFF121218)
val Surface2 = Color(0xFF1B1B23)
val OnSurfaceDim = Color(0xFF9A9AA6)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDim,
    secondary = Ok,
    error = Danger,
    background = Bg,
    onBackground = Color(0xFFF2F2F7),
    surface = Surface1,
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Surface2,
    onSurfaceVariant = OnSurfaceDim,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 56.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelSmall = TextStyle(fontSize = 12.sp, color = OnSurfaceDim),
)

@Composable
fun GalaxyAlarmTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme() // 常にダーク基調
    MaterialTheme(colorScheme = DarkColors, typography = AppTypography, content = content)
}
