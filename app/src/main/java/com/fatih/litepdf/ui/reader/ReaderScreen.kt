package com.fatih.litepdf.ui.reader

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fatih.litepdf.R
import com.fatih.litepdf.domain.model.AppSettings
import com.fatih.litepdf.pdf.PdfSearchFailure
import com.fatih.litepdf.pdf.PdfWordHighlight
import com.fatih.litepdf.pdf.PdfPageLink
import com.fatih.litepdf.util.PageJumpValidator
import com.fatih.litepdf.ui.theme.SumatraLikeColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    settings: AppSettings,
    isLowRamDevice: Boolean,
    onBack: () -> Unit,
    onOpenDocument: () -> Unit,
    onToggleToolbar: () -> Unit,
    onVisiblePageChanged: (Int) -> Unit,
    onRenderPage: (Int, Int) -> Unit,
    onBookmarkCurrentPage: () -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchHitSelected: (Int) -> Unit,
    onRenderThumbnail: (Int) -> Unit
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
    val configuration = LocalConfiguration.current
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
            if (layoutMode != ReaderLayoutMode.SinglePage) {
                listState.animateScrollToItem(pageIndex)
            }
            onVisiblePageChanged(pageIndex)
            drawerState.close()
        }
    }

    val fitWidth = {
        scale = 1f
        offset = Offset.Zero
    }
    val rotate = {
        pageRotation = (pageRotation + 90) % 360
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val usePermanentNavigation =
            configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP &&
                maxWidth >= TABLET_NAVIGATION_BREAKPOINT_DP.dp
        val navigationPanelWidth = if (maxWidth >= EXPANDED_NAVIGATION_BREAKPOINT_DP.dp) {
            EXPANDED_NAVIGATION_WIDTH_DP.dp
        } else {
            MEDIUM_NAVIGATION_WIDTH_DP.dp
        }
        val navigationPanel: @Composable () -> Unit = {
            ReaderNavigationPanel(
                state = state,
                permanent = usePermanentNavigation,
                panelWidth = navigationPanelWidth,
                onJumpToPage = jumpToPage,
                onBookmarkCurrentPage = onBookmarkCurrentPage,
                onRemoveBookmark = onRemoveBookmark,
                onSearchQueryChange = onSearchQueryChange,
                onSearch = onSearch,
                onSearchHitSelected = { pageIndex ->
                    onSearchHitSelected(pageIndex)
                    jumpToPage(pageIndex)
                },
                onRenderThumbnail = onRenderThumbnail,
                onCloseNavigation = { scope.launch { drawerState.close() } }
            )
        }

        if (usePermanentNavigation) {
            Row(modifier = Modifier.fillMaxSize()) {
                navigationPanel()
                ReaderScaffold(
                    modifier = Modifier.weight(1f),
                    state = state,
                    settings = settings,
                    isLowRamDevice = isLowRamDevice,
                    layoutMode = layoutMode,
                    listState = listState,
                    scale = scale,
                    offset = offset,
                    pageRotation = pageRotation,
                    transformState = transformState,
                    showNavigationButton = false,
                    onBack = onBack,
                    onOpenDocument = onOpenDocument,
                    onToggleToolbar = onToggleToolbar,
                    onOpenNavigation = {},
                    onShowJumpDialog = { showJumpDialog = true },
                    onBookmarkCurrentPage = onBookmarkCurrentPage,
                    onLayoutModeChange = { layoutMode = it },
                    onFitWidth = fitWidth,
                    onRotate = rotate,
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
                Box(modifier = Modifier.fillMaxSize()) {
                    ReaderScaffold(
                        modifier = Modifier.fillMaxSize(),
                        state = state,
                        settings = settings,
                        isLowRamDevice = isLowRamDevice,
                        layoutMode = layoutMode,
                        listState = listState,
                        scale = scale,
                        offset = offset,
                        pageRotation = pageRotation,
                        transformState = transformState,
                        showNavigationButton = true,
                        onBack = onBack,
                        onOpenDocument = onOpenDocument,
                        onToggleToolbar = onToggleToolbar,
                        onOpenNavigation = { scope.launch { drawerState.open() } },
                        onShowJumpDialog = { showJumpDialog = true },
                        onBookmarkCurrentPage = onBookmarkCurrentPage,
                        onLayoutModeChange = { layoutMode = it },
                        onFitWidth = fitWidth,
                        onRotate = rotate,
                        onJumpToPage = jumpToPage,
                        onRenderPage = onRenderPage,
                        onDoubleTapZoom = {
                            scale = if (scale == 1f) 2f else 1f
                            offset = Offset.Zero
                        }
                    )
                    SidebarDragHandle(
                        expanded = drawerState.isOpen,
                        onToggle = {
                            scope.launch {
                                if (drawerState.isOpen) drawerState.close() else drawerState.open()
                            }
                        },
                        onDragOpen = { scope.launch { drawerState.open() } },
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
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

@Composable
private fun SidebarDragHandle(
    expanded: Boolean,
    onToggle: () -> Unit,
    onDragOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(SIDEBAR_HANDLE_TOUCH_WIDTH_DP.dp)
            .height(SIDEBAR_HANDLE_HEIGHT_DP.dp)
            .pointerInput(expanded) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (!expanded && dragAmount > 8f) {
                        onDragOpen()
                    }
                }
            }
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
            tonalElevation = 2.dp,
            modifier = Modifier
                .width(SIDEBAR_HANDLE_VISUAL_WIDTH_DP.dp)
                .fillMaxHeight()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) {
                        "Close navigation panel"
                    } else {
                        "Open navigation panel"
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ReaderToolbar(
    state: ReaderUiState,
    layoutMode: ReaderLayoutMode,
    showNavigationButton: Boolean,
    onBack: () -> Unit,
    onOpenDocument: () -> Unit,
    onShowJumpDialog: () -> Unit,
    onBookmarkCurrentPage: () -> Unit,
    onLayoutModeChange: (ReaderLayoutMode) -> Unit,
    onFitWidth: () -> Unit,
    onRotate: () -> Unit,
    onOpenNavigation: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        color = SumatraLikeColors.ToolbarLight,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .border(1.dp, SumatraLikeColors.DividerLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = state.document?.displayName.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            )
            IconButton(onClick = onOpenNavigation) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_document))
            }
            IconButton(onClick = onBookmarkCurrentPage) {
                Icon(
                    if (state.currentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = stringResource(R.string.bookmark_page)
                )
            }
            if (showNavigationButton) {
                IconButton(onClick = onOpenNavigation) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.navigation_panel))
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_pdf)) },
                        onClick = {
                            menuExpanded = false
                            onOpenDocument()
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.jump_to_page)) },
                        onClick = {
                            menuExpanded = false
                            onShowJumpDialog()
                        },
                        leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fit_width)) },
                        onClick = {
                            menuExpanded = false
                            onFitWidth()
                        },
                        leadingIcon = { Icon(Icons.Default.ZoomOutMap, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mode_continuous)) },
                        onClick = {
                            menuExpanded = false
                            onLayoutModeChange(ReaderLayoutMode.Continuous)
                        },
                        leadingIcon = if (layoutMode == ReaderLayoutMode.Continuous) {
                            { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                        } else {
                            null
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mode_single_page)) },
                        onClick = {
                            menuExpanded = false
                            onLayoutModeChange(ReaderLayoutMode.SinglePage)
                        },
                        leadingIcon = if (layoutMode == ReaderLayoutMode.SinglePage) {
                            { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                        } else {
                            null
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Horizontal") },
                        onClick = {
                            menuExpanded = false
                            onLayoutModeChange(ReaderLayoutMode.Horizontal)
                        },
                        leadingIcon = if (layoutMode == ReaderLayoutMode.Horizontal) {
                            { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                        } else {
                            null
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rotate_page)) },
                        onClick = {
                            menuExpanded = false
                            onRotate()
                        },
                        leadingIcon = { Icon(Icons.Default.Rotate90DegreesCw, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScaffold(
    modifier: Modifier,
    state: ReaderUiState,
    settings: AppSettings,
    isLowRamDevice: Boolean,
    layoutMode: ReaderLayoutMode,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scale: Float,
    offset: Offset,
    pageRotation: Int,
    transformState: androidx.compose.foundation.gestures.TransformableState,
    showNavigationButton: Boolean,
    onBack: () -> Unit,
    onOpenDocument: () -> Unit,
    onToggleToolbar: () -> Unit,
    onOpenNavigation: () -> Unit,
    onShowJumpDialog: () -> Unit,
    onBookmarkCurrentPage: () -> Unit,
    onLayoutModeChange: (ReaderLayoutMode) -> Unit,
    onFitWidth: () -> Unit,
    onRotate: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onRenderPage: (Int, Int) -> Unit,
    onDoubleTapZoom: () -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.toolbarVisible) {
                ReaderToolbar(
                    state = state,
                    layoutMode = layoutMode,
                    showNavigationButton = showNavigationButton,
                    onBack = onBack,
                    onOpenDocument = onOpenDocument,
                    onShowJumpDialog = onShowJumpDialog,
                    onBookmarkCurrentPage = onBookmarkCurrentPage,
                    onLayoutModeChange = onLayoutModeChange,
                    onFitWidth = onFitWidth,
                    onRotate = onRotate,
                    onOpenNavigation = onOpenNavigation
                )
            }
        },
        bottomBar = {
            if (state.toolbarVisible && settings.showPageControls && state.pageCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .background(SumatraLikeColors.ToolbarLight)
                        .border(1.dp, SumatraLikeColors.DividerLight)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { onJumpToPage((state.currentPage - 1).coerceAtLeast(0)) },
                        enabled = state.currentPage > 0
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.previous_page))
                    }
                    Text(
                        stringResource(R.string.page_position, state.currentPage + 1, state.pageCount),
                        modifier = Modifier.clickable(onClick = onShowJumpDialog)
                    )
                    IconButton(
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
            isLowRamDevice = isLowRamDevice,
            layoutMode = layoutMode,
            listState = listState,
            scale = scale,
            offset = offset,
            pageRotation = pageRotation,
            transformState = transformState,
            onToggleToolbar = onToggleToolbar,
            onDoubleTapZoom = onDoubleTapZoom,
            onJumpToPage = onJumpToPage,
            onRenderPage = onRenderPage
        )
    }
}

@Composable
private fun ReaderDocumentArea(
    modifier: Modifier,
    state: ReaderUiState,
    settings: AppSettings,
    isLowRamDevice: Boolean,
    layoutMode: ReaderLayoutMode,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scale: Float,
    offset: Offset,
    pageRotation: Int,
    transformState: androidx.compose.foundation.gestures.TransformableState,
    onToggleToolbar: () -> Unit,
    onDoubleTapZoom: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    onRenderPage: (Int, Int) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = modifier
            .background(SumatraLikeColors.CanvasLight)
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
                        pageAspectRatio = state.pageAspectRatios[state.currentPage],
                        searchHighlights = state.currentSearchHighlights,
                        isLowRamDevice = isLowRamDevice,
                        shouldRender = true,
                        renderScale = scale,
                        pageRotation = pageRotation,
                        pageLinks = state.linksByPage[state.currentPage].orEmpty(),
                        onLinkClick = { link ->
                            when {
                                link.targetPageIndex != null -> onJumpToPage(link.targetPageIndex)
                                !link.uri.isNullOrBlank() -> uriHandler.openUri(link.uri)
                            }
                        },
                        onRenderPage = onRenderPage
                    )
                }
            }
            state.pageCount > 0 && layoutMode == ReaderLayoutMode.Horizontal -> {
                LazyRow(
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
                    horizontalArrangement = Arrangement.spacedBy(settings.pageSpacing.dp.dp)
                ) {
                    items(state.pageCount) { index ->
                        PdfPageItem(
                            pageIndex = index,
                            renderState = state.pageStates[index],
                            pageAspectRatio = state.pageAspectRatios[index],
                            searchHighlights = state.searchHighlightsForPage(index),
                            isLowRamDevice = isLowRamDevice,
                            shouldRender = kotlin.math.abs(index - state.currentPage) <= 1,
                            renderScale = scale,
                            pageRotation = pageRotation,
                            pageLinks = state.linksByPage[index].orEmpty(),
                            onLinkClick = { link ->
                                when {
                                    link.targetPageIndex != null -> onJumpToPage(link.targetPageIndex)
                                    !link.uri.isNullOrBlank() -> uriHandler.openUri(link.uri)
                                }
                            },
                            onRenderPage = onRenderPage
                        )
                    }
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
                            pageAspectRatio = state.pageAspectRatios[index],
                            searchHighlights = state.searchHighlightsForPage(index),
                            isLowRamDevice = isLowRamDevice,
                            shouldRender = kotlin.math.abs(index - state.currentPage) <= 1,
                            renderScale = scale,
                            pageRotation = pageRotation,
                            pageLinks = state.linksByPage[index].orEmpty(),
                            onLinkClick = { link ->
                                when {
                                    link.targetPageIndex != null -> onJumpToPage(link.targetPageIndex)
                                    !link.uri.isNullOrBlank() -> uriHandler.openUri(link.uri)
                                }
                            },
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
    permanent: Boolean,
    panelWidth: androidx.compose.ui.unit.Dp,
    onJumpToPage: (Int) -> Unit,
    onBookmarkCurrentPage: () -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchHitSelected: (Int) -> Unit,
    onRenderThumbnail: (Int) -> Unit,
    onCloseNavigation: () -> Unit
) {
    val panelModifier = Modifier
        .width(panelWidth)
        .fillMaxHeight()
        .pointerInput(permanent) {
            if (!permanent) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -8f) {
                        onCloseNavigation()
                    }
                }
            }
        }
    val content: @Composable () -> Unit = {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.navigation_panel),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (!permanent) {
                            IconButton(onClick = onCloseNavigation) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        }
                    }
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
                    modifier = Modifier.clickable { onSearchHitSelected(hit.pageIndex) },
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
                NavigationSectionTitle("Outline")
            }
            if (state.outline.isEmpty()) {
                item {
                    Text(
                        text = "No outline",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.outline.size) { outlineIndex ->
                    val item = state.outline[outlineIndex]
                    ListItem(
                        modifier = Modifier
                            .clickable { onJumpToPage(item.pageIndex) }
                            .padding(start = (item.depth * 12).dp),
                        headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(stringResource(R.string.page_position_unknown_total, item.pageIndex + 1))
                        }
                    )
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                NavigationSectionTitle(stringResource(R.string.pages))
            }
            items(state.pageCount) { pageIndex ->
                LaunchedEffect(pageIndex) {
                    onRenderThumbnail(pageIndex)
                }
                ListItem(
                    modifier = Modifier.clickable { onJumpToPage(pageIndex) },
                    leadingContent = {
                        val thumbnail = state.pageThumbnails[pageIndex]
                        if (thumbnail != null && !thumbnail.isRecycled) {
                            Image(
                                bitmap = thumbnail.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(60.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        }
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
    pageAspectRatio: Float?,
    searchHighlights: List<PdfWordHighlight>,
    isLowRamDevice: Boolean,
    shouldRender: Boolean,
    renderScale: Float,
    pageRotation: Int,
    pageLinks: List<PdfPageLink>,
    onLinkClick: (PdfPageLink) -> Unit,
    onRenderPage: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val baseAspectRatio = pageAspectRatio ?: DEFAULT_PAGE_ASPECT_RATIO
    val displayAspectRatio = if (pageRotation % 180 == 0) {
        baseAspectRatio
    } else {
        1f / baseAspectRatio
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(displayAspectRatio)
            .shadow(3.dp, clip = false)
            .border(1.dp, SumatraLikeColors.DividerLight)
            .background(androidx.compose.ui.graphics.Color.White),
        contentAlignment = Alignment.Center
    ) {
        val targetWidthPx = with(density) {
            val baseWidth = (maxWidth.roundToPx() * renderScale).roundToInt()
            baseWidth.coerceAtMost(
                if (isLowRamDevice) LOW_RAM_TARGET_WIDTH_PX else MAX_ZOOM_TARGET_WIDTH_PX
            ).coerceAtLeast(MIN_TARGET_WIDTH_PX)
        }
        LaunchedEffect(pageIndex, targetWidthPx, shouldRender) {
            if (shouldRender) {
                onRenderPage(pageIndex, targetWidthPx)
            }
        }
        when (renderState) {
            is PageRenderState.Ready -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = pageRotation.toFloat() }
                ) {
                    Image(
                        bitmap = renderState.bitmap.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.page_position_unknown_total,
                            pageIndex + 1
                        ),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    if (searchHighlights.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            searchHighlights.forEach { highlight ->
                                drawRect(
                                    color = SEARCH_HIGHLIGHT_COLOR,
                                    topLeft = Offset(
                                        x = highlight.normX * size.width,
                                        y = highlight.normY * size.height
                                    ),
                                    size = Size(
                                        width = highlight.normW * size.width,
                                        height = highlight.normH * size.height
                                    )
                                )
                            }
                        }
                    }
                    if (pageLinks.isNotEmpty()) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(pageLinks) {
                                    detectTapGestures { tap ->
                                        pageLinks.firstOrNull { link ->
                                            val left = link.bounds.normX * size.width
                                            val top = link.bounds.normY * size.height
                                            val right = left + link.bounds.normW * size.width
                                            val bottom = top + link.bounds.normH * size.height
                                            tap.x in left..right && tap.y in top..bottom
                                        }?.let(onLinkClick)
                                    }
                                }
                        ) {}
                    }
                }
            }
            is PageRenderState.Failed -> ErrorMessage(stringResource(R.string.page_render_failed))
            PageRenderState.Loading, null -> LoadingMessage(stringResource(R.string.rendering_page))
        }
    }
}

private const val DEFAULT_PAGE_ASPECT_RATIO = 1f / 1.414f
private const val LOW_RAM_TARGET_WIDTH_PX = 1400
private const val MIN_TARGET_WIDTH_PX = 120
private const val MAX_ZOOM_TARGET_WIDTH_PX = 2800
private const val TABLET_SMALLEST_WIDTH_DP = 600
private const val TABLET_NAVIGATION_BREAKPOINT_DP = 840
private const val EXPANDED_NAVIGATION_BREAKPOINT_DP = 840
private const val MEDIUM_NAVIGATION_WIDTH_DP = 320
private const val EXPANDED_NAVIGATION_WIDTH_DP = 360
private const val SIDEBAR_HANDLE_TOUCH_WIDTH_DP = 40
private const val SIDEBAR_HANDLE_VISUAL_WIDTH_DP = 10
private const val SIDEBAR_HANDLE_HEIGHT_DP = 64
private val SEARCH_HIGHLIGHT_COLOR = Color(0xFFFFEB3B).copy(alpha = 0.45f)

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
    SinglePage,
    Horizontal
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
