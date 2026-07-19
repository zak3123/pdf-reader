package com.fatih.litepdf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.fatih.litepdf.domain.model.ThemeMode

private val LightScheme: ColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF235789),
    secondary = androidx.compose.ui.graphics.Color(0xFF4C6B5D),
    tertiary = androidx.compose.ui.graphics.Color(0xFF755B00),
    surface = androidx.compose.ui.graphics.Color(0xFFFBFCFE),
    background = androidx.compose.ui.graphics.Color(0xFFF7F9FC)
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF9DCAFF),
    secondary = androidx.compose.ui.graphics.Color(0xFFB4CCBD),
    tertiary = androidx.compose.ui.graphics.Color(0xFFE2C46C),
    surface = androidx.compose.ui.graphics.Color(0xFF111418),
    background = androidx.compose.ui.graphics.Color(0xFF0D1116)
)

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
