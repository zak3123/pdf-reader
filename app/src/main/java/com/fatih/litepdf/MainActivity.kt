package com.fatih.litepdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fatih.litepdf.domain.model.AppSettings
import com.fatih.litepdf.navigation.Routes
import com.fatih.litepdf.ui.home.HomeOpenError
import com.fatih.litepdf.ui.home.HomeScreen
import com.fatih.litepdf.ui.home.HomeViewModel
import com.fatih.litepdf.ui.reader.ReaderScreen
import com.fatih.litepdf.ui.reader.ReaderViewModel
import com.fatih.litepdf.ui.settings.AboutScreen
import com.fatih.litepdf.ui.settings.SettingsScreen
import com.fatih.litepdf.ui.settings.SettingsViewModel
import com.fatih.litepdf.ui.theme.LitePdfTheme

class MainActivity : ComponentActivity() {
    private var incomingUri by mutableStateOf<Uri?>(null)
    private var incomingFlags by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            val container = (application as LitePdfApplication).appContainer
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    settingsRepository = container.settingsRepository,
                    documentRepository = container.documentRepository,
                    bitmapCache = container.bitmapCache
                )
            )
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            LitePdfTheme(themeMode = settings.themeMode) {
                LitePdfApp(
                    settings = settings,
                    settingsViewModel = settingsViewModel,
                    incomingUri = incomingUri,
                    incomingFlags = incomingFlags,
                    onIncomingConsumed = { incomingUri = null },
                    persistReadPermission = ::persistReadPermission
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            incomingFlags = intent.flags
            incomingUri = intent.data
        }
    }

    private fun persistReadPermission(uri: Uri, flags: Int) {
        val takeFlags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (takeFlags == 0) return
        try {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: SecurityException) {
            // Some providers grant temporary, non-persistable access. The current open still proceeds.
        } catch (_: IllegalArgumentException) {
            // Non-SAF providers can reject persistable permission.
        }
    }
}

@Composable
private fun LitePdfApp(
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    incomingUri: Uri?,
    incomingFlags: Int,
    onIncomingConsumed: () -> Unit,
    persistReadPermission: (Uri, Int) -> Unit
) {
    val container = (androidx.compose.ui.platform.LocalContext.current.applicationContext as LitePdfApplication).appContainer
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(container.documentRepository)
    )
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    var relocateDocumentId by remember { mutableStateOf<String?>(null) }
    val emptyPdfMessage = stringResource(R.string.empty_pdf)
    val unsupportedPdfMessage = stringResource(R.string.unsupported_pdf)
    val providerErrorMessage = stringResource(R.string.provider_error)
    val accessLostMessage = stringResource(R.string.access_lost_title)

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        persistReadPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        homeViewModel.openUri(uri, relocateDocumentId)
        relocateDocumentId = null
    }

    LaunchedEffect(incomingUri) {
        val uri = incomingUri ?: return@LaunchedEffect
        persistReadPermission(uri, incomingFlags)
        homeViewModel.openUri(uri)
        onIncomingConsumed()
    }

    LaunchedEffect(homeViewModel) {
        homeViewModel.openedDocuments.collect { documentId ->
            navController.navigate(Routes.reader(documentId))
        }
    }

    LaunchedEffect(homeState.openError) {
        val error = homeState.openError ?: return@LaunchedEffect
        if (error != HomeOpenError.AccessUnavailable) {
            val message = when (error) {
                HomeOpenError.EmptyFile -> emptyPdfMessage
                HomeOpenError.UnsupportedOrCorrupt -> unsupportedPdfMessage
                HomeOpenError.ProviderError -> providerErrorMessage
                HomeOpenError.AccessUnavailable -> accessLostMessage
            }
            snackbarHostState.showSnackbar(message)
            homeViewModel.clearOpenError()
        }
    }

    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScreen(
                state = homeState,
                snackbarHostState = snackbarHostState,
                onOpenPdf = { openDocumentLauncher.launch(arrayOf("application/pdf")) },
                onOpenRecent = homeViewModel::reopen,
                onRemoveRecent = homeViewModel::removeRecent,
                onClearHistory = homeViewModel::clearRecents,
                onSettings = { navController.navigate(Routes.Settings) }
            )
        }
        composable(Routes.Reader) { entry ->
            val documentId = entry.arguments?.getString("documentId").orEmpty()
            val readerViewModel: ReaderViewModel = viewModel(
                key = "reader-$documentId",
                factory = ReaderViewModel.Factory(
                    documentId = documentId,
                    repository = container.documentRepository,
                    settingsRepository = container.settingsRepository,
                    pdfEngine = container.pdfEngine,
                    textSearchEngine = container.textSearchEngine,
                    bitmapCache = container.bitmapCache,
                    thumbnailCache = container.thumbnailCache,
                    documentStructureReader = container.documentStructureReader
                )
            )
            val readerState by readerViewModel.uiState.collectAsStateWithLifecycle()
            ReaderScreen(
                state = readerState,
                settings = settings,
                isLowRamDevice = container.isLowRamDevice,
                onBack = { navController.popBackStack() },
                onToggleToolbar = readerViewModel::toggleToolbar,
                onVisiblePageChanged = readerViewModel::onVisiblePageChanged,
                onRenderPage = readerViewModel::renderPage,
                onBookmarkCurrentPage = readerViewModel::bookmarkCurrentPage,
                onRemoveBookmark = readerViewModel::removeBookmark,
                onSearchQueryChange = readerViewModel::updateSearchQuery,
                onSearch = readerViewModel::searchText,
                onSearchHitSelected = readerViewModel::selectSearchHit,
                onRenderThumbnail = readerViewModel::renderThumbnail
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                settings = settings,
                onBack = { navController.popBackStack() },
                onThemeMode = settingsViewModel::setThemeMode,
                onPageSpacing = settingsViewModel::setPageSpacing,
                onKeepAwake = settingsViewModel::setKeepAwake,
                onRememberLastPage = settingsViewModel::setRememberLastPage,
                onShowPageControls = settingsViewModel::setShowPageControls,
                onClearBitmapCache = settingsViewModel::clearBitmapCache,
                onClearRecents = settingsViewModel::clearRecents,
                onAbout = { navController.navigate(Routes.About) }
            )
        }
        composable(Routes.About) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }

    if (homeState.pendingRelocateDocumentId != null && homeState.openError == HomeOpenError.AccessUnavailable) {
        AlertDialog(
            onDismissRequest = homeViewModel::dismissRelocate,
            title = { Text(stringResource(R.string.access_lost_title)) },
            text = { Text(stringResource(R.string.access_lost_body)) },
            confirmButton = {
                Button(onClick = {
                    relocateDocumentId = homeState.pendingRelocateDocumentId
                    homeViewModel.dismissRelocate()
                    openDocumentLauncher.launch(arrayOf("application/pdf"))
                }) {
                    Text(stringResource(R.string.locate))
                }
            },
            dismissButton = {
                TextButton(onClick = homeViewModel::dismissRelocate) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
