package com.encore.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SongEntity
import com.encore.core.ui.theme.SetColor
import kotlin.math.roundToInt

/**
 * Library Screen - Standalone full-screen version (used by Navigation graph).
 *
 * For embedding in Command Center, use [LibraryListContent] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSongClick: (String) -> Unit = {},
    onAddToSetlist: (String) -> Unit = {},
    setFilter: Int? = null
) {
    val songs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val activeSetFilter by viewModel.setFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(setFilter) { viewModel.updateSetFilter(setFilter) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importSongs(context, uris)
    }

    LaunchedEffect(importResult) {
        importResult?.let { result ->
            val message = buildString {
                if (result.addedCount > 0) append("${result.addedCount} song(s) imported")
                if (result.skippedCount > 0) {
                    if (result.addedCount > 0) append(", ")
                    append("${result.skippedCount} duplicate(s) skipped")
                }
                if (result.addedCount == 0 && result.skippedCount == 0) append("No files imported")
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearImportResult()
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Song Library", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { viewModel.toggleSort() }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = if (sortOrder == SortOrder.ARTIST) "Sort by Title" else "Sort by Artist",
                            tint = if (sortOrder == SortOrder.ARTIST) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Import Song")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isImporting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onClearClick = { viewModel.clearSearch() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (songs.isEmpty()) {
                EmptyLibraryMessage(hasSearchQuery = searchQuery.isNotEmpty())
            } else {
                SongList(
                    songs = songs,
                    viewModel = viewModel,
                    activeSetFilter = activeSetFilter,
                    onSongClick = onSongClick,
                    onDeleteSong = { song -> viewModel.deleteSong(song) },
                    onRemoveFromSet = { songId, setNum -> viewModel.removeSongFromSetNumber(songId, setNum) },
                    onAddToSet = { songId -> /* handled inside item with dialog */ },
                    onReorder = { songId, toIdx -> viewModel.reorderSong(songId, toIdx) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Embeddable library content — no Scaffold, no TopAppBar.
 * Used by Command Center to avoid nested Scaffold issues.
 */
@Composable
fun LibraryListContent(
    viewModel: LibraryViewModel,
    onSongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val activeSetFilter by viewModel.setFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
            viewModel.clearImportResult()
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onClearClick = { viewModel.clearSearch() },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.toggleSort() }) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = if (sortOrder == SortOrder.ARTIST) "Sort by Title" else "Sort by Artist",
                        tint = if (sortOrder == SortOrder.ARTIST) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isImporting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (songs.isEmpty()) {
                EmptyLibraryMessage(
                    hasSearchQuery = searchQuery.isNotEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SongList(
                    songs = songs,
                    viewModel = viewModel,
                    activeSetFilter = activeSetFilter,
                    onSongClick = onSongClick,
                    onDeleteSong = { song -> viewModel.deleteSong(song) },
                    onRemoveFromSet = { songId, setNum -> viewModel.removeSongFromSetNumber(songId, setNum) },
                    onAddToSet = { /* handled inside item */ },
                    onReorder = { songId, toIdx -> viewModel.reorderSong(songId, toIdx) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Scrollable song list with swipe-to-dismiss and optional drag-to-reorder.
 *
 * - Left swipe: shows confirmation dialog (remove from set or delete)
 * - Drag handle (visible when set filter active): long-press to reorder
 */
@Composable
fun SongList(
    songs: List<SongEntity>,
    viewModel: LibraryViewModel,
    activeSetFilter: Int?,
    onSongClick: (String) -> Unit,
    onDeleteSong: (SongEntity) -> Unit,
    onRemoveFromSet: (String, Int) -> Unit,
    onAddToSet: (String) -> Unit,
    onReorder: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemHeightPx = remember(density) { with(density) { 72.dp.toPx() } }

    // Drag-and-drop state
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            val index = songs.indexOf(song)
            val isDragging = draggingIndex == index

            val dragHandleModifier = if (activeSetFilter != null) {
                Modifier.pointerInput(song.id, songs.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingIndex = index; dragOffsetY = 0f },
                        onDrag = { change, drag ->
                            change.consume()
                            dragOffsetY += drag.y
                        },
                        onDragEnd = {
                            val offsetInItems = (dragOffsetY / itemHeightPx).roundToInt()
                            val targetIdx = (index + offsetInItems).coerceIn(0, songs.size - 1)
                            if (targetIdx != index) onReorder(song.id, targetIdx)
                            draggingIndex = null
                            dragOffsetY = 0f
                        },
                        onDragCancel = { draggingIndex = null; dragOffsetY = 0f }
                    )
                }
            } else Modifier

            SongListItem(
                song = song,
                viewModel = viewModel,
                activeSetFilter = activeSetFilter,
                onClick = { onSongClick(song.id) },
                onDeleteSong = { onDeleteSong(song) },
                onRemoveFromSet = onRemoveFromSet,
                onAddToSet = { onAddToSet(song.id) },
                isDragging = isDragging,
                dragOffsetY = if (isDragging) dragOffsetY else 0f,
                dragHandleModifier = dragHandleModifier,
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
            )
        }
    }
}

/**
 * Single song row with swipe-to-dismiss gesture.
 *
 * Layout: [drag handle] [Title – Artist  key]  [●1][●3]  [+]
 *
 * Left swipe reveals a colored background and triggers a confirmation dialog.
 * - If set filter active: "Remove from Set N"
 * - Otherwise: "Delete from Library"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListItem(
    song: SongEntity,
    viewModel: LibraryViewModel,
    activeSetFilter: Int?,
    onClick: () -> Unit,
    onDeleteSong: () -> Unit,
    onRemoveFromSet: (String, Int) -> Unit,
    onAddToSet: () -> Unit,
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    var sets by remember { mutableStateOf<List<SetEntity>>(emptyList()) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showAddToSetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(song.id) {
        sets = viewModel.getSetsContainingSong(song.id)
    }

    val rowAccentColor = remember(sets) {
        sets.minByOrNull { it.number }?.number?.let { SetColor.getSetColor(it) }
    }

    // Two-action confirmation dialog triggered by left swipe
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("\"${song.title}\"") },
            text = {
                Column {
                    // Action 1: Remove from Set (only shown when song is in the active set)
                    if (activeSetFilter != null && sets.any { it.number == activeSetFilter }) {
                        OutlinedButton(
                            onClick = {
                                showConfirmDialog = false
                                onRemoveFromSet(song.id, activeSetFilter)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                "Remove from Set $activeSetFilter",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.padding(top = 8.dp))
                    }
                    // Action 2: Delete from Library (always shown)
                    OutlinedButton(
                        onClick = {
                            showConfirmDialog = false
                            onDeleteSong()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Delete from Library",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Permanently removes the song record",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add to Set picker dialog
    if (showAddToSetDialog) {
        AlertDialog(
            onDismissRequest = { showAddToSetDialog = false },
            title = { Text("Add to Set") },
            text = {
                Column {
                    for (setNum in 1..4) {
                        val setColor = SetColor.getSetColor(setNum)
                        val alreadyInSet = sets.any { it.number == setNum }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !alreadyInSet) {
                                    if (!alreadyInSet) {
                                        showAddToSetDialog = false
                                        viewModel.addSongToSetNumber(song.id, setNum)
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (alreadyInSet) setColor.copy(alpha = 0.3f) else setColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = setNum.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Set $setNum" + if (alreadyInSet) " (already added)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (alreadyInSet)
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (setNum < 4) HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToSetDialog = false }) { Text("Cancel") }
            }
        )
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            value == SwipeToDismissBoxValue.EndToStart
        }
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            showConfirmDialog = true
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = if (activeSetFilter != null) "Remove from set" else "Delete",
                    tint = Color.White,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .let { if (isDragging) it.padding(horizontal = 4.dp) else it }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle — only shown when set filter is active
            if (activeSetFilter != null) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(20.dp)
                        .then(dragHandleModifier)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Title (bold) stacked above Artist (grey) — Column takes remaining width
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = rowAccentColor ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.artist != "Unknown Artist") {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Key badge — accent-colored pill, directly left of Set Circles
            song.currentKey?.let { key ->
                KeyBadge(key = key, accentColor = rowAccentColor)
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Set membership circles
            Spacer(modifier = Modifier.width(6.dp))
            sets.forEach { set ->
                SetNumberCircle(
                    setNumber = set.number,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // Add-to-Set pill button with outline border
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedButton(
                onClick = { showAddToSetDialog = true },
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to set",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
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
        placeholder = { Text("Search by title or artist...") },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearClick) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
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
 * Small colored circle showing which set number a song belongs to.
 */
@Composable
fun SetNumberCircle(
    setNumber: Int,
    modifier: Modifier = Modifier
) {
    val color = SetColor.getSetColor(setNumber)
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = setNumber.toString(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 11.sp
        )
    }
}

/**
 * Key badge pill — uses accentColor (set tint) when available, gray fallback otherwise.
 */
@Composable
fun KeyBadge(
    key: String,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = accentColor?.copy(alpha = 0.12f) ?: MaterialTheme.colorScheme.surfaceVariant
    val textColor = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = bgColor
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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
                text = if (hasSearchQuery) "No songs found" else "Your library is empty",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (hasSearchQuery) "Try a different search term"
                       else "Use 'Add Songs' to import your first song",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
