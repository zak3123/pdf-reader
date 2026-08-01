package com.fatih.litepdf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.fatih.litepdf.domain.model.ThemeMode

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F5F99),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF4B5661),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF6B5F35),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF171A1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A1F),
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF555E68),
    outline = Color(0xFFC8C8C8)
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF8EC5FF),
    onPrimary = Color(0xFF002E52),
    secondary = Color(0xFFC8D0D8),
    onSecondary = Color(0xFF202A33),
    tertiary = Color(0xFFE2C96F),
    background = Color(0xFF101316),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF171B20),
    onSurface = Color(0xFFF1F3F5),
    surfaceVariant = Color(0xFF252B31),
    onSurfaceVariant = Color(0xFFC2C8D0),
    outline = Color(0xFF49515A)
)

object SumatraLikeColors {
    val CanvasLight = Color(0xFF9B9B9B)
    val CanvasDark = Color(0xFF4A4A4A)
    val ToolbarLight = Color(0xFFF2F2F2)
    val ToolbarDark = Color(0xFF252525)
    val SidebarLight = Color(0xFFF8F8F8)
    val SidebarDark = Color(0xFF202020)
    val DividerLight = Color(0xFFC7C7C7)
    val DividerDark = Color(0xFF4B4B4B)
    val PageShadow = Color(0x66000000)
    val ActiveTabLight = Color(0xFFFFFFFF)
    val ActiveTabDark = Color(0xFF303030)
}

@Composable
fun LitePdfTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
