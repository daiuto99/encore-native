package com.encore.tablet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.encore.core.ui.theme.SetColor
import com.encore.feature.library.LibraryListContent
import com.encore.feature.library.LibraryViewModel
import com.encore.feature.performance.SongDetailScreen
import com.encore.feature.performance.SongDetailViewModel
import com.encore.tablet.di.AppContainer
import com.encore.tablet.di.ViewModelFactory
import com.encore.tablet.navigation.Routes

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

    NavHost(
        navController = navController,
        startDestination = "command_center",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("command_center") {
            CommandCenterScreen(
                libraryViewModel = libraryViewModel,
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
 * 1. Quick action cards: Song Folder Library | Add Songs
 * 2. "Song Library" section label
 * 3. Song list with search (LibraryListContent — no nested Scaffold)
 * 4. Sets section with global color-coded filter chips
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterScreen(
    libraryViewModel: LibraryViewModel,
    onSongClick: (String) -> Unit
) {
    var selectedSetFilter by remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val importResult by libraryViewModel.importResult.collectAsState()

    // Sync set filter state into ViewModel
    LaunchedEffect(selectedSetFilter) {
        libraryViewModel.updateSetFilter(selectedSetFilter)
    }

    // File picker for "Add Songs" card
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) libraryViewModel.importSongs(context, uris)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Quick Action Cards ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    title = "Song Folder Library",
                    subtitle = "Browse and manage your song files",
                    icon = Icons.Default.Folder,
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    title = "Add Songs",
                    subtitle = "Import markdown song files",
                    icon = Icons.Default.Add,
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Song Library Section ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Song Library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = {
                        libraryViewModel.clearSearch()
                        libraryViewModel.updateSetFilter(null)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Show All",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

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
 * Quick action card for the Command Center top row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(90.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}
