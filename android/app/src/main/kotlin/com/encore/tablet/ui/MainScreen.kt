package com.encore.tablet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
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
import com.encore.core.ui.theme.SetColor
import com.encore.feature.library.LibraryListContent
import com.encore.feature.library.LibraryViewModel
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

    NavHost(
        navController = navController,
        startDestination = "command_center",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("command_center") {
            CommandCenterScreen(
                libraryViewModel = libraryViewModel,
                authViewModel = authViewModel,
                onSongClick = { songId ->
                    navController.navigate(Routes.songDetail(songId))
                }
            )
        }

        // Song detail is full-screen — NavHost replaces entire content, no Scaffold around it
        composable(
            route = Routes.SONG_DETAIL,
            arguments = listOf(navArgument("songId") { type = NavType.StringType })
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
            val viewModel: SongDetailViewModel = viewModel(factory = viewModelFactory)
            SongDetailScreen(
                viewModel = viewModel,
                songId = songId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
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
    onSongClick: (String) -> Unit
) {
    var selectedSetFilter by remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val importResult by libraryViewModel.importResult.collectAsState()
    val isImporting by libraryViewModel.isImporting.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    var showProfileSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAccountDropdown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Sync set filter state into ViewModel
    LaunchedEffect(selectedSetFilter) {
        libraryViewModel.updateSetFilter(selectedSetFilter)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        showImportSheet = false
        if (uris.isNotEmpty()) libraryViewModel.importSongs(context, uris)
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
                if (result.addedCount > 0) append("${result.addedCount} imported")
                if (result.skippedCount > 0) {
                    if (result.addedCount > 0) append(", ")
                    append("${result.skippedCount} skipped")
                }
            }.ifEmpty { "No files imported" }
            snackbarHostState.showSnackbar(msg)
            libraryViewModel.clearImportResult()
        }
    }

    if (showImportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Import Songs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select one or more markdown (.md) files from your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = {
                        filePickerLauncher.launch("*/*")
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Choose Files")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showImportSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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

    Scaffold(
        containerColor = Color(0xFF121212),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(paddingValues)
        ) {
            // ── Header ───────────────────────────────────────────────────────
            EncoreHeader(
                authState = authState,
                showAccountDropdown = showAccountDropdown,
                onImportClick = { showImportSheet = true },
                onShowDropdown = { showAccountDropdown = true },
                onDropdownDismiss = { showAccountDropdown = false },
                onSignOut = { authViewModel.signOut(); showAccountDropdown = false },
                onProfileSheetRequest = { showProfileSheet = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Song list (search bar + rows) — fills available space
            LibraryListContent(
                viewModel = libraryViewModel,
                onSongClick = onSongClick,
                modifier = Modifier.weight(1f)
            )

            // ── Sets Section ─────────────────────────────────────────────────
            SetsSection(
                selectedSet = selectedSetFilter,
                onSetSelected = { setNumber ->
                    selectedSetFilter = if (selectedSetFilter == setNumber) null else setNumber
                },
                onClearFilter = { selectedSetFilter = null }
            )
        }
    }
}

/**
 * Sets management footer with global color-coded filter chips (Sets 1–4).
 *
 * Active state uses the set's persistent color (blue/orange/green/purple).
 * Selecting an active set deselects it (toggle behavior).
 */
@Composable
fun SetsSection(
    selectedSet: Int?,
    onSetSelected: (Int) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
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
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selectedSet != null) {
                TextButton(
                    onClick = onClearFilter,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Show All",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (setNumber in 1..4) {
                val setColor = SetColor.getSetColor(setNumber)
                FilterChip(
                    selected = selectedSet == setNumber,
                    onClick = { onSetSelected(setNumber) },
                    label = { Text("Set $setNumber") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = setColor,
                        selectedLabelColor = Color.White,
                        labelColor = setColor
                    )
                )
            }
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
