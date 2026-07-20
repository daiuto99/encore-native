package com.encore.tablet.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import com.encore.core.ui.theme.DarkEncoreColors
import com.encore.core.ui.theme.EncoreColors
import com.encore.core.ui.theme.LightEncoreColors
import com.encore.core.ui.theme.LocalEncoreColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.encore.feature.library.SyncProgress
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.encore.core.data.auth.AuthState
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.preferences.AppPreferences
import com.encore.core.ui.theme.SetColor
import com.encore.core.ui.theme.setCoverColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.encore.core.data.sync.SyncHudState
import kotlinx.coroutines.delay
import com.encore.tablet.R
import com.encore.tablet.audit.LibraryAuditViewModel
import com.encore.tablet.preferences.AppPreferencesViewModel
import com.encore.feature.library.ImportViewModel
import com.encore.feature.library.LibraryListContent
import com.encore.feature.library.LibraryViewModel
import com.encore.feature.library.SetViewModel
import com.encore.feature.library.SongChartEditorScreen
import com.encore.feature.library.SyncViewModel
import com.encore.feature.library.SongEditBottomSheet
import com.encore.tablet.settings.SettingsScreen
import com.encore.feature.performance.SongDetailScreen
import com.encore.feature.performance.SongDetailViewModel
import com.encore.tablet.auth.AuthViewModel
import com.encore.tablet.di.AppContainer
import com.encore.tablet.di.ViewModelFactory
import com.encore.tablet.navigation.Routes
import kotlinx.coroutines.launch

/**
 * Main Screen - Root of the app.
 *
 * Hosts a NavHost with two destinations:
 * - "command_center": The main library + sets management view
 * - Routes.SONG_DETAIL: Full-screen performance mode (no Scaffold overlay)
 *
 * The NavHost is the fix for the crash: a bare navController without a NavHost
 * has no registered graph, causing IllegalStateException on any navigate() call.
 *
 * Based on: docs/design/main UI.png
 */
@Composable
fun MainScreen(
    viewModelFactory: ViewModelFactory,
    appContainer: AppContainer
) {
    val navController = rememberNavController()
    val libraryViewModel: LibraryViewModel = viewModel(factory = viewModelFactory)
    val syncViewModel: SyncViewModel = viewModel(factory = viewModelFactory)
    val importViewModel: ImportViewModel = viewModel(factory = viewModelFactory)
    val setViewModel: SetViewModel = viewModel(factory = viewModelFactory)
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory)
    val appPrefsViewModel: AppPreferencesViewModel = viewModel(factory = viewModelFactory)
    val auditViewModel: LibraryAuditViewModel = viewModel(factory = viewModelFactory)
    val appPreferences by appPrefsViewModel.preferences.collectAsState()
    val syncHudState by syncViewModel.syncHudState.collectAsState()
    val lastSyncTimestamp by syncViewModel.lastSyncTimestamp.collectAsState()
    val availableSets by setViewModel.availableSets.collectAsState()
    val availableSetNumbers = remember(availableSets) { availableSets.map { it.number }.sorted() }
    var isDarkMode by remember { mutableStateOf(false) }
    var editSong by remember { mutableStateOf<SongEntity?>(null) }
    val encoreColors = if (isDarkMode) DarkEncoreColors else LightEncoreColors

    CompositionLocalProvider(LocalEncoreColors provides encoreColors) {
    editSong?.let { song ->
        SongEditBottomSheet(
            song = song,
            onSave = { title, artist, isLeadGuitar, harmonyMode, resetZoom, clearHarmonies, capoEnabled, capoFret, displayKey, bpm ->
                libraryViewModel.updateSongMetadata(song.id, title, artist, isLeadGuitar, harmonyMode, resetZoom, clearHarmonies, capoEnabled, capoFret, displayKey, bpm)
                editSong = null
            },
            onDismiss = { editSong = null },
            onEditChart = {
                editSong = null
                navController.navigate(Routes.chartEditor(song.id))
            }
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = "command_center",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("command_center") {
            CommandCenterScreen(
                libraryViewModel = libraryViewModel,
                syncViewModel = syncViewModel,
                importViewModel = importViewModel,
                setViewModel = setViewModel,
                authViewModel = authViewModel,
                onToggleDarkMode = { isDarkMode = !isDarkMode },
                onSongClick = { songId, setNumber ->
                    navController.navigate(Routes.songDetail(songId, setNumber))
                },
                onEditChart = { songId ->
                    navController.navigate(Routes.chartEditor(songId))
                },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = appPrefsViewModel,
                auditViewModel = auditViewModel,
                onEditSong = { song -> editSong = song },
                onNavigateBack = { navController.popBackStack() },
                onSyncNow = { syncViewModel.triggerGlobalSync() },
                syncHudState = syncHudState,
                lastSyncTimestamp = lastSyncTimestamp,
                onClearAllSets = { setViewModel.clearAllSets() }
            )
        }

        composable(
            route = Routes.SONG_CHART_EDITOR,
            arguments = listOf(navArgument("songId") { type = NavType.StringType })
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
            SongChartEditorScreen(
                songId = songId,
                viewModel = libraryViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Song detail is full-screen — NavHost replaces entire content, no Scaffold around it
        composable(
            route = Routes.SONG_DETAIL,
            arguments = listOf(
                navArgument("songId") { type = NavType.StringType },
                navArgument("setNumber") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
            val setNumber = backStackEntry.arguments?.getInt("setNumber") ?: -1
            val viewModel: SongDetailViewModel = viewModel(factory = viewModelFactory)
            SongDetailScreen(
                viewModel = viewModel,
                songId = songId,
                setNumber = setNumber,
                availableSetNumbers = availableSetNumbers,
                appPreferences = appPreferences,
                onToggleDarkMode = { isDarkMode = !isDarkMode },
                onEditClick = { song -> editSong = song },
                onPageChanged = { editSong = null },
                onNavigateBack = {
                    // popBackStack() returns false when the stack is empty or corrupted.
                    // Fall back to an explicit navigate so the user never lands on a blank screen.
                    val popped = navController.popBackStack()
                    if (!popped) {
                        navController.navigate("command_center") {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onNavigateToSong = { newSongId ->
                    // launchSingleTop prevents stacking multiple song_detail instances.
                    // popUpTo("command_center") clears any previous song_detail entries,
                    // keeping command_center as the sole base so Back always works.
                    navController.navigate(Routes.songDetail(newSongId, setNumber)) {
                        popUpTo("command_center")
                        launchSingleTop = true
                    }
                },
                onNavigateToSongInSet = { newSongId, newSetNumber ->
                    navController.navigate(Routes.songDetail(newSongId, newSetNumber)) {
                        popUpTo("command_center")
                        launchSingleTop = true
                    }
                },
                onQuickSearchSong = { songId ->
                    // Simple push — no popUpTo so Back returns to the current song in the set.
                    navController.navigate(Routes.songDetail(songId, -1))
                },
                onEditChart = { songId ->
                    navController.navigate(Routes.chartEditor(songId))
                },
                syncHudState = syncHudState
            )
        }
    } // end NavHost

        // Full-screen launch loader — logo + progress bar while the first sync runs.
        // Gated to app launch only; background polls keep the small in-bar HUD.
        SyncLaunchLoader(syncHudState = syncHudState)
    } // end Box
    } // end CompositionLocalProvider
}

/**
 * Full-screen branded loading overlay shown only during the initial app-launch sync.
 *
 * State machine (one-time): WAITING → SHOWING → DONE.
 *  - WAITING: shown immediately on launch with an indeterminate bar. If no sync starts
 *    within a short grace window (e.g. library synced recently, so startup sync is skipped),
 *    it dismisses on its own.
 *  - SHOWING: a sync started — show determinate progress (current / total).
 *  - DONE: first sync finished (or nothing to sync) — overlay never returns, so the
 *    2-minute background poller never covers the UI.
 */
@Composable
private fun SyncLaunchLoader(syncHudState: SyncHudState?) {
    val encoreColors = LocalEncoreColors.current
    // 0 = waiting, 1 = showing, 2 = done
    var phase by remember { mutableStateOf(0) }

    LaunchedEffect(syncHudState, phase) {
        when (phase) {
            0 -> if (syncHudState is SyncHudState.InProgress) phase = 1
            1 -> if (syncHudState is SyncHudState.Complete || syncHudState == null) phase = 2
        }
    }
    // Grace window: if the startup sync never begins, stop waiting and reveal the app.
    LaunchedEffect(Unit) {
        delay(2500)
        if (phase == 0) phase = 2
    }
    // Absolute safety cap — the loader can NEVER block the app for more than this, even if
    // sync stalls (e.g. offline at a venue, where each remote check may time out slowly).
    // Whatever sync remains continues in the background under the small in-bar HUD.
    LaunchedEffect(Unit) {
        delay(LAUNCH_LOADER_MAX_MS)
        phase = 2
    }

    if (phase == 2) return

    val inProgress = syncHudState as? SyncHudState.InProgress
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(encoreColors.screenBackground)
            // Tap anywhere to skip straight into the library (offline / in a hurry).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { phase = 2 },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.encore_logo_full),
                contentDescription = "Encore",
                modifier = Modifier.size(132.dp)
            )
            Spacer(modifier = Modifier.height(40.dp))
            if (inProgress != null && inProgress.total > 0) {
                val fraction = inProgress.current.toFloat() / inProgress.total
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.width(220.dp),
                    color = encoreColors.titleText,
                    trackColor = encoreColors.titleText.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Syncing ${inProgress.current} of ${inProgress.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = encoreColors.subtleText
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.width(220.dp),
                    color = encoreColors.titleText,
                    trackColor = encoreColors.titleText.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading your library…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = encoreColors.subtleText
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Tap to skip",
                style = MaterialTheme.typography.labelMedium,
                color = encoreColors.subtleText.copy(alpha = 0.6f)
            )
        }
    }
}

/** Hard ceiling on the launch loader so a stalled/offline sync can never block entry. */
private const val LAUNCH_LOADER_MAX_MS = 10_000L


/**
 * Command Center — the main library + sets management screen.
 *
 * Layout (top to bottom):
 * 1. EncoreHeader — logo, version badge, Import, SAVE/LOAD SET, PERFORM, Settings, avatar
 * 2. Song list with search (LibraryListContent — no nested Scaffold)
 * 3. Sets section with global color-coded filter chips
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterScreen(
    libraryViewModel: LibraryViewModel,
    syncViewModel: SyncViewModel,
    importViewModel: ImportViewModel,
    setViewModel: SetViewModel,
    authViewModel: AuthViewModel,
    onToggleDarkMode: () -> Unit,
    onSongClick: (songId: String, setNumber: Int?) -> Unit,
    onEditChart: ((songId: String) -> Unit)? = null,
    onSettingsClick: () -> Unit = {}
) {
    var selectedSetFilter by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val importResult by importViewModel.importResult.collectAsState()
    val isImporting by importViewModel.isImporting.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    var showProfileSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showSaveSetDialog by remember { mutableStateOf(false) }
    var showLoadSetDialog by remember { mutableStateOf(false) }
    var showCloudSetPicker by remember { mutableStateOf(false) }
    var showSaveToCloudDialog by remember { mutableStateOf(false) }
    var saveSetName by remember { mutableStateOf("") }
    var cloudSetName by remember { mutableStateOf("") }
    val cloudSets by setViewModel.cloudSets.collectAsState()
    val cloudSetsLoading by setViewModel.cloudSetsLoading.collectAsState()
    val currentShowName by setViewModel.currentShowName.collectAsState()
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Sync set filter state into ViewModel
    LaunchedEffect(selectedSetFilter) {
        libraryViewModel.updateSetFilter(selectedSetFilter)
    }

    val syncProgress by importViewModel.syncProgress.collectAsState()
    val connectedFolderUri by importViewModel.connectedFolderUri.collectAsState()
    val availableSets by setViewModel.availableSets.collectAsState()
    val songs by libraryViewModel.songs.collectAsState()
    val setlists by setViewModel.setlists.collectAsState()

    // Folder Sync — OpenDocumentTree gives a persistent tree URI
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        showImportSheet = false
        uri?.let { importViewModel.syncFolder(context, it) }
    }

    // Individual file import — GetMultipleContents uses ACTION_GET_CONTENT
    // Native back-stack handles cancel: Back = up a level, Back again = return to app
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        showImportSheet = false
        if (uris.isNotEmpty()) importViewModel.importSongs(context, uris)
    }

    // Set export — CreateDocument lets the user choose where to save the .encore.json file
    var pendingExportSetlistId by remember { mutableStateOf<String?>(null) }
    var pendingExportSetlistName by remember { mutableStateOf<String?>(null) }
    val exportSetlistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val setlistId = pendingExportSetlistId
        if (uri != null && setlistId != null) {
            importViewModel.exportSetlistToUri(context, setlistId, uri)
        }
        pendingExportSetlistId = null
        pendingExportSetlistName = null
    }

    // Set import — GetContent filtered to JSON files
    val importSetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        showImportSheet = false
        uri?.let { importViewModel.importSetFromJson(context, it) }
    }

    // "Importing…" Snackbar with Cancel — dismissed automatically when import finishes
    LaunchedEffect(isImporting) {
        if (isImporting) {
            val result = snackbarHostState.showSnackbar(
                message = "Importing…",
                actionLabel = "Cancel",
                duration = androidx.compose.material3.SnackbarDuration.Indefinite
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                importViewModel.cancelImport()
            }
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    // Sign-in error snackbar — Indefinite so the full error string is readable
    LaunchedEffect(Unit) {
        authViewModel.signInError.collect { errorMsg ->
            snackbarHostState.showSnackbar(
                message = errorMsg,
                duration = androidx.compose.material3.SnackbarDuration.Indefinite,
                withDismissAction = true
            )
        }
    }

    // Import result snackbar
    LaunchedEffect(importResult) {
        importResult?.let { result ->
            val msg = buildString {
                if (result.addedCount > 0) append("${result.addedCount} added")
                if (result.updatedCount > 0) {
                    if (result.addedCount > 0) append(", ")
                    append("${result.updatedCount} updated")
                }
                if (result.skippedCount > 0) {
                    if (result.addedCount > 0 || result.updatedCount > 0) append(", ")
                    append("${result.skippedCount} unchanged")
                }
            }.ifEmpty { "No changes" }
            snackbarHostState.showSnackbar(msg)
            importViewModel.clearImportResult()
        }
    }

    // ── Save Set dialog ───────────────────────────────────────────────────────
    if (showSaveSetDialog) {
        AlertDialog(
            onDismissRequest = { showSaveSetDialog = false; saveSetName = "" },
            title = { Text("Save Current Set") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = saveSetName,
                    onValueChange = { saveSetName = it },
                    label = { Text("Setlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = saveSetName.trim()
                        if (name.isNotEmpty()) {
                            setViewModel.saveCurrentSetAs(name)
                            showSaveSetDialog = false
                            saveSetName = ""
                        }
                    },
                    enabled = saveSetName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveSetDialog = false; saveSetName = "" }) { Text("Cancel") }
            }
        )
    }

    // ── Load Set dialog ───────────────────────────────────────────────────────
    if (showLoadSetDialog) {
        AlertDialog(
            onDismissRequest = { showLoadSetDialog = false },
            title = { Text("Load Setlist") },
            text = {
                if (setlists.isEmpty()) {
                    Text(
                        "No saved setlists yet. Save the current set first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        setlists.forEach { setlist ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        setViewModel.loadSetlistAsCurrent(setlist.id)
                                        showLoadSetDialog = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = setlist.name,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        pendingExportSetlistId = setlist.id
                                        pendingExportSetlistName = setlist.name
                                        showLoadSetDialog = false
                                        val filename = "${setlist.name.replace(" ", "_")}.encore.json"
                                        exportSetlistLauncher.launch(filename)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Export",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLoadSetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showImportSheet) {
        ImportModal(
            onDismiss = { showImportSheet = false },
            onSyncFolder = { folderPickerLauncher.launch(null) },
            onImportFiles = { filePickerLauncher.launch("*/*") },
            onImportSet = {
                showImportSheet = false
                setViewModel.refreshCloudSets()
                showCloudSetPicker = true
            },
            syncProgress = syncProgress,
            connectedFolderUri = connectedFolderUri
        )
    }

    // Cloud set picker — lists sets from GCS, user picks to load or save
    if (showCloudSetPicker) {
        ModalBottomSheet(onDismissRequest = { showCloudSetPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Load Show",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                if (cloudSetsLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                } else if (cloudSets.isEmpty()) {
                    Text(
                        text = "No shows found in cloud library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(cloudSets) { name ->
                            val isCurrent = name == currentShowName
                            androidx.compose.material3.ListItem(
                                headlineContent = { Text(name) },
                                supportingContent = if (isCurrent) {
                                    { Text("Currently loaded", color = Color(0xFF3B82F6)) }
                                } else null,
                                trailingContent = {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Default.Check else Icons.Default.FileOpen,
                                        contentDescription = if (isCurrent) "Currently loaded" else "Load",
                                        tint = if (isCurrent) Color(0xFF3B82F6) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.clickable {
                                    setViewModel.loadCloudShow(name)
                                    showCloudSetPicker = false
                                }
                            )
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showCloudSetPicker = false; showSaveToCloudDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Show to Cloud…")
                }
                TextButton(
                    onClick = { showCloudSetPicker = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel") }
            }
        }
    }

    // Save current set to cloud — prompts for a name
    if (showSaveToCloudDialog) {
        AlertDialog(
            onDismissRequest = { showSaveToCloudDialog = false },
            title = { Text("Save Show to Cloud") },
            text = {
                Column {
                    Text("Enter a name for this show (saves all sets):", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = cloudSetName,
                        onValueChange = { cloudSetName = it },
                        placeholder = { Text("e.g. Friday Night") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (cloudSetName.isNotBlank()) {
                            setViewModel.saveCloudShow(cloudSetName.trim())
                            showSaveToCloudDialog = false
                            cloudSetName = ""
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveToCloudDialog = false; cloudSetName = "" }) { Text("Cancel") }
            }
        )
    }

    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            sheetState = profileSheetState
        ) {
            ProfileSheetContent(
                authState = authState,
                onSignIn = {
                    authViewModel.signIn(context)
                    scope.launch { profileSheetState.hide() }.invokeOnCompletion {
                        showProfileSheet = false
                    }
                },
                onSignOut = {
                    authViewModel.signOut()
                    scope.launch { profileSheetState.hide() }.invokeOnCompletion {
                        showProfileSheet = false
                    }
                }
            )
        }
    }

    val encoreColors = LocalEncoreColors.current
    Scaffold(
        containerColor = encoreColors.screenBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(encoreColors.screenBackground)
                .padding(paddingValues)
        ) {
            // ── iOS-style large-title header ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(R.drawable.encore_logo_full)
                            .build(),
                        contentDescription = "Encore",
                        modifier = Modifier
                            .height(52.dp)
                            .width(52.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Library",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1.2).sp,
                            color = encoreColors.titleText,
                            lineHeight = 37.sp
                        )
                        Text(
                            text = "${songs.size} songs · ${availableSets.size} sets",
                            fontSize = 13.sp,
                            color = encoreColors.subtleText,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleDarkMode,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(
                            text = if (encoreColors.isDark) "☀" else "☾",
                            fontSize = 18.sp,
                            color = encoreColors.iconTint
                        )
                    }
                    IconButton(
                        onClick = { showImportSheet = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Import",
                            tint = encoreColors.iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = encoreColors.iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { showProfileSheet = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        UserAvatar(
                            profilePictureUri = (authState as? AuthState.Authenticated)?.user?.profilePictureUri,
                            isAuthenticated = authState is AuthState.Authenticated,
                            size = 28.dp
                        )
                    }
                }
            }

            // ── Tonight's Sets carousel ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tonight's Sets",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = encoreColors.titleText
                    )
                    // Loaded-show indicator + quick switch — tap to pick another show.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                setViewModel.refreshCloudSets()
                                showCloudSetPicker = true
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (currentShowName != null) Color(0xFF3B82F6)
                                   else encoreColors.titleText.copy(alpha = 0.4f)
                        )
                        Text(
                            text = currentShowName ?: "No show loaded — tap to choose",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (currentShowName != null) Color(0xFF3B82F6)
                                    else encoreColors.titleText.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch show",
                            modifier = Modifier.size(16.dp),
                            tint = if (currentShowName != null) Color(0xFF3B82F6)
                                   else encoreColors.titleText.copy(alpha = 0.4f)
                        )
                    }
                }
                TextButton(
                    onClick = { selectedSetFilter = null },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (selectedSetFilter == null) "All" else "Show all",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF3B82F6)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LibrarySetTile(
                    number = null,
                    label = "All Songs",
                    subLabel = "${songs.size} songs",
                    isActive = selectedSetFilter == null,
                    isDark = encoreColors.isDark,
                    titleTextColor = encoreColors.titleText,
                    onClick = { selectedSetFilter = null }
                )
                availableSets.forEach { set ->
                    LibrarySetTile(
                        number = set.number,
                        label = "Set ${set.number}",
                        subLabel = null,
                        isActive = selectedSetFilter == set.id,
                        isDark = encoreColors.isDark,
                        titleTextColor = encoreColors.titleText,
                        onClick = {
                            selectedSetFilter = if (selectedSetFilter == set.id) null else set.id
                        }
                    )
                }
            }

            val selectedSetNumber = availableSets.find { it.id == selectedSetFilter }?.number

            // ── Section header ────────────────────────────────────────────────
            Text(
                text = if (selectedSetFilter == null) "All Songs"
                       else "Set $selectedSetNumber",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = encoreColors.titleText,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp)
            )

            // Song list (search bar + rows) — fills available space
            LibraryListContent(
                viewModel = libraryViewModel,
                syncViewModel = syncViewModel,
                importViewModel = importViewModel,
                setViewModel = setViewModel,
                onSongClick = { songId -> onSongClick(songId, selectedSetNumber) },
                onEditChart = onEditChart,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Zen Import Modal — two-option bottom sheet for ingesting songs.
 *
 * Primary:   "Sync Folder" — opens DocumentTree picker; persists URI for future re-scans.
 * Secondary: "Import Files" — opens multi-file picker for individual .md files.
 *
 * When a sync is in progress, the buttons are replaced with a non-intrusive progress
 * indicator showing "Syncing N of M…" and a LinearProgressIndicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportModal(
    onDismiss: () -> Unit,
    onSyncFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onImportSet: (() -> Unit)? = null,
    syncProgress: SyncProgress?,
    connectedFolderUri: String? = null
) {
    // Derive a readable folder name from the tree URI (e.g. "primary:Encore" → "Encore")
    val folderName = connectedFolderUri?.let {
        try {
            android.net.Uri.parse(it).lastPathSegment
                ?.substringAfterLast(':')
                ?.takeIf { name -> name.isNotBlank() }
                ?: "Connected Folder"
        } catch (e: Exception) { "Connected Folder" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon in circle
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (connectedFolderUri != null) "Library Connected" else "Import Songs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (folderName != null) "Folder: $folderName"
                       else "Sync an entire folder or pick individual files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Progress or action buttons
            AnimatedVisibility(
                visible = syncProgress != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (syncProgress != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = syncProgress.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { syncProgress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = syncProgress == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Primary: Sync Folder / Update Library
                    androidx.compose.material3.Button(
                        onClick = onSyncFolder,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (connectedFolderUri != null) "Update Library" else "Sync Folder",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Secondary: Import Files
                    OutlinedButton(
                        onClick = onImportFiles,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Import Files",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    if (onImportSet != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onImportSet,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Import Set (.json)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sets management footer — dynamic chips driven by sets that exist in the DB.
 *
 * Active state uses the set's persistent color. Selecting an active set deselects
 * it (toggle). "New Set" chip appended at the end creates the next numbered set.
 */
@OptIn(ExperimentalFoundationApi::class)
/**
 * Sticky set-filter tab bar shown just below the header.
 *
 * Tapping a chip filters the library to that set; tapping the active chip
 * clears the filter. Long-pressing a non-Set-1 chip deletes it.
 * "+ New Set" creates an additional set.
 */
@Composable
fun SetsSection(
    sets: List<SetEntity>,
    selectedSet: Int?,
    onSetSelected: (Int) -> Unit,
    onCreateSet: () -> Unit,
    onDeleteSet: (SetEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var setToDelete by remember { mutableStateOf<SetEntity?>(null) }

    if (setToDelete != null) {
        AlertDialog(
            onDismissRequest = { setToDelete = null },
            title = { Text("Delete Set ${setToDelete!!.number}?") },
            text = { Text("This will remove the set and all its song assignments. Songs will not be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSet(setToDelete!!)
                        setToDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { setToDelete = null }) { Text("Cancel") }
            }
        )
    }

    val encoreColors = LocalEncoreColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sets.forEach { set ->
                val setColor = SetColor.getSetColor(set.number)
                val isSelected = selectedSet == set.number
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) setColor else Color.Transparent,
                    border = BorderStroke(1.dp, setColor),
                    modifier = Modifier
                        .clip(CircleShape)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.material.ripple.rememberRipple(),
                            onClick = { onSetSelected(set.number) },
                            onLongClick = { if (set.number > 1) setToDelete = set }
                        )
                ) {
                    Text(
                        text = "Set ${set.number}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) Color.White else setColor,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }
            FilterChip(
                selected = false,
                onClick = onCreateSet,
                label = {
                    Text(
                        text = "+ New Set",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = encoreColors.subtleText,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = encoreColors.subtleText
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = false,
                    borderColor = encoreColors.divider,
                    selectedBorderColor = encoreColors.divider
                )
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = encoreColors.divider)
    }
}

/**
 * Apple Music-style set tile for the "Tonight's Sets" carousel.
 *
 * @param number Set number (null = "All Songs" infinity tile)
 * @param label  Title shown below the tile (set name or "All Songs")
 * @param subLabel Optional subtitle shown below label (e.g. "N songs")
 * @param isActive Whether this tile has a 3px selection ring
 * @param isDark Current color mode (affects the "All Songs" neutral palette)
 * @param titleTextColor Active ring color
 * @param onClick Selection callback
 */
@Composable
fun LibrarySetTile(
    number: Int?,
    label: String,
    subLabel: String?,
    isActive: Boolean,
    isDark: Boolean,
    titleTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val encoreColors = LocalEncoreColors.current
    val coverBg = if (number != null) setCoverColors(number).bg
                  else if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)
    val coverFg = if (number != null) setCoverColors(number).fg
                  else if (isDark) Color.White else Color(0xFF1C1C1E)

    Column(
        modifier = modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .background(coverBg)
                .then(if (isActive) Modifier.border(
                    3.dp,
                    titleTextColor,
                    androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                ) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            // Corner "Set" / "All" label
            Text(
                text = if (number != null) "Set" else "All",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = coverFg.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 10.dp)
            )
            // Large centered number or library icon for "All Songs"
            if (number != null) {
                Text(
                    text = number.toString(),
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-4.5).sp,
                    color = coverFg,
                    lineHeight = 76.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = "All Songs",
                    tint = coverFg,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = encoreColors.titleText,
            letterSpacing = (-0.14).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        if (subLabel != null) {
            Text(
                text = subLabel,
                fontSize = 12.sp,
                color = encoreColors.subtleText,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Google avatar image clipped to a circle, with AccountCircle fallback.
 */
@Composable
fun UserAvatar(
    profilePictureUri: android.net.Uri?,
    isAuthenticated: Boolean,
    size: Dp,
    modifier: Modifier = Modifier
) {
    if (profilePictureUri != null) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(profilePictureUri)
                .crossfade(200)
                .build(),
            contentDescription = "Profile picture",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Account",
            tint = if (isAuthenticated) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(size)
        )
    }
}

/**
 * Profile bottom sheet — shows sign-in prompt when unauthenticated,
 * account details + sign-out when authenticated.
 */
@Composable
fun ProfileSheetContent(
    authState: AuthState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (authState) {
            is AuthState.Unauthenticated, AuthState.Loading -> {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Sign in to Sync",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your songs are saved offline. Sign in to back them up and sync across devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onSignIn,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign in with Google")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Offline Mode  •  Sign in to Sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            is AuthState.Authenticated -> {
                UserAvatar(
                    profilePictureUri = authState.user.profilePictureUri,
                    isAuthenticated = true,
                    size = 64.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                val displayName = authState.user.displayName
                if (displayName != null) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = authState.user.googleAccountId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline
                    )
                ) {
                    Text("Sign Out")
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
