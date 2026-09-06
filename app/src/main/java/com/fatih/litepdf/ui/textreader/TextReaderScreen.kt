package com.fatih.litepdf.ui.textreader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatih.litepdf.R
import com.fatih.litepdf.office.DocBlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextReaderScreen(
    state: TextReaderUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: (Boolean) -> Unit = {},
    onNextMatch: () -> Unit = {},
    onPrevMatch: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    // Auto-scroll to the block of the currently selected match.
    LaunchedEffect(state.currentMatchIndex, state.searchResults) {
        val match = state.searchResults.getOrNull(state.currentMatchIndex)
        if (match != null) {
            listState.animateScrollToItem(match.blockIndex.coerceAtLeast(0))
        }
    }

    // Index of the block currently containing an active match (for highlight emphasis).
    val activeMatch = state.searchResults.getOrNull(state.currentMatchIndex)

    Scaffold(
        topBar = {
            if (state.showSearchBar) {
                SearchTopBar(
                    query = state.searchQuery,
                    resultsCount = state.searchResults.size,
                    currentMatchNumber = if (state.searchResults.isEmpty()) 0 else state.currentMatchIndex + 1,
                    onQueryChange = onSearchQueryChange,
                    onNext = onNextMatch,
                    onPrev = onPrevMatch,
                    onClose = { onToggleSearch(false) }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = state.displayName ?: stringResource(R.string.reader_loading),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        if (state.content != null) {
                            IconButton(onClick = { onToggleSearch(true) }) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.search_document)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                state.isLoading -> LoadingState()
                state.openError -> ErrorState(
                    message = state.openErrorMessage?.let { stringResource(it) }
                        ?: stringResource(R.string.unsupported_office_corrupt),
                    onRetry = onRetry
                )
                state.content != null -> {
                    DocumentContent(
                        blocks = state.content.blocks,
                        listState = listState,
                        activeBlockIndex = activeMatch?.blockIndex ?: -1,
                        searchQuery = if (state.showSearchBar) state.searchQuery else ""
                    )
                    PositionIndicator(
                        listState = listState,
                        blocks = state.content.blocks
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    resultsCount: Int,
    currentMatchNumber: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.back))
                        }
                    }
                }
            )
            // Match counter e.g. "3 / 12"
            if (query.length >= 2) {
                Text(
                    text = "$currentMatchNumber / $resultsCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            IconButton(onClick = onPrev, enabled = resultsCount > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.previous_page))
            }
            IconButton(onClick = onNext, enabled = resultsCount > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.next_page))
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.reader_loading))
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.search))
            }
        }
    }
}

@Composable
private fun DocumentContent(
    blocks: List<DocBlock>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    activeBlockIndex: Int,
    searchQuery: String
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            blocks = blocks,
            activeBlockIndex = activeBlockIndex,
            searchQuery = searchQuery
        )
    }
}

/** Emits one lazy item per block; tables emit one lazy item per row so huge tables stay virtualized. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    blocks: List<DocBlock>,
    activeBlockIndex: Int,
    searchQuery: String
) {
    blocks.forEachIndexed { index, block ->
        when (block) {
            is DocBlock.Heading -> item(key = "h$index") {
                HeadingBlock(block, searchQuery, highlighted = index == activeBlockIndex)
            }
            is DocBlock.Paragraph -> item(key = "p$index") {
                ParagraphBlock(block, searchQuery, highlighted = index == activeBlockIndex)
            }
            is DocBlock.Bullet -> item(key = "b$index") {
                BulletBlock(block, searchQuery, highlighted = index == activeBlockIndex)
            }
            is DocBlock.Divider -> item(key = "d$index") { DividerBlock(block) }
            is DocBlock.Table -> {
                // Header row of the table (visual chrome), then virtualized rows.
                itemsTable(block, index)
            }
        }
    }
}

/** Renders a table's rows as individual lazy items so rendering stays bounded. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsTable(
    block: DocBlock.Table,
    blockIndex: Int
) {
    val rows = block.rows
    items(
        count = rows.size,
        key = { row -> "t${blockIndex}_$row" }
    ) { rowIndex ->
        TableRow(
            row = rows[rowIndex],
            isHeader = block.hasHeader && rowIndex == 0
        )
    }
}

@Composable
private fun highlightedText(text: String, query: String) = buildAnnotatedString {
    if (query.length < 2) {
        append(text)
        return@buildAnnotatedString
    }
    val lower = text.lowercase()
    val q = query.lowercase()
    var start = 0
    while (true) {
        val idx = lower.indexOf(q, start)
        if (idx < 0) {
            append(text.substring(start))
            break
        }
        append(text.substring(start, idx))
        withStyle(
            SpanStyle(
                background = MaterialTheme.colorScheme.tertiaryContainer,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        ) {
            append(text.substring(idx, idx + query.length))
        }
        start = idx + query.length
    }
}

@Composable
private fun HeadingBlock(block: DocBlock.Heading, query: String, highlighted: Boolean) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }
    Text(
        text = highlightedText(block.text, query),
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ParagraphBlock(block: DocBlock.Paragraph, query: String, highlighted: Boolean) {
    if (block.text.isBlank()) return
    Text(
        text = highlightedText(block.text, query),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun BulletBlock(block: DocBlock.Bullet, query: String, highlighted: Boolean) {
    Row(modifier = Modifier.padding(start = (block.indent * 16).dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = highlightedText(block.text, query),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun DividerBlock(block: DocBlock.Divider) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.width(24.dp))
        block.label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TableRow(row: List<String>, isHeader: Boolean) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .background(
                if (isHeader) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
    ) {
        row.forEach { cell ->
            Text(
                text = cell,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(160.dp)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

/** Floating position indicator (Sumatra-style "Page X of Y"), tracking the top visible block. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PositionIndicator(
    listState: androidx.compose.foundation.lazy.LazyListState,
    blocks: List<DocBlock>
) {
    val firstVisible = listState.firstVisibleItemIndex
    val total = blocks.size
    if (total == 0) return

    // Nearest section label (divider) above the current block.
    val sectionLabel = remainingSectionLabel(blocks, firstVisible)
    val positionText = if (sectionLabel != null) {
        sectionLabel
    } else {
        stringResource(R.string.text_position, (firstVisible + 1).coerceAtMost(total), total)
    }

    Surface(
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = positionText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun remainingSectionLabel(blocks: List<DocBlock>, fromIndex: Int): String? {
    var i = fromIndex.coerceIn(0, blocks.size - 1)
    while (i >= 0) {
        val b = blocks.getOrNull(i)
        if (b is DocBlock.Divider && !b.label.isNullOrBlank()) return b.label
        i--
    }
    return null
}
