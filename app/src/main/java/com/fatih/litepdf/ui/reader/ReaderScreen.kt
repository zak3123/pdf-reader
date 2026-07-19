package com.fatih.litepdf.ui.reader

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fatih.litepdf.R
import com.fatih.litepdf.domain.model.AppSettings
import com.fatih.litepdf.pdf.PdfSearchFailure
import com.fatih.litepdf.util.PageJumpValidator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    settings: AppSettings,
    onBack: () -> Unit,
    onToggleToolbar: () -> Unit,
    onVisiblePageChanged: (Int) -> Unit,
    onRenderPage: (Int, Int) -> Unit,
    onBookmarkCurrentPage: () -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val activity = LocalActivity.current
    DisposableEffect(settings.keepScreenAwake) {
        if (settings.keepScreenAwake) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentPage)
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showJumpDialog by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pageRotation by remember { mutableStateOf(0) }
    var layoutMode by remember { mutableStateOf(ReaderLayoutMode.Continuous) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = nextScale
        offset = if (nextScale == 1f) Offset.Zero else offset + panChange
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { onVisiblePageChanged(it) }
    }

    val jumpToPage: (Int) -> Unit = { pageIndex ->
        scope.launch {
            if (layoutMode == ReaderLayoutMode.Continuous) {
                listState.animateScrollToItem(pageIndex)
            }
            onVisiblePageChanged(pageIndex)
            drawerState.close()
        }
    }

    val zoomIn = {
        scale = (scale + 0.25f).coerceAtMost(4f)
    }
    val zoomOut = {
        scale = (scale - 0.25f).coerceAtLeast(1f)
        if (scale == 1f) offset = Offset.Zero
    }
    val fitWidth = {
        scale = 1f
        offset = Offset.Zero
    }
    val rotate = {
        pageRotation = (pageRotation + 90) % 360
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val usePermanentNavigation = maxWidth >= 900.dp
        val navigationPanel: @Composable () -> Unit = {
            ReaderNavigationPanel(
                state = state,
                layoutMode = layoutMode,
                scale = scale,
                pageRotation = pageRotation,
                permanent = usePermanentNavigation,
                onLayoutModeChange = { layoutMode = it },
                onJumpToPage = jumpToPage,
                onShowJumpDialog = { showJumpDialog = true },
                onBookmarkCurrentPage = onBookmarkCurrentPage,
                onRemoveBookmark = onRemoveBookmark,
                onSearchQueryChange = onSearchQueryChange,
                onSearch = onSearch,
                onZoomIn = zoomIn,
                onZoomOut = zoomOut,
                onFitWidth = fitWidth,
                onRotate = rotate
            )
        }

        if (usePermanentNavigation) {
            Row(modifier = Modifier.fillMaxSize()) {
                navigationPanel()
                ReaderScaffold(
                    modifier = Modifier.weight(1f),
                    state = state,
                    settings = settings,
                    layoutMode = layoutMode,
                    listState = listState,
                    scale = scale,
                    offset = offset,
                    pageRotation = pageRotation,
                    transformState = transformState,
                    showNavigationButton = false,
                    onBack = onBack,
                    onToggleToolbar = onToggleToolbar,
                    onOpenNavigation = {},
                    onShowJumpDialog = { showJumpDialog = true },
                    onBookmarkCurrentPage = onBookmarkCurrentPage,
                    onJumpToPage = jumpToPage,
                    onRenderPage = onRenderPage,
                    onDoubleTapZoom = {
                        scale = if (scale == 1f) 2f else 1f
                        offset = Offset.Zero
                    }
                )
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = navigationPanel
            ) {
                ReaderScaffold(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    settings = settings,
                    layoutMode = layoutMode,
                    listState = listState,
                    scale = scale,
                    offset = offset,
                    pageRotation = pageRotation,
                    transformState = transformState,
                    showNavigationButton = true,
                    onBack = onBack,
                    onToggleToolbar = onToggleToolbar,
                    onOpenNavigation = { scope.launch { drawerState.open() } },
                    onShowJumpDialog = { showJumpDialog = true },
                    onBookmarkCurrentPage = onBookmarkCurrentPage,
                    onJumpToPage = jumpToPage,
                    onRenderPage = onRenderPage,
                    onDoubleTapZoom = {
                        scale = if (scale == 1f) 2f else 1f
                        offset = Offset.Zero
                    }
                )
            }
        }
    }

    if (showJumpDialog) {
        JumpToPageDialog(
            pageCount = state.pageCount,
            onDismiss = { showJumpDialog = false },
            onJump = { pageIndex ->
                showJumpDialog = false
                jumpToPage(pageIndex)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScaffold(
    modifier: Modifier,
    state: ReaderUiState,
    settings: AppSettings,
    layoutMode: ReaderLayoutMode,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scale: Float,
    offset: Offset,
    pageRotation: Int,
    transformState: androidx.compose.foundation.gestures.TransformableState,
    showNavigationButton: Boolean,
    onBack: () -> Unit,
    onToggleToolbar: () -> Unit,
    onOpenNavigation: () -> Unit,
    onShowJumpDialog: () -> Unit,
    onBookmarkCurrentPage: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onRenderPage: (Int, Int) -> Unit,
    onDoubleTapZoom: () -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.toolbarVisible) {
                TopAppBar(
                    title = {
                        Text(
                            state.document?.displayName.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = onShowJumpDialog) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = stringResource(R.string.jump_to_page))
                        }
                        IconButton(onClick = onBookmarkCurrentPage) {
                            Icon(
                                if (state.currentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (state.currentPageBookmarked) {
                                    stringResource(R.string.remove_bookmark)
                                } else {
                                    stringResource(R.string.bookmark_page)
                                }
                            )
                        }
                        if (showNavigationButton) {
                            IconButton(onClick = onOpenNavigation) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.navigation_panel))
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (state.toolbarVisible && settings.showPageControls && state.pageCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalIconButton(
                        onClick = { onJumpToPage((state.currentPage - 1).coerceAtLeast(0)) },
                        enabled = state.currentPage > 0
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.previous_page))
                    }
                    Text(stringResource(R.string.page_position, state.currentPage + 1, state.pageCount))
                    FilledTonalIconButton(
                        onClick = { onJumpToPage((state.currentPage + 1).coerceAtMost(state.pageCount - 1)) },
                        enabled = state.currentPage < state.pageCount - 1
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.next_page))
                    }
                }
            }
        }
    ) { padding ->
        ReaderDocumentArea(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = state,
            settings = settings,
            layoutMode = layoutMode,
            listState = listState,
            scale = scale,
            offset = offset,
            pageRotation = pageRotation,
            transformState = transformState,
            onToggleToolbar = onToggleToolbar,
            onDoubleTapZoom = onDoubleTapZoom,
            onRenderPage = onRenderPage
        )
    }
}

@Composable
private fun ReaderDocumentArea(
    modifier: Modifier,
    state: ReaderUiState,
    settings: AppSettings,
    layoutMode: ReaderLayoutMode,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scale: Float,
    offset: Offset,
    pageRotation: Int,
    transformState: androidx.compose.foundation.gestures.TransformableState,
    onToggleToolbar: () -> Unit,
    onDoubleTapZoom: () -> Unit,
    onRenderPage: (Int, Int) -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleToolbar() },
                    onDoubleTap = { onDoubleTapZoom() }
                )
            }
    ) {
        when {
            state.isOpening -> LoadingMessage(stringResource(R.string.reader_loading))
            state.openError -> ErrorMessage(stringResource(R.string.open_failed))
            state.pageCount > 0 && layoutMode == ReaderLayoutMode.SinglePage -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(settings.pageSpacing.dp.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .transformable(transformState),
                    contentAlignment = Alignment.Center
                ) {
                    PdfPageItem(
                        pageIndex = state.currentPage,
                        renderState = state.pageStates[state.currentPage],
                        pageRotation = pageRotation,
                        onRenderPage = onRenderPage
                    )
                }
            }
            state.pageCount > 0 -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .transformable(transformState),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = settings.pageSpacing.dp.dp,
                        bottom = settings.pageSpacing.dp.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(settings.pageSpacing.dp.dp)
                ) {
                    items(state.pageCount) { index ->
                        PdfPageItem(
                            pageIndex = index,
                            renderState = state.pageStates[index],
                            pageRotation = pageRotation,
                            onRenderPage = onRenderPage
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderNavigationPanel(
    state: ReaderUiState,
    layoutMode: ReaderLayoutMode,
    scale: Float,
    pageRotation: Int,
    permanent: Boolean,
    onLayoutModeChange: (ReaderLayoutMode) -> Unit,
    onJumpToPage: (Int) -> Unit,
    onShowJumpDialog: () -> Unit,
    onBookmarkCurrentPage: () -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitWidth: () -> Unit,
    onRotate: () -> Unit
) {
    val panelModifier = Modifier
        .width(320.dp)
        .fillMaxHeight()
    val content: @Composable () -> Unit = {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.navigation_panel),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = state.document?.displayName.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.pageCount > 0) {
                        Text(
                            text = stringResource(R.string.page_position, state.currentPage + 1, state.pageCount),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                HorizontalDivider()
            }
            item {
                NavigationSectionTitle(stringResource(R.string.view_mode))
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = layoutMode == ReaderLayoutMode.Continuous,
                        onClick = { onLayoutModeChange(ReaderLayoutMode.Continuous) },
                        label = { Text(stringResource(R.string.mode_continuous)) }
                    )
                    FilterChip(
                        selected = layoutMode == ReaderLayoutMode.SinglePage,
                        onClick = { onLayoutModeChange(ReaderLayoutMode.SinglePage) },
                        label = { Text(stringResource(R.string.mode_single_page)) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { onJumpToPage((state.currentPage - 1).coerceAtLeast(0)) },
                        enabled = state.currentPage > 0
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.previous_page))
                    }
                    FilledTonalIconButton(
                        onClick = onShowJumpDialog,
                        enabled = state.pageCount > 0
                    ) {
                        Icon(Icons.Default.FormatListNumbered, contentDescription = stringResource(R.string.jump_to_page))
                    }
                    FilledTonalIconButton(
                        onClick = { onJumpToPage((state.currentPage + 1).coerceAtMost(state.pageCount - 1)) },
                        enabled = state.currentPage < state.pageCount - 1
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.next_page))
                    }
                    FilledTonalIconButton(
                        onClick = onBookmarkCurrentPage,
                        enabled = state.pageCount > 0
                    ) {
                        Icon(
                            if (state.currentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.bookmark_page)
                        )
                    }
                }
                HorizontalDivider()
            }
            item {
                NavigationSectionTitle(stringResource(R.string.view_controls))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(onClick = onZoomOut, enabled = scale > 1f) {
                        Icon(Icons.Default.ZoomOut, contentDescription = stringResource(R.string.zoom_out))
                    }
                    FilledTonalIconButton(onClick = onZoomIn, enabled = scale < 4f) {
                        Icon(Icons.Default.ZoomIn, contentDescription = stringResource(R.string.zoom_in))
                    }
                    FilledTonalIconButton(onClick = onFitWidth) {
                        Icon(Icons.Default.ZoomOutMap, contentDescription = stringResource(R.string.fit_width))
                    }
                    FilledTonalIconButton(onClick = onRotate) {
                        Icon(Icons.Default.Rotate90DegreesCw, contentDescription = stringResource(R.string.rotate_page))
                    }
                }
                Text(
                    text = stringResource(
                        R.string.view_status,
                        (scale * 100f).roundToInt(),
                        pageRotation
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
            item {
                NavigationSectionTitle(stringResource(R.string.bookmarks))
                Button(
                    onClick = onBookmarkCurrentPage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (state.currentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (state.currentPageBookmarked) {
                            stringResource(R.string.remove_current_bookmark)
                        } else {
                            stringResource(R.string.bookmark_current_page)
                        }
                    )
                }
            }
            if (state.bookmarks.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_bookmarks),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.bookmarks.size) { index ->
                    val bookmark = state.bookmarks[index]
                    ListItem(
                        modifier = Modifier.clickable { onJumpToPage(bookmark.pageIndex) },
                        leadingContent = {
                            Icon(Icons.Default.Bookmark, contentDescription = null)
                        },
                        headlineContent = {
                            Text(stringResource(R.string.page_position_unknown_total, bookmark.pageIndex + 1))
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemoveBookmark(bookmark.pageIndex) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_bookmark))
                            }
                        }
                    )
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                NavigationSectionTitle(stringResource(R.string.search_document))
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    label = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true
                )
                Button(
                    onClick = onSearch,
                    enabled = !state.isSearching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    if (state.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.search))
                }
                SearchStatus(state.searchFailure, state.searchHits.size, state.searchCompleted)
            }
            items(state.searchHits.size) { index ->
                val hit = state.searchHits[index]
                ListItem(
                    modifier = Modifier.clickable { onJumpToPage(hit.pageIndex) },
                    leadingContent = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    headlineContent = {
                        Text(stringResource(R.string.page_position_unknown_total, hit.pageIndex + 1))
                    },
                    supportingContent = {
                        Text(hit.snippet, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                NavigationSectionTitle(stringResource(R.string.pages))
            }
            items(state.pageCount) { pageIndex ->
                ListItem(
                    modifier = Modifier.clickable { onJumpToPage(pageIndex) },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                    },
                    headlineContent = {
                        Text(stringResource(R.string.page_position_unknown_total, pageIndex + 1))
                    },
                    supportingContent = if (pageIndex == state.currentPage) {
                        { Text(stringResource(R.string.current_page)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
    if (permanent) {
        Surface(
            modifier = panelModifier,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            content()
        }
    } else {
        ModalDrawerSheet(modifier = panelModifier) {
            content()
        }
    }
}

@Composable
private fun SearchStatus(failure: PdfSearchFailure?, hitCount: Int, completed: Boolean) {
    val text = when {
        failure == PdfSearchFailure.EmptyQuery -> stringResource(R.string.search_empty_query)
        failure == PdfSearchFailure.AccessUnavailable -> stringResource(R.string.search_access_unavailable)
        failure == PdfSearchFailure.Unsupported -> stringResource(R.string.search_unsupported)
        failure == PdfSearchFailure.Failed -> stringResource(R.string.search_failed)
        hitCount > 0 -> stringResource(R.string.search_results_count, hitCount)
        completed -> stringResource(R.string.search_no_results)
        else -> null
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (failure == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun NavigationSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    renderState: PageRenderState?,
    pageRotation: Int,
    onRenderPage: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val targetWidthPx = with(density) { 760.dp.roundToPx() }
    LaunchedEffect(pageIndex, targetWidthPx) {
        onRenderPage(pageIndex, targetWidthPx)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when (renderState) {
            is PageRenderState.Ready -> {
                Image(
                    bitmap = renderState.bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.page_position_unknown_total, pageIndex + 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { rotationZ = pageRotation.toFloat() },
                    contentScale = ContentScale.FillWidth
                )
            }
            is PageRenderState.Failed -> ErrorMessage(stringResource(R.string.page_render_failed))
            PageRenderState.Loading, null -> LoadingMessage(stringResource(R.string.rendering_page))
        }
    }
}

@Composable
private fun LoadingMessage(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorMessage(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(24.dp)
    )
}

private enum class ReaderLayoutMode {
    Continuous,
    SinglePage
}

@Composable
private fun JumpToPageDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.jump_to_page)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    invalid = false
                },
                label = { Text(stringResource(R.string.page_number)) },
                isError = invalid,
                supportingText = {
                    if (invalid) Text(stringResource(R.string.invalid_page_number, pageCount))
                },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = {
                val pageIndex = PageJumpValidator.validate(input, pageCount)
                if (pageIndex == null) invalid = true else onJump(pageIndex)
            }) {
                Text(stringResource(R.string.go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
