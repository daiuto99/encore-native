package com.encore.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SongEntity
import com.encore.core.ui.theme.LocalEncoreColors
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
    onEditChart: ((songId: String) -> Unit)? = null,
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
                    onAddToSet = { songId -> viewModel.addToPerformSet(songId) },
                    onReorder = { songId, toIdx -> viewModel.reorderSong(songId, toIdx) },
                    onEditChart = onEditChart,
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
@OptIn(ExperimentalFoundationApi::class)
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
    onEditChart: ((songId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemHeightPx = remember(density) { with(density) { 60.dp.toPx() } }
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    // Drag state
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    // Local shadow list — swapped live during drag; DB written only on drag end
    val localSongs = remember { mutableStateListOf<SongEntity>() }
    LaunchedEffect(songs) {
        // Only sync from DB when no drag is active; delay lets animateItemPlacement finish
        if (draggingIndex == null) {
            delay(150)
            localSongs.clear()
            localSongs.addAll(songs)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(localSongs, key = { it.id }) { song ->
            val index = localSongs.indexOf(song)
            val isDragging = draggingIndex == index

            val dragHandleModifier = if (activeSetFilter != null) {
                Modifier.pointerInput(song.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggingIndex = localSongs.indexOf(song)
                            dragAccumY = 0f
                        },
                        onDrag = { change, drag ->
                            change.consume()
                            dragAccumY += drag.y
                            val currentIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress
                            val steps = (dragAccumY / itemHeightPx).toInt()
                            if (steps != 0) {
                                val newIdx = (currentIdx + steps).coerceIn(0, localSongs.size - 1)
                                if (newIdx != currentIdx) {
                                    localSongs.add(newIdx, localSongs.removeAt(currentIdx))
                                    draggingIndex = newIdx
                                    dragAccumY -= steps * itemHeightPx
                                }
                            }
                        },
                        onDragEnd = {
                            val finalIdx = draggingIndex
                            val originalIdx = songs.indexOf(song)
                            if (finalIdx != null && finalIdx != originalIdx) {
                                onReorder(song.id, finalIdx)
                            }
                            draggingIndex = null
                            dragAccumY = 0f
                        },
                        onDragCancel = {
                            // Restore original order on cancel
                            localSongs.clear()
                            localSongs.addAll(songs)
                            draggingIndex = null
                            dragAccumY = 0f
                        }
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
                onEditChart = onEditChart,
                isDragging = isDragging,
                dragHandleModifier = dragHandleModifier,
                modifier = Modifier
                    .animateItemPlacement(spring(stiffness = Spring.StiffnessMediumLow))
                    .zIndex(if (isDragging) 1f else 0f)
                    .then(if (isDragging) Modifier
                        .scale(1.03f)
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                        .border(2.dp, Color.Red.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    else Modifier)
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
    onEditChart: ((songId: String) -> Unit)? = null,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val encoreColors = LocalEncoreColors.current
    val sets by remember(song.id) { viewModel.observeSetsContainingSong(song.id) }
        .collectAsState(initial = emptyList())
    var showConfirmDialog by remember { mutableStateOf(false) }

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


    var showEditSheet by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.75f }
    )

    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> {
                showConfirmDialog = true
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                showEditSheet = true
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            else -> {}
        }
    }

    if (showEditSheet) {
        SongEditBottomSheet(
            song = song,
            onSave = { title, artist, isLeadGuitar, harmonyMode ->
                viewModel.updateSongMetadata(song.id, title, artist, isLeadGuitar, harmonyMode)
                showEditSheet = false
            },
            onDismiss = { showEditSheet = false },
            onEditChart = onEditChart?.let { cb -> { showEditSheet = false; cb(song.id) } }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isDragging,
        enableDismissFromEndToStart = !isDragging,
        backgroundContent = {
            val isEditSwipe = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (isEditSwipe) Color(0xFF5AC8FA) else MaterialTheme.colorScheme.error,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = if (isEditSwipe) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = if (isEditSwipe) Icons.Default.Edit else Icons.Default.Delete,
                    contentDescription = if (isEditSwipe) "Edit" else if (activeSetFilter != null) "Remove from set" else "Delete",
                    tint = Color.White,
                    modifier = Modifier.padding(start = if (isEditSwipe) 20.dp else 0.dp, end = if (isEditSwipe) 0.dp else 20.dp)
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .let { if (isDragging) it.padding(horizontal = 4.dp) else it }
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = encoreColors.cardBackground,
            shadowElevation = encoreColors.cardElevation,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left accent bar — 4dp wide, full height, set color
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(rowAccentColor ?: Color.Transparent)
                )

                // Content row with internal padding
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Drag handle — only shown when set filter is active
                    if (activeSetFilter != null) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Drag to reorder",
                            tint = encoreColors.titleText.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(20.dp)
                                .then(dragHandleModifier)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    // Single-line: [Bold Title] — [Artist]
                    val titleText = remember(song.id, song.title, song.artist, encoreColors.titleText, encoreColors.artistText, encoreColors.separatorText) {
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = encoreColors.titleText)) {
                                append(song.title)
                            }
                            if (song.artist != "Unknown Artist") {
                                withStyle(SpanStyle(color = encoreColors.separatorText)) {
                                    append("  —  ")
                                }
                                withStyle(SpanStyle(color = encoreColors.artistText)) {
                                    append(song.artist)
                                }
                            }
                        }
                    }
                    Text(
                        text = titleText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // Key badge — glass pill
                    song.displayKey?.let { key ->
                        KeyBadge(key = key, accentColor = rowAccentColor)
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Set membership circles
                    Spacer(modifier = Modifier.width(6.dp))
                    sets.forEach { set ->
                        SetNumberCircle(
                            setNumber = set.number,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    // Stage-for-perform pill — tap to add song to the perform set
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = { onAddToSet() },
                        shape = RoundedCornerShape(50.dp),
                        border = BorderStroke(1.dp, encoreColors.titleText.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(60.dp)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to set",
                            tint = encoreColors.artistText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
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
            focusedContainerColor = LocalEncoreColors.current.searchBarBackground,
            unfocusedContainerColor = LocalEncoreColors.current.searchBarBackground,
            disabledContainerColor = LocalEncoreColors.current.searchBarBackground
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
 * Ghost key badge — barely-there pill that labels the key without competing with song titles.
 * Uses a hairline accent tint when the song is in a set, otherwise near-invisible on black.
 */
@Composable
fun KeyBadge(
    key: String,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val encoreColors = LocalEncoreColors.current
    Box(
        modifier = modifier
            .background(encoreColors.titleText.copy(alpha = 0.10f), RoundedCornerShape(50))
            .border(1.dp, encoreColors.titleText.copy(alpha = 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = encoreColors.titleText.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium
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

/**
 * Edit modal — Title, Artist, Key, Harmony Mode, Highlight Style with Zen theming.
 * Used by both Library (swipe-right) and Performance (header icon).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongEditBottomSheet(
    song: com.encore.core.data.entities.SongEntity,
    onSave: (title: String, artist: String, isLeadGuitar: Boolean, isHarmonyMode: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onEditChart: (() -> Unit)? = null
) {
    val encoreColors = LocalEncoreColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(if (song.artist == "Unknown Artist") "" else song.artist) }
    var leadGuitar by remember(song.id) { mutableStateOf(song.isLeadGuitar) }
    var harmonyMode by remember(song.id) { mutableStateOf(song.isHarmonyMode) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = encoreColors.titleText,
        unfocusedTextColor = encoreColors.titleText,
        focusedBorderColor = encoreColors.titleText.copy(alpha = 0.5f),
        unfocusedBorderColor = encoreColors.titleText.copy(alpha = 0.2f),
        focusedLabelColor = encoreColors.artistText,
        unfocusedLabelColor = encoreColors.artistText,
        cursorColor = encoreColors.titleText,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

    val baseToolbar = LocalTextToolbar.current
    val noSelectAllToolbar = remember(baseToolbar) {
        object : TextToolbar {
            override val status: TextToolbarStatus get() = baseToolbar.status
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) = baseToolbar.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, null)
            override fun hide() = baseToolbar.hide()
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides noSelectAllToolbar) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = encoreColors.cardBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Song",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = encoreColors.titleText,
                    modifier = Modifier.weight(1f)
                )
                onEditChart?.let { editChart ->
                    androidx.compose.material3.TextButton(onClick = editChart) {
                        Text(
                            text = "Edit Chart",
                            style = MaterialTheme.typography.labelLarge,
                            color = encoreColors.iconTint
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Lead Guitar toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lead Guitar",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = encoreColors.titleText
                    )
                    Text(
                        text = "Show guitar icon in performance header",
                        style = MaterialTheme.typography.bodySmall,
                        color = encoreColors.artistText
                    )
                }
                Switch(
                    checked = leadGuitar,
                    onCheckedChange = { leadGuitar = it }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Harmony Mode toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Harmony Mode",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = encoreColors.titleText
                    )
                    Text(
                        text = "Show harmony annotations",
                        style = MaterialTheme.typography.bodySmall,
                        color = encoreColors.artistText
                    )
                }
                Switch(
                    checked = harmonyMode,
                    onCheckedChange = { harmonyMode = it }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            androidx.compose.material3.Button(
                onClick = {
                    onSave(
                        title.trim().ifBlank { song.title },
                        artist.trim().ifBlank { "Unknown Artist" },
                        leadGuitar,
                        harmonyMode
                    )
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Exit",
                    style = MaterialTheme.typography.labelLarge,
                    color = encoreColors.artistText
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    } // CompositionLocalProvider
}
