package com.fatih.litepdf.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fatih.litepdf.BuildConfig
import com.fatih.litepdf.R
import com.fatih.litepdf.domain.model.AppSettings
import com.fatih.litepdf.domain.model.PageSpacing
import com.fatih.litepdf.domain.model.ThemeMode
import com.fatih.litepdf.ui.components.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onPageSpacing: (PageSpacing) -> Unit,
    onKeepAwake: (Boolean) -> Unit,
    onRememberLastPage: (Boolean) -> Unit,
    onShowPageControls: (Boolean) -> Unit,
    onClearBitmapCache: () -> Unit,
    onClearRecents: () -> Unit,
    onAbout: () -> Unit
) {
    var confirmClearHistory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroup(title = stringResource(R.string.theme)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = { Text(mode.label()) }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            SettingsGroup(title = stringResource(R.string.default_page_spacing)) {
                PageSpacing.entries.forEach { spacing ->
                    FilterChip(
                        selected = settings.pageSpacing == spacing,
                        onClick = { onPageSpacing(spacing) },
                        label = { Text(spacing.label()) }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
            SwitchRow(
                title = stringResource(R.string.keep_screen_awake),
                checked = settings.keepScreenAwake,
                onCheckedChange = onKeepAwake
            )
            SwitchRow(
                title = stringResource(R.string.remember_last_page),
                checked = settings.rememberLastPage,
                onCheckedChange = onRememberLastPage
            )
            SwitchRow(
                title = stringResource(R.string.show_page_controls),
                checked = settings.showPageControls,
                onCheckedChange = onShowPageControls
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.clear_bitmap_cache)) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = onClearBitmapCache) { Text(stringResource(R.string.clear)) }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.clear_history)) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = { confirmClearHistory = true }) { Text(stringResource(R.string.clear)) }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about)) },
                supportingContent = { Text(stringResource(R.string.version, BuildConfig.VERSION_NAME)) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = onAbout) { Text(stringResource(R.string.about)) }
                }
            )
        }
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history_question),
            confirmText = stringResource(R.string.clear),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                confirmClearHistory = false
                onClearRecents()
            },
            onDismiss = { confirmClearHistory = false }
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Row(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

@Composable
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.System -> stringResource(R.string.theme_system)
    ThemeMode.Light -> stringResource(R.string.theme_light)
    ThemeMode.Dark -> stringResource(R.string.theme_dark)
}

@Composable
private fun PageSpacing.label(): String = when (this) {
    PageSpacing.Compact -> stringResource(R.string.spacing_compact)
    PageSpacing.Normal -> stringResource(R.string.spacing_normal)
    PageSpacing.Relaxed -> stringResource(R.string.spacing_relaxed)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(R.string.privacy_statement),
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = stringResource(R.string.current_limitations),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = stringResource(R.string.limitations_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
