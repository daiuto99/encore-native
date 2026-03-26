package com.encore.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SongEntity
import com.encore.core.ui.theme.SetColor

/**
 * Library Screen - Main song library view.
 *
 * Features:
 * - Search bar with live filtering
 * - List of all songs (title, artist, key)
 * - Set membership badges
 * - FAB for importing songs
 * - Tap song to view/edit (navigation wired in app module)
 *
 * Milestone 2: Library Management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSongClick: (String) -> Unit = {},
    onAddToSetlist: (String) -> Unit = {}
) {
    val songs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // File picker launcher for importing markdown files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importSongs(context, uris)
        }
    }

    // Show snackbar when import completes
    LaunchedEffect(importResult) {
        importResult?.let { result ->
            val message = buildString {
                if (result.addedCount > 0) {
                    append("${result.addedCount} song${if (result.addedCount != 1) "s" else ""} imported")
                }
                if (result.skippedCount > 0) {
                    if (result.addedCount > 0) append(", ")
                    append("${result.skippedCount} duplicate${if (result.skippedCount != 1) "s" else ""} skipped")
                }
                if (result.addedCount == 0 && result.skippedCount == 0) {
                    append("No files imported")
                }
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearImportResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Launch file picker for .md files (supports multi-select)
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import Song"
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Progress indicator during import
            if (isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onClearClick = { viewModel.clearSearch() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            // Song List
            if (songs.isEmpty()) {
                EmptyLibraryMessage(hasSearchQuery = searchQuery.isNotEmpty())
            } else {
                SongList(
                    songs = songs,
                    viewModel = viewModel,
                    onSongClick = onSongClick,
                    onAddToSetlist = onAddToSetlist,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Search bar with clear button.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = {
            Text("Search by title or artist...")
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearClick) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large
    )
}

/**
 * Scrollable list of songs.
 */
@Composable
fun SongList(
    songs: List<SongEntity>,
    viewModel: LibraryViewModel,
    onSongClick: (String) -> Unit,
    onAddToSetlist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(
            items = songs,
            key = { song -> song.id }
        ) { song ->
            SongListItem(
                song = song,
                viewModel = viewModel,
                onClick = { onSongClick(song.id) },
                onAddToSetlist = { onAddToSetlist(song.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Individual song list item card with set membership badges.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongListItem(
    song: SongEntity,
    viewModel: LibraryViewModel,
    onClick: () -> Unit,
    onAddToSetlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sets by remember { mutableStateOf<List<SetEntity>>(emptyList()) }

    // Fetch sets containing this song
    LaunchedEffect(song.id) {
        sets = viewModel.getSetsContainingSong(song.id)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Song info (title, artist)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Key badge
                song.currentKey?.let { key ->
                    Spacer(modifier = Modifier.width(8.dp))
                    KeyBadge(key = key)
                }

                // Add to setlist button
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onAddToSetlist
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Add to Setlist",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Set membership badges
            if (sets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    sets.forEach { set ->
                        SetMembershipBadge(
                            setNumber = set.number,
                            modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Set membership badge showing which set(s) a song belongs to.
 */
@Composable
fun SetMembershipBadge(
    setNumber: Int,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val badgeColor = SetColor.getSetBadgeColor(setNumber, colorScheme)
    val textColor = SetColor.getSetBadgeTextColor(setNumber, colorScheme)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = badgeColor
    ) {
        Text(
            text = "Set $setNumber",
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Key badge showing the current key of the song.
 */
@Composable
fun KeyBadge(
    key: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Empty state message.
 */
@Composable
fun EmptyLibraryMessage(
    hasSearchQuery: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (hasSearchQuery) {
                    "No songs found"
                } else {
                    "Your library is empty"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (hasSearchQuery) {
                    "Try a different search term"
                } else {
                    "Tap the + button to import your first song"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ========== Previews ==========

@Preview(
    showBackground = true,
    widthDp = 800,
    heightDp = 1280,
    name = "11-inch Tablet Portrait - With Songs"
)
@Composable
fun LibraryScreenPreview() {
    MaterialTheme {
        // Note: Preview uses fake data since we can't instantiate ViewModel here
        LibraryScreenContent(
            songs = listOf(
                SongEntity(
                    id = "1",
                    userId = "local-user",
                    title = "Amazing Grace",
                    artist = "John Newton",
                    currentKey = "G",
                    markdownBody = "",
                    originalImportBody = null,
                    version = 1,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    localUpdatedAt = System.currentTimeMillis()
                ),
                SongEntity(
                    id = "2",
                    userId = "local-user",
                    title = "How Great Thou Art",
                    artist = "Carl Boberg",
                    currentKey = "C",
                    markdownBody = "",
                    originalImportBody = null,
                    version = 1,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    localUpdatedAt = System.currentTimeMillis()
                ),
                SongEntity(
                    id = "3",
                    userId = "local-user",
                    title = "Cornerstone",
                    artist = "Hillsong Worship",
                    currentKey = "D",
                    markdownBody = "",
                    originalImportBody = null,
                    version = 1,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    localUpdatedAt = System.currentTimeMillis()
                )
            ),
            searchQuery = "",
            onSearchQueryChange = {},
            onClearSearch = {},
            onSongClick = {},
            onAddToSetlist = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 800,
    heightDp = 1280,
    name = "11-inch Tablet Portrait - Empty"
)
@Composable
fun LibraryScreenEmptyPreview() {
    MaterialTheme {
        LibraryScreenContent(
            songs = emptyList(),
            searchQuery = "",
            onSearchQueryChange = {},
            onClearSearch = {},
            onSongClick = {},
            onAddToSetlist = {}
        )
    }
}

/**
 * Content-only composable for previews (no ViewModel dependency).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreenContent(
    songs: List<SongEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSongClick: (String) -> Unit,
    onAddToSetlist: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClearClick = onClearSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            if (songs.isEmpty()) {
                EmptyLibraryMessage(hasSearchQuery = searchQuery.isNotEmpty())
            } else {
                // Note: Can't show badges in preview without ViewModel
                Text(
                    text = "Preview: ${songs.size} songs",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
