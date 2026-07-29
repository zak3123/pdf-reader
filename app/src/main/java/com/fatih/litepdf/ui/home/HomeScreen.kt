package com.fatih.litepdf.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fatih.litepdf.R
import com.fatih.litepdf.domain.model.RecentDocument
import com.fatih.litepdf.ui.components.ConfirmDialog
import com.fatih.litepdf.ui.theme.LitePdfDimensions
import com.fatih.litepdf.util.asReadableDate
import com.fatih.litepdf.util.asReadableFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onOpenPdf: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSettings: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LitePdfDimensions.TopBarHeight)
                        .padding(horizontal = LitePdfDimensions.CompactPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onOpenPdf, enabled = !state.isOpening) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = stringResource(R.string.open_pdf))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = LitePdfDimensions.ScreenPadding, vertical = LitePdfDimensions.CompactPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.recent_documents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (state.recentDocuments.isNotEmpty()) {
                    TextButton(onClick = { showClearDialog = true }) {
                        Text(stringResource(R.string.clear_history))
                    }
                }
            }

            if (state.recentDocuments.isEmpty()) {
                EmptyRecentState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = LitePdfDimensions.CompactPadding)
                ) {
                    items(state.recentDocuments, key = { it.id }) { document ->
                        RecentDocumentRow(
                            document = document,
                            onOpen = { onOpenRecent(document.id) },
                            onRemove = { onRemoveRecent(document.id) }
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = stringResource(R.string.clear_history_question),
            confirmText = stringResource(R.string.clear),
            cancelText = stringResource(R.string.cancel),
            onConfirm = {
                showClearDialog = false
                onClearHistory()
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@Composable
private fun EmptyRecentState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(54.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.no_recent_documents), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.no_recent_documents_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentDocumentRow(
    document: RecentDocument,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LitePdfDimensions.RecentItemMinHeight)
                .clickable(onClick = onOpen)
                .padding(horizontal = 0.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val pageText = document.pageCount?.let {
                    stringResource(R.string.page_position, document.lastViewedPage + 1, it)
                } ?: stringResource(R.string.page_position_unknown_total, document.lastViewedPage + 1)
                val sizeText = document.sizeBytes?.let {
                    " - " + stringResource(R.string.file_size, it.asReadableFileSize())
                }.orEmpty()
                Text(
                    text = document.displayName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = pageText + sizeText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.last_opened, document.lastOpenedAt.asReadableDate()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_pdf)) },
                        onClick = {
                            expanded = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove)) },
                        onClick = {
                            expanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }
}
