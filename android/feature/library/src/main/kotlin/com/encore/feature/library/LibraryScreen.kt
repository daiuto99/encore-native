package com.encore.feature.library

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.encore.core.data.entities.SongEntity

/**
 * Library Screen - Main song library view.
 *
 * Features:
 * - Search bar with live filtering
 * - List of all songs (title, artist, key)
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
    onImportClick: () -> Unit = {}
) {
    val songs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

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
                onClick = onImportClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import Song"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                    onSongClick = onSongClick,
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
    onSongClick: (String) -> Unit,
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
                onClick = { onSongClick(song.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Individual song list item card.
 */
@Composable
fun SongListItem(
    song: SongEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                Spacer(modifier = Modifier.width(16.dp))
                KeyBadge(key = key)
            }
        }
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
            onImportClick = {}
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
            onImportClick = {}
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
    onImportClick: () -> Unit
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onImportClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import Song"
                )
            }
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
                SongList(
                    songs = songs,
                    onSongClick = onSongClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
