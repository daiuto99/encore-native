package com.encore.feature.setlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.relations.SetEntryWithSong
import com.encore.core.data.relations.SetWithEntries
import com.encore.core.ui.theme.SetColor

/**
 * Setlist Detail Screen.
 *
 * Shows songs in a setlist organized by sets with color coding.
 * Includes FAB for adding songs to sets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(
    viewModel: SetlistViewModel,
    songRepository: com.encore.core.data.repository.SongRepository,
    setlistId: String,
    onNavigateBack: () -> Unit,
    onSongClick: (String) -> Unit = {}
) {
    val setlistWithSongs by viewModel.getSetlistWithSongs(setlistId).collectAsState()
    var showSongSelectionDialog by remember { mutableStateOf(false) }
    var selectedSetId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = setlistWithSongs?.setlist?.name ?: "Setlist",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            // Show FAB only if there are sets
            if (setlistWithSongs?.sets?.isNotEmpty() == true) {
                FloatingActionButton(
                    onClick = {
                        // Default to adding to first set
                        selectedSetId = setlistWithSongs?.sets?.first()?.set?.id
                        showSongSelectionDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Song"
                    )
                }
            }
        }
    ) { paddingValues ->
        setlistWithSongs?.let { data ->
            if (data.sets.isEmpty() || data.sets.all { it.entries.isEmpty() }) {
                EmptySetlistDetailMessage(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            } else {
                SetlistContent(
                    sets = data.sets,
                    onAddToSet = { setId ->
                        selectedSetId = setId
                        showSongSelectionDialog = true
                    },
                    onSongClick = onSongClick,
                    onReorderSong = { entryId, newPos -> viewModel.reorderSongInSet(entryId, newPos) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }

    // Song Selection Dialog
    if (showSongSelectionDialog && selectedSetId != null) {
        SongSelectionDialog(
            songRepository = songRepository,
            onDismiss = { showSongSelectionDialog = false },
            onSongSelected = { songId ->
                viewModel.addSongToSpecificSet(selectedSetId!!, songId)
                showSongSelectionDialog = false
            }
        )
    }
}

/**
 * Setlist content with sets and songs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetlistContent(
    sets: List<SetWithEntries>,
    onAddToSet: (String) -> Unit,
    onSongClick: (String) -> Unit,
    onReorderSong: (entryId: String, newPosition: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(sets, key = { it.set.id }) { setWithEntries ->
            SetSection(
                setNumber = setWithEntries.set.number,
                setId = setWithEntries.set.id,
                songs = setWithEntries.entries,
                onAddToSet = onAddToSet,
                onSongClick = onSongClick,
                onReorderSong = onReorderSong,
                modifier = Modifier.animateItemPlacement()
            )
        }
    }
}

/**
 * Individual set section with songs, color coding, and drag-to-reorder.
 */
@Composable
fun SetSection(
    setNumber: Int,
    setId: String,
    songs: List<SetEntryWithSong>,
    onAddToSet: (String) -> Unit,
    onSongClick: (String) -> Unit,
    onReorderSong: (entryId: String, newPosition: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = SetColor.getSetContainerColor(setNumber, colorScheme)
    val contentColor = SetColor.getSetContentColor(setNumber, colorScheme)


    val sortedSongs = songs.sortedBy { it.entry.position }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Set $setNumber",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                IconButton(onClick = { onAddToSet(setId) }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add song to Set $setNumber",
                        tint = contentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            sortedSongs.forEachIndexed { index, entryWithSong ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetlistSongCard(
                        position = index + 1,
                        songId = entryWithSong.song.id,
                        title = entryWithSong.song.title,
                        artist = entryWithSong.song.artist,
                        key = entryWithSong.song.displayKey,
                        onClick = { onSongClick(entryWithSong.song.id) },
                        modifier = Modifier.weight(1f)
                    )
                    Column {
                        IconButton(
                            onClick = { if (index > 0) onReorderSong(entryWithSong.entry.id, index - 1) },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", tint = contentColor)
                        }
                        IconButton(
                            onClick = { if (index < sortedSongs.lastIndex) onReorderSong(entryWithSong.entry.id, index + 1) },
                            enabled = index < sortedSongs.lastIndex
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", tint = contentColor)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Song card in setlist. Long-press anywhere on the card to drag-to-reorder.
 * [isDragging] triggers a red border + scale lift to signal active drag.
 */
@Composable
fun SetlistSongCard(
    position: Int,
    songId: String,
    title: String,
    artist: String,
    key: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position number
            Text(
                text = "$position.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )

            // Song info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Key badge
            key?.let {
                Spacer(modifier = Modifier.width(8.dp))
                KeyBadgeSmall(key = it)
            }
        }
    }
}

/**
 * Small key badge for setlist view.
 */
@Composable
fun KeyBadgeSmall(
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Empty setlist detail message.
 */
@Composable
fun EmptySetlistDetailMessage(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "No songs yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tap the + button to add songs from your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * Song Selection Dialog with search.
 *
 * Shows all songs in library with live search filtering.
 */
@Composable
fun SongSelectionDialog(
    songRepository: com.encore.core.data.repository.SongRepository,
    onDismiss: () -> Unit,
    onSongSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allSongs by songRepository.searchSongs(searchQuery).collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Song") },
        text = {
            Column {
                // Search bar
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search songs...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = MaterialTheme.shapes.medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Song list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(
                        items = allSongs,
                        key = { song -> song.id }
                    ) { song ->
                        SongSelectionItem(
                            song = song,
                            onClick = { onSongSelected(song.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Individual song item in selection dialog.
 */
@Composable
fun SongSelectionItem(
    song: SongEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        song.displayKey?.let { key ->
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
