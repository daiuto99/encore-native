package com.encore.tablet.ui

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
import com.encore.tablet.audit.LibraryAuditViewModel
import com.encore.tablet.preferences.AppPreferencesViewModel
import com.encore.feature.library.LibraryListContent
import com.encore.feature.library.LibraryViewModel
import com.encore.feature.library.SongChartEditorScreen
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
    val authViewModel: AuthViewModel = viewModel(factory = viewModelFactory)
    val appPrefsViewModel: AppPreferencesViewModel = viewModel(factory = viewModelFactory)
    val auditViewModel: LibraryAuditViewModel = viewModel(factory = viewModelFactory)
    val appPreferences by appPrefsViewModel.preferences.collectAsState()
    var isDarkMode by remember { mutableStateOf(false) }
    var editSong by remember { mutableStateOf<SongEntity?>(null) }
    val encoreColors = if (isDarkMode) DarkEncoreColors else LightEncoreColors

    CompositionLocalProvider(LocalEncoreColors provides encoreColors) {
    editSong?.let { song ->
        SongEditBottomSheet(
            song = song,
            onSave = { title, artist, isLeadGuitar, harmonyMode, resetZoom, clearHarmonies ->
                libraryViewModel.updateSongMetadata(song.id, title, artist, isLeadGuitar, harmonyMode, resetZoom, clearHarmonies)
                editSong = null
            },
            onDismiss = { editSong = null },
            onEditChart = {
                editSong = null
                navController.navigate(Routes.chartEditor(song.id))
            }
        )
    }
    NavHost(
        navController = navController,
        startDestination = "command_center",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("command_center") {
            CommandCenterScreen(
                libraryViewModel = libraryViewModel,
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
                onNavigateBack = { navController.popBackStack() }
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
                }
            )
        }
    }
    } // end CompositionLocalProvider
}


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
    authViewModel: AuthViewModel,
    onToggleDarkMode: () -> Unit,
    onSongClick: (songId: String, setNumber: Int?) -> Unit,
    onEditChart: ((songId: String) -> Unit)? = null,
    onSettingsClick: () -> Unit = {}
) {
    var selectedSetFilter by remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val importResult by libraryViewModel.importResult.collectAsState()
    val isImporting by libraryViewModel.isImporting.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    var showProfileSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var showSaveSetDialog by remember { mutableStateOf(false) }
    var showLoadSetDialog by remember { mutableStateOf(false) }
    var saveSetName by remember { mutableStateOf("") }
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAccountDropdown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Sync set filter state into ViewModel
    LaunchedEffect(selectedSetFilter) {
        libraryViewModel.updateSetFilter(selectedSetFilter)
    }

    val syncProgress by libraryViewModel.syncProgress.collectAsState()
    val connectedFolderUri by libraryViewModel.connectedFolderUri.collectAsState()
    val availableSets by libraryViewModel.availableSets.collectAsState()
    val songs by libraryViewModel.songs.collectAsState()
    val performSetEntries by libraryViewModel.performSetEntries.collectAsState()
    val setlists by libraryViewModel.setlists.collectAsState()

    // Folder Sync — OpenDocumentTree gives a persistent tree URI
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        showImportSheet = false
        uri?.let { libraryViewModel.syncFolder(context, it) }
    }

    // Individual file import — GetMultipleContents uses ACTION_GET_CONTENT
    // Native back-stack handles cancel: Back = up a level, Back again = return to app
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        showImportSheet = false
        if (uris.isNotEmpty()) libraryViewModel.importSongs(context, uris)
    }

    // Set export — CreateDocument lets the user choose where to save the .encore.json file
    var pendingExportSetlistId by remember { mutableStateOf<String?>(null) }
    var pendingExportSetlistName by remember { mutableStateOf<String?>(null) }
    val exportSetlistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val setlistId = pendingExportSetlistId
        if (uri != null && setlistId != null) {
            libraryViewModel.exportSetlistToUri(context, setlistId, uri)
        }
        pendingExportSetlistId = null
        pendingExportSetlistName = null
    }

    // Set import — GetContent filtered to JSON files
    val importSetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        showImportSheet = false
        uri?.let { libraryViewModel.importSetFromJson(context, it) }
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
                libraryViewModel.cancelImport()
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
            libraryViewModel.clearImportResult()
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
                            libraryViewModel.saveCurrentSetAs(name)
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
                                        libraryViewModel.loadSetlistAsCurrent(setlist.id)
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
            onImportSet = { importSetLauncher.launch("application/json") },
            syncProgress = syncProgress,
            connectedFolderUri = connectedFolderUri
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
            // ── Header ───────────────────────────────────────────────────────
            EncoreHeader(
                authState = authState,
                showAccountDropdown = showAccountDropdown,
                connectedFolderUri = connectedFolderUri,
                onImportClick = { showImportSheet = true },
                onSaveSetClick = { showSaveSetDialog = true },
                onLoadSetClick = { showLoadSetDialog = true },
                onToggleDarkMode = onToggleDarkMode,
                onPerformClick = {
                    val setNum = selectedSetFilter ?: 1
                    val firstSongId = if (selectedSetFilter != null) {
                        songs.firstOrNull()?.id
                    } else {
                        performSetEntries.firstOrNull()?.song?.id
                    }
                    if (firstSongId != null) {
                        onSongClick(firstSongId, setNum)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("No songs in set")
                        }
                    }
                },
                onRefreshClick = { libraryViewModel.refreshConnectedFolder(context) },
                onShowDropdown = { showAccountDropdown = true },
                onDropdownDismiss = { showAccountDropdown = false },
                onSignOut = { authViewModel.signOut(); showAccountDropdown = false },
                onProfileSheetRequest = { showProfileSheet = true },
                onSettingsClick = onSettingsClick
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = encoreColors.divider
            )

            // Song list (search bar + rows) — fills available space
            LibraryListContent(
                viewModel = libraryViewModel,
                onSongClick = { songId -> onSongClick(songId, selectedSetFilter) },
                onEditChart = onEditChart,
                modifier = Modifier.weight(1f)
            )

            // ── Sets Section ─────────────────────────────────────────────────
            SetsSection(
                sets = availableSets,
                selectedSet = selectedSetFilter,
                onSetSelected = { setNumber ->
                    selectedSetFilter = if (selectedSetFilter == setNumber) null else setNumber
                },
                onClearFilter = { selectedSetFilter = null },
                onCreateSet = { libraryViewModel.createNewSet() },
                onDeleteSet = { set -> libraryViewModel.deleteSet(set) }
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
@Composable
fun SetsSection(
    sets: List<SetEntity>,
    selectedSet: Int?,
    onSetSelected: (Int) -> Unit,
    onClearFilter: () -> Unit,
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
    HorizontalDivider(
        thickness = 0.5.dp,
        color = encoreColors.divider
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = encoreColors.titleText,
                modifier = Modifier.weight(1f)
            )
            if (selectedSet != null) {
                TextButton(
                    onClick = onClearFilter,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Show All",
                        style = MaterialTheme.typography.labelMedium,
                        color = encoreColors.iconTint
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) Color.White else setColor,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
            FilterChip(
                selected = false,
                onClick = onCreateSet,
                label = {
                    Text(
                        text = "+ New Set",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = encoreColors.titleText,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = encoreColors.cardBackground,
                    labelColor = encoreColors.titleText
                )
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
