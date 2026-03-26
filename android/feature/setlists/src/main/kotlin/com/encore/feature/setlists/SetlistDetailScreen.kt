package com.encore.feature.setlists

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.encore.core.data.relations.SetEntryWithSong
import com.encore.core.data.relations.SetWithEntries

/**
 * Setlist Detail Screen.
 *
 * Shows songs in a setlist organized by sets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(
    viewModel: SetlistViewModel,
    setlistId: String,
    onNavigateBack: () -> Unit
) {
    val setlistWithSongs by viewModel.getSetlistWithSongs(setlistId).collectAsState()

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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

/**
 * Setlist content with sets and songs.
 */
@Composable
fun SetlistContent(
    sets: List<SetWithEntries>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(sets) { setWithEntries ->
            SetSection(
                setNumber = setWithEntries.set.number,
                songs = setWithEntries.entries
            )
        }
    }
}

/**
 * Individual set section with songs.
 */
@Composable
fun SetSection(
    setNumber: Int,
    songs: List<SetEntryWithSong>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Set header
        Text(
            text = "Set $setNumber",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Songs in set
        songs.sortedBy { it.entry.position }.forEach { entryWithSong ->
            SetlistSongCard(
                position = entryWithSong.entry.position + 1, // Display as 1-indexed
                title = entryWithSong.song.title,
                artist = entryWithSong.song.artist,
                key = entryWithSong.song.currentKey
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Song card in setlist.
 */
@Composable
fun SetlistSongCard(
    position: Int,
    title: String,
    artist: String,
    key: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                modifier = Modifier.width(32.dp)
            )

            // Song info
            Column(
                modifier = Modifier.weight(1f)
            ) {
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
                text = "Add songs from your library using the + icon",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
