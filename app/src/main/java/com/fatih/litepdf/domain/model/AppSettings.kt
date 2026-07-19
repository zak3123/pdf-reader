package com.fatih.litepdf.domain.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val pageSpacing: PageSpacing = PageSpacing.Normal,
    val keepScreenAwake: Boolean = false,
    val rememberLastPage: Boolean = true,
    val showPageControls: Boolean = true
)
