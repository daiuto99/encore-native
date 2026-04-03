package com.encore.feature.performance

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.encore.core.data.entities.SetlistEntity
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.preferences.AppPreferences
import com.encore.core.ui.theme.LocalEncoreColors
import com.encore.core.ui.theme.SetColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// ─────────────────────────────────────────────────────────────────────────────
// Viewer preferences
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Per-section display style — colour, base font size, and bold flag.
 *
 * Stored in [ViewerPreferences.sectionStyles] so the entire map can be
 * replaced from a DataStore-backed Settings screen without touching render code.
 */
data class SectionStyle(
    val color: Color,
    val fontSize: TextUnit,
    val isBold: Boolean = true,
)

/**
 * Default section styles matching ChordSidekick's colour assignments.
 *
 * Keys are lowercase, stripped of trailing numbers (see [normalizeSectionName]).
 * Aliases handle common spelling variations (e.g. "prechorus" / "pre-chorus").
 */
val DEFAULT_SECTION_STYLES: Map<String, SectionStyle> = mapOf(
    "intro"         to SectionStyle(Color(0xFF60A5FA), 20.sp),  // Blue-400
    "verse"         to SectionStyle(Color(0xFFFB923C), 20.sp),  // Orange-400
    "pre-chorus"    to SectionStyle(Color(0xFFFACC15), 20.sp),  // Yellow-400
    "prechorus"     to SectionStyle(Color(0xFFFACC15), 20.sp),  // alias
    "chorus"        to SectionStyle(Color(0xFFF87171), 20.sp),  // Red-400
    "post-chorus"   to SectionStyle(Color(0xFFF472B6), 20.sp),  // Pink-400
    "bridge"        to SectionStyle(Color(0xFFA78BFA), 20.sp),  // Purple-400
    "outro"         to SectionStyle(Color(0xFF9CA3AF), 20.sp),  // Gray-400
    "tag"           to SectionStyle(Color(0xFF34D399), 20.sp),  // Emerald-400
    "interlude"     to SectionStyle(Color(0xFF22D3EE), 20.sp),  // Cyan-400
    "instrumental"  to SectionStyle(Color(0xFF4ADE80), 20.sp),  // Green-400
    "solo"          to SectionStyle(Color(0xFFFBBF24), 20.sp),  // Amber-400
    "breakdown"     to SectionStyle(Color(0xFFFF6B6B), 20.sp),  // Coral
    "coda"          to SectionStyle(Color(0xFF9CA3AF), 20.sp),  // Gray-400
)

/**
 * Display preferences for the performance viewer.
 *
 * All values carry sensible defaults that can be overridden from DataStore once
 * the 'Options' screen is wired up in a future milestone.  For now, create an
 * instance with `remember { ViewerPreferences() }` to get the defaults.
 */
data class ViewerPreferences(
    // Body text
    val bodyFontSizeSp: Float = 14f,
    val lineHeightSp: Float = 21f,
    val lyricAlpha: Float = 0.85f,
    // Section headers (used when section name is not in [sectionStyles])
    val h1FontSizeSp: Float = 28f,
    val h2FontSizeSp: Float = 24f,
    val hnFontSizeSp: Float = 20f,
    val sectionTopPaddingDp: Float = 20f,
    val sectionBottomPaddingDp: Float = 4f,
    // Key badge
    val keyBadgeAlpha: Float = 0.60f,
    val keyBadgeFontSizeSp: Float = 13f,
    // Per-section styles — swap the entire map to restyle all sections at once
    val sectionStyles: Map<String, SectionStyle> = DEFAULT_SECTION_STYLES,
)

// ─────────────────────────────────────────────────────────────────────────────
// Section model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A parsed segment of a song file.
 *
 * - [KeyLabel]  — extracted from `**Key:** G` metadata lines
 * - [Header]    — section header from HTML `<span>` or legacy `# Markdown`
 * - [Body]      — block of lyric/chord lines between headers
 */
sealed class SongSection {
    data class KeyLabel(val key: String) : SongSection()
    data class Header(val text: String, val level: Int, val color: String?) : SongSection()
    data class Body(val markdown: String) : SongSection()
}

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Song Detail Screen — Full-Screen Performance Mode.
 *
 * Rendering pipeline (ChordSidekick / Obsidian Chord Sheets format):
 *  - `**Key:** G`  → slim key badge at top
 *  - `<span style="color: (#XXXXXX); font-weight: bold;">## Section</span>` → coloured section header
 *  - `` `[Chord]` `` backtick notation → chord coloured with set accent; lyrics remain white
 *  - Legacy `# Header` and bare chord lines (old format) also supported
 *
 * Features:
 *  - Pure black background for stage use
 *  - Screen kept on while active
 *  - Slim title/artist header + close button
 *  - Monospace body for chord alignment
 *  - Pinch-to-zoom with per-song persistence (500ms debounce)
 *  - Double-tap to reset zoom
 *  - Tap to reveal floating zoom HUD (auto-hides after 3s)
 *  - Horizontal swipe + edge arrows for set navigation
 *
 * Phase 4.5: High-Visibility Performance Viewer
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongDetailScreen(
    viewModel: SongDetailViewModel,
    songId: String,
    setNumber: Int = -1,
    onNavigateBack: () -> Unit,
    onToggleDarkMode: (() -> Unit)? = null,
    onEditClick: ((com.encore.core.data.entities.SongEntity) -> Unit)? = null,
    onPageChanged: (() -> Unit)? = null,
    onNavigateToSong: ((String) -> Unit)? = null,
    appPreferences: AppPreferences = AppPreferences(),
    syncHudState: com.encore.core.data.sync.SyncHudState? = null,
    modifier: Modifier = Modifier
) {
    val song by viewModel.song.collectAsState()
    val textSizeMultiplier by viewModel.textSizeMultiplier.collectAsState()
    val prevSongId by viewModel.prevSongId.collectAsState()
    val nextSongId by viewModel.nextSongId.collectAsState()
    val performSongIds by viewModel.performSongIds.collectAsState()
    val setName by viewModel.setName.collectAsState()
    val prevSong by viewModel.prevSong.collectAsState()
    val nextSong by viewModel.nextSong.collectAsState()
    val setlists by viewModel.setlists.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val pagerResetTrigger by viewModel.pagerResetTrigger.collectAsState()

    var showControls by remember { mutableStateOf(false) }
    var showPageIndicator by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Per-song in-session zoom map. Populated on first zoom gesture; falls back to DB value.
    val zoomPerSong = remember { mutableStateMapOf<String, Float>() }

    // Keep screen on during performance
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            zoomPerSong.clear()
        }
    }

    LaunchedEffect(songId) {
        viewModel.loadSong(songId, setNumber)
    }

    // Auto-hide zoom controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    BackHandler { onNavigateBack() }

    // Effective list: always ≥ 1 entry so pager never has pageCount = 0
    val effectiveSongIds = if (performSongIds.isEmpty()) listOf(songId) else performSongIds
    val initialPage = remember(effectiveSongIds, songId) {
        effectiveSongIds.indexOf(songId).coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { effectiveSongIds.size }

    // Jump instantly to the correct page once performSongIds loads
    LaunchedEffect(effectiveSongIds) {
        val targetPage = effectiveSongIds.indexOf(songId).coerceAtLeast(0)
        if (targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    // After loadSetlist() completes, scroll to page 0 so the user sees the new set from the top
    LaunchedEffect(pagerResetTrigger) {
        if (pagerResetTrigger > 0) pagerState.scrollToPage(0)
    }

    // Auto-clear save success feedback after 2.5 seconds
    LaunchedEffect(saveSuccess) {
        if (saveSuccess != null) {
            delay(2500)
            viewModel.clearSaveSuccess()
        }
    }

    // Notify ViewModel when the user swipes to a new page; show "X / Y" indicator briefly
    LaunchedEffect(pagerState.currentPage) {
        val currentId = effectiveSongIds.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        viewModel.onPageChanged(currentId, setNumber)
        onPageChanged?.invoke()
        if (effectiveSongIds.size > 1) {
            showPageIndicator = true
            delay(2000)
            showPageIndicator = false
        }
    }

    val encoreColors = LocalEncoreColors.current
    val chordAccentColor = if (setNumber > 0) {
        SetColor.getSetColor(setNumber)
    } else {
        parseColorSafe(
            if (encoreColors.isDark) appPreferences.darkChordColor
            else appPreferences.lightChordColor
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(encoreColors.screenBackground)
    ) {
        if (song == null) {
            // ── Initial load spinner ─────────────────────────────────────────
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            // ── HorizontalPager — one page per song in the set ───────────────
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageSongId = effectiveSongIds.getOrNull(page) ?: songId
                val pageSong by viewModel.getSongForPage(pageSongId).collectAsState(initial = null)
                val pageScrollState = rememberScrollState()
                val isActivePage = page == pagerState.currentPage

                Crossfade(
                    targetState = pageSong?.id,
                    animationSpec = tween(250)
                ) { _ ->
                    val currentSong = pageSong
                    if (currentSong != null) {
                        // Use currentSong.id (concrete DB identity) — not pageSongId (pager slot).
                        // This locks every gesture closure to the exact song being displayed,
                        // immune to slot reuse during mid-swipe beyondBoundsPageCount rendering.
                        val concreteSongId = currentSong.id
                        val effectiveZoom = zoomPerSong[concreteSongId] ?: currentSong.lastZoomLevel
                        SongContent(
                            song = currentSong,
                            scrollState = pageScrollState,
                            textSizeMultiplier = effectiveZoom,
                            chordAccentColor = chordAccentColor,
                            appPreferences = appPreferences,
                            onZoomChange = { zoom ->
                                zoomPerSong[concreteSongId] = zoom
                                if (isActivePage) viewModel.updateTextSize(zoom)
                            },
                            onSingleTap = { showControls = !showControls },
                            onDoubleTap = {
                                // Map write is unconditional — the key is song-specific so it's
                                // always safe. ViewModel reset only fires for the active page.
                                zoomPerSong[concreteSongId] = 1.0f
                                if (isActivePage) {
                                    viewModel.resetTextSize()
                                    showControls = true
                                }
                            }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }

            // ── Context Bar + Performance Dashboard (pinned) ─────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
            ) {
                if (setName.isNotEmpty()) {
                    PerformanceContextBar(
                        setName = setName,
                        setNumber = setNumber,
                        setColor = SetColor.getSetColor(setNumber),
                        syncHudState = syncHudState,
                        prevSong = prevSong,
                        nextSong = nextSong,
                        saveSuccess = saveSuccess,
                        onPrevClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        onNextClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    )
                }
                PerformanceDashboard(
                    song = song!!,
                    harmonyColor = parseColorSafe(
                        if (encoreColors.isDark) appPreferences.darkHarmonyColor
                        else appPreferences.lightHarmonyColor
                    ),
                    setColor = SetColor.getSetColor(setNumber),
                    leadIconColor = parseColorSafe(
                        if (encoreColors.isDark) appPreferences.darkLeadIconColor
                        else appPreferences.lightLeadIconColor
                    ),
                    capoColor = parseColorSafe(
                        if (encoreColors.isDark) appPreferences.darkCapoColor
                        else appPreferences.lightCapoColor
                    ),
                    appPreferences = appPreferences,
                    onToggleDarkMode = onToggleDarkMode,
                    onEditClick = onEditClick,
                    onNavigateBack = onNavigateBack,
                    onSaveClick = if (setNumber > 0) ({ showSaveDialog = true }) else null,
                    onLoadClick = if (setNumber > 0) ({ showLoadDialog = true }) else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Prev song arrow ──────────────────────────────────────────────
            if (prevSongId != null) {
                IconButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous song",
                        tint = encoreColors.titleText.copy(alpha = 0.25f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // ── Next song arrow ──────────────────────────────────────────────
            if (nextSongId != null) {
                IconButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next song",
                        tint = encoreColors.titleText.copy(alpha = 0.25f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // ── Page X of Y indicator ────────────────────────────────────────
            AnimatedVisibility(
                visible = showPageIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.15f),
                    tonalElevation = 0.dp
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${effectiveSongIds.size}",
                        color = Color.White.copy(alpha = 0.80f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            // ── Floating zoom HUD ────────────────────────────────────────────
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                val activeSongId = effectiveSongIds.getOrNull(pagerState.currentPage) ?: songId
                val activeZoom = zoomPerSong[activeSongId] ?: textSizeMultiplier
                FloatingZoomControls(
                    currentZoom = activeZoom,
                    onZoomIn = {
                        val newZoom = (activeZoom + 0.1f).coerceIn(0.5f, 3.0f)
                        zoomPerSong[activeSongId] = newZoom
                        viewModel.updateTextSize(newZoom)
                        showControls = true
                    },
                    onZoomOut = {
                        val newZoom = (activeZoom - 0.1f).coerceIn(0.5f, 3.0f)
                        zoomPerSong[activeSongId] = newZoom
                        viewModel.updateTextSize(newZoom)
                        showControls = true
                    }
                )
            }
        }
    }

    // ── Save / Load dialogs (shown over the performance screen) ──────────────
    if (showSaveDialog) {
        SaveSetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                viewModel.saveCurrentSet(name)
                showSaveDialog = false
            }
        )
    }
    if (showLoadDialog) {
        LoadSetDialog(
            setlists = setlists,
            onDismiss = { showLoadDialog = false },
            onLoad = { id ->
                viewModel.loadSetlist(id)
                showLoadDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Song content composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Scrollable song body.
 *
 * Renders the parsed [SongSection] list:
 *  - [SongSection.KeyLabel] → dim key badge
 *  - [SongSection.Header]   → coloured bold section title (monospace)
 *  - [SongSection.Body]     → line-by-line monospace text; chords coloured via [chordAccentColor]
 */
@Composable
fun SongContent(
    song: SongEntity,
    scrollState: androidx.compose.foundation.ScrollState,
    textSizeMultiplier: Float,
    chordAccentColor: Color?,
    onZoomChange: (Float) -> Unit,
    onSingleTap: () -> Unit,
    onDoubleTap: () -> Unit,
    appPreferences: AppPreferences = AppPreferences(),
    modifier: Modifier = Modifier
) {
    val encoreColors = LocalEncoreColors.current
    var currentZoom by remember { mutableFloatStateOf(textSizeMultiplier) }
    val isDark = encoreColors.isDark
    val sections = remember(song.markdownBody, song.title, appPreferences.showChords, appPreferences.showKeyInfo, isDark) {
        val body = stripLeadingTitle(song.markdownBody, song.title)
        parseSongSections(body, appPreferences, isDark)
    }
    val vp = remember { ViewerPreferences() }

    // Reset local zoom only when the song itself changes, not on every zoom tick.
    LaunchedEffect(song.id) { currentZoom = textSizeMultiplier }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput("zoom") {
                awaitEachGesture {
                    var pressed = true
                    var didZoom = false
                    while (pressed) {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                currentZoom = (currentZoom * zoomChange).coerceIn(0.5f, 3.0f)
                                event.changes.forEach { it.consume() }
                                didZoom = true
                            }
                        }
                        pressed = event.changes.any { it.pressed }
                    }
                    // Only persist if a real pinch occurred — single-finger taps (including
                    // double-tap reset) must not overwrite the zoom that onDoubleTap just set.
                    if (didZoom) onZoomChange(currentZoom)
                }
            }
            .pointerInput("tap") {
                // Use Initial pass so double-tap is detected before HorizontalPager's
                // scroll handler on Main pass can consume the pointer events.
                awaitEachGesture {
                    val down = awaitPointerEvent(PointerEventPass.Initial)
                    if (down.changes.size != 1 || !down.changes[0].pressed) return@awaitEachGesture

                    // Wait for first finger up
                    do {
                        val up = awaitPointerEvent(PointerEventPass.Initial)
                        if (up.changes.none { it.pressed }) break
                    } while (true)

                    // Watch for second tap within the double-tap window
                    val isDoubleTap = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        val second = awaitPointerEvent(PointerEventPass.Initial)
                        second.changes.size == 1 && second.changes[0].pressed
                    } ?: false

                    if (isDoubleTap) onDoubleTap() else onSingleTap()
                }
            }
            .verticalScroll(scrollState)
            // top padding clears the floating cards: 8dp + 68dp dashboard + 8dp + 52dp context bar + 8dp gap = 144dp → 152dp
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 152.dp)
    ) {
        // Hoist per-song color lookups out of the render loop
        val lyricColor = parseColorSafe(
            if (isDark) appPreferences.darkLyricColor else appPreferences.lightLyricColor
        ).copy(alpha = vp.lyricAlpha)
        val harmonyColor = parseColorSafe(
            if (isDark) appPreferences.darkHarmonyColor else appPreferences.lightHarmonyColor
        )

        // Group flat section list into [Header + its Bodies] for card rendering.
        // KeyLabel entries are suppressed here — displayed in PerformanceDashboard.
        val groups = remember(sections) {
            val result = mutableListOf<Pair<SongSection.Header?, MutableList<SongSection.Body>>>()
            var current: Pair<SongSection.Header?, MutableList<SongSection.Body>>? = null
            for (s in sections) {
                when (s) {
                    is SongSection.KeyLabel -> {}
                    is SongSection.Header -> {
                        current?.let { result.add(it) }
                        current = Pair(s, mutableListOf())
                    }
                    is SongSection.Body -> {
                        if (current == null) current = Pair(null, mutableListOf())
                        current!!.second.add(s)
                    }
                }
            }
            current?.let { result.add(it) }
            result
        }

        groups.forEachIndexed { groupIndex, (header, bodies) ->
            // Gap between section cards
            if (groupIndex > 0) Spacer(Modifier.height(vp.sectionTopPaddingDp.dp))

            // Resolve this section's accent colour
            val sectionColor: Color = if (header != null) {
                val styleEntry = vp.sectionStyles[normalizeSectionName(header.text)]
                styleEntry?.color
                    ?: header.color?.let {
                        try { Color(android.graphics.Color.parseColor(it)) }
                        catch (_: Exception) { null }
                    }
                    ?: (chordAccentColor ?: MaterialTheme.colorScheme.primary)
            } else {
                chordAccentColor ?: MaterialTheme.colorScheme.primary
            }

            if (header != null) {
                // ── Section card: drawBehind paints background + accent bar
                //    after layout so the Column sizes freely with zoom ─────────
                val styleEntry = vp.sectionStyles[normalizeSectionName(header.text)]
                val headerFontSizeSp = styleEntry?.fontSize?.value
                    ?: when (header.level) {
                        1    -> vp.h1FontSizeSp
                        2    -> vp.h2FontSizeSp
                        else -> vp.hnFontSizeSp
                    }
                val bgColor    = encoreColors.cardBackground
                val barColor   = sectionColor.copy(alpha = 0.38f)
                val barWidthPx = 4f  // dp — converted in drawBehind via density
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .drawBehind {
                            drawRect(color = bgColor)
                            drawRect(
                                color = barColor,
                                size = Size(barWidthPx * density, size.height)
                            )
                        }
                        .padding(start = 16.dp, top = 10.dp, end = 10.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = header.text,
                        color = sectionColor,
                        fontSize = (headerFontSizeSp * textSizeMultiplier).sp,
                        fontWeight = if (styleEntry?.isBold != false) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = vp.sectionBottomPaddingDp.dp)
                    )
                    SectionBodyLines(
                        bodies = bodies,
                        chordAccentColor = chordAccentColor,
                        lyricColor = lyricColor,
                        harmonyColor = harmonyColor,
                        appPreferences = appPreferences,
                        textSizeMultiplier = textSizeMultiplier,
                        vp = vp
                    )
                }
            } else {
                // No header — body-only block before first section tag, render flat
                SectionBodyLines(
                    bodies = bodies,
                    chordAccentColor = chordAccentColor,
                    lyricColor = lyricColor,
                    harmonyColor = harmonyColor,
                    appPreferences = appPreferences,
                    textSizeMultiplier = textSizeMultiplier,
                    vp = vp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section body renderer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a list of [SongSection.Body] items line by line.
 * Handles multi-line [h]...[/h] harmony blocks and chord-spacing gaps.
 * Extracted so it can be called from both the card path and the headerless path.
 */
@Composable
private fun SectionBodyLines(
    bodies: List<SongSection.Body>,
    chordAccentColor: Color?,
    lyricColor: Color,
    harmonyColor: Color,
    appPreferences: AppPreferences,
    textSizeMultiplier: Float,
    vp: ViewerPreferences
) {
    bodies.forEach { body ->
        if (body.markdown.isNotBlank()) {
            var inHarmonyBlock = false
            body.markdown.lines().forEach { rawLine ->
                val hasOpen = rawLine.contains("[h]")
                val hasClose = rawLine.contains("[/h]")
                val isHarmonyLine: Boolean
                val lineToRender: String
                when {
                    hasOpen && hasClose -> {
                        // Self-contained — let buildChordLine handle it
                        isHarmonyLine = false
                        lineToRender = rawLine
                    }
                    hasOpen -> {
                        inHarmonyBlock = true
                        isHarmonyLine = true
                        lineToRender = rawLine.replace("[h]", "")
                    }
                    hasClose -> {
                        isHarmonyLine = true
                        lineToRender = rawLine.replace("[/h]", "")
                        inHarmonyBlock = false
                    }
                    else -> {
                        isHarmonyLine = inHarmonyBlock
                        lineToRender = rawLine
                    }
                }
                if (lineToRender.isBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    Text(
                        text = buildChordLine(
                            lineToRender,
                            chordAccentColor,
                            lyricColor,
                            harmonyColor = harmonyColor,
                            isHarmonyLine = isHarmonyLine
                        ),
                        fontSize = (vp.bodyFontSizeSp * textSizeMultiplier).sp,
                        lineHeight = (vp.lineHeightSp * textSizeMultiplier).sp,
                        fontFamily = FontFamily.Monospace
                    )
                    val isChordLine = isPureBacktickChordLine(lineToRender) || isBareChordLine(lineToRender)
                    if (isChordLine && appPreferences.chordSpacing > 0f) {
                        Spacer(Modifier.height(appPreferences.chordSpacing.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Performance Context Bar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Floating card pinned above [PerformanceDashboard] showing set-level navigation context.
 *
 * Layout (left → right):
 *  - Prev song pill (← title) or "..." when first song
 *  - Set name centred in set colour
 *  - Next song pill (title →) or "..." when last song
 *  - Divider + live clock (HH:MM:SS, 1-second tick)
 */
@Composable
private fun PerformanceContextBar(
    setName: String,
    setNumber: Int,
    setColor: Color,
    prevSong: SongEntity?,
    nextSong: SongEntity?,
    saveSuccess: String?,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    syncHudState: com.encore.core.data.sync.SyncHudState? = null,
    modifier: Modifier = Modifier
) {
    val encoreColors = LocalEncoreColors.current
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = java.util.Calendar.getInstance()
            currentTime = "%02d:%02d:%02d".format(
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                cal.get(java.util.Calendar.SECOND)
            )
            delay(1000)
        }
    }

    Box(modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = encoreColors.cardBackground.copy(alpha = 0.98f),
            shadowElevation = encoreColors.cardElevation,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(1.dp, encoreColors.divider, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Prev Song pill (weight=1f for symmetry) ──────────────────
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (prevSong != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(encoreColors.titleText.copy(alpha = 0.07f))
                                .clickable(onClick = onPrevClick)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous song",
                                tint = encoreColors.titleText.copy(alpha = 0.65f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = prevSong.title,
                                color = encoreColors.titleText.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = "...",
                            color = encoreColors.titleText.copy(alpha = 0.22f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }

                // ── Set Name / Save feedback (centre, weight=1f) ─────────────
                val setLabel = if (setNumber > 0) "SET $setNumber" else setName
                Text(
                    text = saveSuccess ?: setLabel,
                    color = if (saveSuccess != null) Color(0xFF4CAF50) else setColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // ── Next Song pill (weight=1f for symmetry) ──────────────────
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (nextSong != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(encoreColors.titleText.copy(alpha = 0.07f))
                                .clickable(onClick = onNextClick)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = nextSong.title,
                                color = encoreColors.titleText.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next song",
                                tint = encoreColors.titleText.copy(alpha = 0.65f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "...",
                            color = encoreColors.titleText.copy(alpha = 0.22f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }

                // ── Divider + Clock / Sync HUD (aligns under Control Pill) ──
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.55f)
                        .background(encoreColors.divider)
                )
                Spacer(modifier = Modifier.width(10.dp))
                when (val hud = syncHudState) {
                    is com.encore.core.data.sync.SyncHudState.InProgress -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.width(96.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = encoreColors.titleText.copy(alpha = 0.55f)
                            )
                            Text(
                                text = "${hud.current}/${hud.total}",
                                color = encoreColors.titleText.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    is com.encore.core.data.sync.SyncHudState.Complete -> {
                        Text(
                            text = "✓ Synced",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(76.dp)
                        )
                    }
                    null -> {
                        Text(
                            text = currentTime,
                            color = encoreColors.titleText.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(76.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Performance Dashboard
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pinned floating "performance card" — visually identical to Library cards.
 *
 * Structure (left → right inside a floating Surface):
 *  [Key Anchor] │ Title / Artist+metadata │ [Status Pill] │ 1dp divider │ [Control Pill]
 *
 * The card floats with 8dp horizontal + 8dp top inset so it never touches the screen edges.
 * Total height consumed: 8dp (top gap) + 68dp (card) = 76dp.
 */
@Composable
private fun PerformanceDashboard(
    song: SongEntity,
    harmonyColor: Color,
    setColor: Color,
    leadIconColor: Color,
    capoColor: Color,
    appPreferences: AppPreferences,
    onToggleDarkMode: (() -> Unit)?,
    onEditClick: ((SongEntity) -> Unit)?,
    onNavigateBack: () -> Unit,
    onSaveClick: (() -> Unit)? = null,
    onLoadClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val encoreColors = LocalEncoreColors.current
    val bpm = remember(song.markdownBody) { parseBpm(song.markdownBody) }
    val (keyRoot, keyScale) = remember(song.displayKey) { splitKey(song.displayKey) }

    // Key anchor badge colours — library style (semi-transparent tint)
    val keyBadgeBg     = harmonyColor.copy(alpha = 0.13f)
    val keyBadgeBorder = harmonyColor.copy(alpha = 0.35f)

    // Floating card — matches Library card exactly
    Box(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = encoreColors.cardBackground.copy(alpha = 0.98f),
            shadowElevation = encoreColors.cardElevation,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .border(1.dp, encoreColors.divider, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Key anchor (library KeyBadge style, enlarged) ────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(keyBadgeBg, RoundedCornerShape(8.dp))
                        .border(1.dp, keyBadgeBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = keyRoot,
                        color = harmonyColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp
                    )
                    if (keyScale.isNotEmpty()) {
                        Text(
                            text = keyScale,
                            color = harmonyColor.copy(alpha = 0.70f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            lineHeight = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // ── Identity: Title + Artist ─────────────────────────────────
                val titleColor = appPreferences.titleColorOverride
                    ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                    ?: setColor
                val artistColor = appPreferences.artistColorOverride
                    ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                    ?: encoreColors.artistText
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = titleColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (song.artist != "Unknown Artist") {
                        Text(
                            text = song.artist,
                            color = artistColor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // ── Status Pill: Lead icon + Capo + BPM ──────────────────────
                val showStatusPill = (appPreferences.showLeadIndicator && song.isLeadGuitar)
                    || song.capoEnabled
                    || bpm != null
                if (showStatusPill) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = encoreColors.titleText.copy(alpha = 0.07f),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (appPreferences.showLeadIndicator && song.isLeadGuitar) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_guitar_pick),
                                    contentDescription = "Lead guitar",
                                    tint = leadIconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            if (song.capoEnabled) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${song.capoFret}",
                                        color = capoColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                    Text(
                                        text = "CAPO",
                                        color = capoColor.copy(alpha = 0.65f),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.4.sp,
                                        lineHeight = 8.sp
                                    )
                                }
                            }
                            if (bpm != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$bpm",
                                        color = encoreColors.titleText.copy(alpha = 0.88f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 14.sp
                                    )
                                    Text(
                                        text = "BPM",
                                        color = encoreColors.artistText.copy(alpha = 0.55f),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.4.sp,
                                        lineHeight = 8.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Transposition badge (outside status pill — distinct warning)
                val displayKey = song.displayKey
                val originalKey = song.originalKey
                if (appPreferences.showTranspositionWarning &&
                    displayKey != null && originalKey != null &&
                    displayKey != originalKey) {
                    Text(
                        text = "⚠",
                        color = Color(0xFFFF9F0A),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                // ── 1dp Vertical Divider ─────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.6f)
                        .width(1.dp)
                        .background(encoreColors.divider)
                )

                // ── Control Pill: ☀ ✎ ✕ ────────────────────────────────────
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = encoreColors.titleText.copy(alpha = 0.06f),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        onToggleDarkMode?.let { toggle ->
                            IconButton(onClick = toggle, modifier = Modifier.size(60.dp)) {
                                Icon(
                                    imageVector = if (encoreColors.isDark) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay,
                                    contentDescription = if (encoreColors.isDark) "Light mode" else "Dark mode",
                                    tint = encoreColors.iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        onEditClick?.let { edit ->
                            IconButton(onClick = { edit(song) }, modifier = Modifier.size(60.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit song",
                                    tint = encoreColors.iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        onSaveClick?.let { save ->
                            IconButton(onClick = save, modifier = Modifier.size(60.dp)) {
                                Icon(
                                    imageVector = Icons.Default.SaveAlt,
                                    contentDescription = "Save set",
                                    tint = encoreColors.iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        onLoadClick?.let { load ->
                            IconButton(onClick = load, modifier = Modifier.size(60.dp)) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Load setlist",
                                    tint = encoreColors.iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(onClick = onNavigateBack, modifier = Modifier.size(60.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = encoreColors.titleText.copy(alpha = 0.40f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Save / Load dialogs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Zen-styled dialog for naming and saving the current set.
 */
@Composable
private fun SaveSetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val encoreColors = LocalEncoreColors.current
    var name by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = encoreColors.cardBackground,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, encoreColors.divider, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Save Set",
                    color = encoreColors.titleText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Set name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = encoreColors.titleText.copy(alpha = 0.6f),
                        unfocusedBorderColor = encoreColors.divider,
                        focusedLabelColor = encoreColors.titleText.copy(alpha = 0.6f),
                        unfocusedLabelColor = encoreColors.artistText,
                        focusedTextColor = encoreColors.titleText,
                        unfocusedTextColor = encoreColors.titleText
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = encoreColors.artistText)
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (name.isNotBlank())
                            encoreColors.titleText.copy(alpha = 0.12f)
                        else
                            encoreColors.titleText.copy(alpha = 0.04f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = name.isNotBlank()) { onSave(name.trim()) }
                    ) {
                        Text(
                            text = "Save",
                            color = if (name.isNotBlank()) encoreColors.titleText
                                    else encoreColors.titleText.copy(alpha = 0.30f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Zen-styled dialog for picking a saved setlist to load into the current performance set.
 */
@Composable
private fun LoadSetDialog(
    setlists: List<SetlistEntity>,
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit
) {
    val encoreColors = LocalEncoreColors.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = encoreColors.cardBackground,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, encoreColors.divider, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Load Setlist",
                    color = encoreColors.titleText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(16.dp))
                if (setlists.isEmpty()) {
                    Text(
                        text = "No saved setlists yet. Save the current set first.",
                        color = encoreColors.artistText,
                        fontSize = 14.sp
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        setlists.forEach { setlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onLoad(setlist.id) }
                                    .padding(horizontal = 8.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = setlist.name,
                                    color = encoreColors.titleText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = encoreColors.titleText.copy(alpha = 0.30f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            HorizontalDivider(color = encoreColors.divider, thickness = 0.5.dp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = encoreColors.artistText)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Parser
// ─────────────────────────────────────────────────────────────────────────────

// ChordSidekick / Obsidian Chord Sheets section header:
//   <span style="color: (#FF5733); font-weight: bold;">## Verse 1</span>
// BPM / Tempo metadata: **BPM:** 120  or  **Tempo:** 120  or  Tempo: 120
private val BPM_PATTERN = Regex(
    """(?i)(?:\*\*)?(?:bpm|tempo)(?:\*\*)?\s*:\s*(\d{2,3})""",
    RegexOption.MULTILINE
)

private fun parseBpm(markdown: String): Int? =
    BPM_PATTERN.find(markdown)?.groupValues?.getOrNull(1)?.toIntOrNull()

/**
 * Splits a key string into (root, scale) for the key block.
 *
 * Examples:
 *   "D"       → ("D",  "")
 *   "Dm"      → ("D",  "m")
 *   "C#m"     → ("C#", "m")
 *   "Bb"      → ("Bb", "")
 *   "D Major" → ("D",  "maj")
 *   "F# minor"→ ("F#", "min")
 */
private fun splitKey(displayKey: String?): Pair<String, String> {
    if (displayKey.isNullOrBlank()) return Pair("?", "")
    val lower = displayKey.lowercase().trim()
    return when {
        lower.endsWith(" major") -> Pair(displayKey.dropLast(6).trim(), "maj")
        lower.endsWith(" minor") -> Pair(displayKey.dropLast(6).trim(), "min")
        lower.endsWith("maj")    -> Pair(displayKey.dropLast(3).trim(), "maj")
        lower.endsWith("min")    -> Pair(displayKey.dropLast(3).trim(), "min")
        // Chord-style suffix: Am, Dm, C#m, Bbm — only if the root is non-empty
        lower.endsWith("m") && displayKey.length > 1 -> {
            val root = displayKey.dropLast(1)
            if (root.all { it.isLetter() || it == '#' || it == 'b' })
                Pair(root, "m")
            else
                Pair(displayKey, "")
        }
        else -> Pair(displayKey, "")
    }
}

/**
 * Removes the first non-blank line from the markdown body if it is a title header
 * (`# SongTitle` or `## SongTitle`) that duplicates the song's metadata title.
 *
 * The Dashboard is the single source of truth for the title; rendering it again
 * at the top of the scroll content creates a visual double-header.
 */
private fun stripLeadingTitle(markdown: String, title: String): String {
    val lines = markdown.lines()
    val firstNonBlank = lines.indexOfFirst { it.isNotBlank() }
    if (firstNonBlank == -1) return markdown
    val candidate = lines[firstNonBlank].trim().trimStart('#').trim()
    return if (candidate.equals(title, ignoreCase = true))
        lines.toMutableList().also { it.removeAt(firstNonBlank) }.joinToString("\n")
    else
        markdown
}

private val SPAN_HEADER_PATTERN = Regex(
    """<span style="color: \(?#([A-Fa-f0-9]{6})\)?; font-weight: bold;">##? ?(.*?)</span>"""
)

// Key metadata line:  **Key:** G   or   **Key:** Eb
private val KEY_METADATA_PATTERN = Regex("""^\*\*Key:\*\*\s*(.+)$""")

// YAML front-matter fence
private val FRONTMATTER_FENCE = Regex("""^-{3,}$""")

/**
 * Parse the raw markdown body of a song into a [SongSection] list.
 *
 * Supports two formats:
 *  1. **ChordSidekick** — `**Key:**` metadata, HTML `<span>` section headers,
 *     backtick chord notation `` `[G]` ``
 *  2. **Legacy** — YAML front-matter, `# Markdown` section headers,
 *     bare chord lines (`G  Em  C  D`)
 */
private fun parseSongSections(
    markdown: String,
    preferences: AppPreferences,
    isDark: Boolean = true
): List<SongSection> {
    val result = mutableListOf<SongSection>()
    val bodyBuffer = mutableListOf<String>()
    var keyExtracted = false
    var inFrontMatter = false
    var frontMatterDone = false

    fun flushBody() {
        val text = bodyBuffer.joinToString("\n")
        if (text.isNotBlank()) result.add(SongSection.Body(text))
        bodyBuffer.clear()
    }

    for (line in markdown.lines()) {
        val trimmed = line.trim()

        // ── Skip / consume YAML front-matter ────────────────────────────────
        if (!frontMatterDone) {
            if (FRONTMATTER_FENCE.matches(trimmed)) {
                if (!inFrontMatter) { inFrontMatter = true; continue }
                else { inFrontMatter = false; frontMatterDone = true; continue }
            }
            if (inFrontMatter) continue // skip front-matter content
        }

        // ── HTML span section header (ChordSidekick format) ─────────────────
        val spanMatch = SPAN_HEADER_PATTERN.find(trimmed)
        if (spanMatch != null) {
            flushBody()
            val hexColor = "#${spanMatch.groupValues[1]}"
            val title = spanMatch.groupValues[2].trim()
            result.add(SongSection.Header(text = title, level = 2, color = hexColor))
            continue
        }

        // ── Key metadata (first occurrence only) ────────────────────────────
        if (!keyExtracted) {
            val keyMatch = KEY_METADATA_PATTERN.find(trimmed)
            if (keyMatch != null) {
                flushBody()
                result.add(SongSection.KeyLabel(keyMatch.groupValues[1].trim()))
                keyExtracted = true
                continue
            }
        }

        // ── Legacy markdown `#` section header ──────────────────────────────
        if (trimmed.startsWith("#")) {
            flushBody()
            val level = trimmed.takeWhile { it == '#' }.length
            val headerText = trimmed.drop(level).trimStart()
            val color = if (preferences.showKeyInfo) {
                AppPreferences.getSectionColor(headerText, preferences, isDark)
            } else null
            result.add(SongSection.Header(text = headerText, level = level, color = color))
            continue
        }

        // ── Body line ────────────────────────────────────────────────────────
        // Suppress bare chord lines when showChords is off
        val suppressLine = !preferences.showChords &&
                (isBareChordLine(line) || isPureBacktickChordLine(line))
        if (!suppressLine) {
            bodyBuffer.add(line)
        }
    }

    flushBody()
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// Chord rendering helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Normalise a section header name for lookup in [ViewerPreferences.sectionStyles].
 *
 * "Verse 1" → "verse",  "Pre-Chorus 2" → "pre-chorus",  "Chorus" → "chorus"
 */
private fun normalizeSectionName(name: String): String =
    name.lowercase().trimEnd { it.isDigit() || it == ' ' }.trim()

// Matches backtick-wrapped bracket chords:  `[G]`  `[Am/E]`  `[Bb7]`
private val BACKTICK_CHORD_PATTERN = Regex("""`\[([^\]]+)\]`""")

// Harmony tag: [h]Text[/h]
private val HARMONY_TAG_PATTERN = Regex("""\[h\](.*?)\[/h\]""")

// Technical note: *(text)*  — single-asterisk wrapping (not bold markdown **)
private val TECH_NOTE_PATTERN = Regex("""\*([^*]+)\*""")

private enum class SpanType { CHORD, HARMONY, TECH_NOTE }
private data class BodySpan(val range: IntRange, val type: SpanType, val text: String)

// Fallback accent when no set context
/** Parses a hex color string (e.g. "#FF9F0A") into a [Color], falling back to [Color.Gray]. */
private fun parseColorSafe(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color.Gray
}

private val DEFAULT_CHORD_COLOR = Color(0xFF3B82F6) // Blue

/**
 * Build an [AnnotatedString] for a single body line.
 *
 * - Strips Markdown bold (`**`) markers.
 * - `` `[Chord]` `` segments → [chordColor] (brackets visible, backticks stripped).
 * - Legacy bare chord lines → entire line in [chordColor].
 * - `[h]Text[/h]` → Bold + [harmonyColor] text + background highlight (harmony annotation).
 * - `*(text)*` → subtle grey italic (technical/director note).
 * - Lyric text → [lyricColor].
 */
private fun buildChordLine(
    rawLine: String,
    chordColor: Color?,
    lyricColor: Color,
    harmonyColor: Color = Color(0xFFFF9F0A),
    isHarmonyLine: Boolean = false
): AnnotatedString {
    val effectiveChordColor = chordColor ?: DEFAULT_CHORD_COLOR
    val effectiveLyricColor = if (isHarmonyLine) harmonyColor else lyricColor
    val harmonyBg = if (isHarmonyLine) harmonyColor.copy(alpha = 0.18f) else Color.Unspecified

    // Strip markdown bold markers so `**Key:**` lines don't show asterisks
    val line = rawLine.replace("**", "")

    // Entire line is a legacy bare chord line → colour it whole
    if (isBareChordLine(line)) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = effectiveChordColor)) { append(line) }
        }
    }

    // Collect all tagged spans in document order so we do one linear pass
    val spans = mutableListOf<BodySpan>()
    BACKTICK_CHORD_PATTERN.findAll(line).forEach {
        spans += BodySpan(it.range, SpanType.CHORD, it.groupValues[1])
    }
    HARMONY_TAG_PATTERN.findAll(line).forEach {
        spans += BodySpan(it.range, SpanType.HARMONY, it.groupValues[1])
    }
    TECH_NOTE_PATTERN.findAll(line).forEach {
        spans += BodySpan(it.range, SpanType.TECH_NOTE, it.groupValues[1])
    }

    // If no special spans, plain lyric line (harmony highlight applied if in block)
    if (spans.isEmpty()) {
        return buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = effectiveLyricColor,
                    background = harmonyBg,
                    fontWeight = if (isHarmonyLine) FontWeight.Bold else null
                )
            ) { append(line) }
        }
    }

    spans.sortBy { it.range.first }

    return buildAnnotatedString {
        var cursor = 0
        for (span in spans) {
            if (span.range.first < cursor) continue // overlapping — skip
            if (span.range.first > cursor) {
                withStyle(
                    SpanStyle(
                        color = effectiveLyricColor,
                        background = harmonyBg,
                        fontWeight = if (isHarmonyLine) FontWeight.Bold else null
                    )
                ) {
                    append(line.substring(cursor, span.range.first))
                }
            }
            when (span.type) {
                SpanType.CHORD -> withStyle(SpanStyle(color = effectiveChordColor)) {
                    append("[${span.text}]")
                }
                SpanType.HARMONY -> withStyle(
                    SpanStyle(
                        color = harmonyColor,
                        background = harmonyColor.copy(alpha = 0.22f),
                        fontWeight = FontWeight.Bold
                    )
                ) { append(span.text) }
                SpanType.TECH_NOTE -> withStyle(
                    SpanStyle(
                        color = effectiveLyricColor.copy(alpha = 0.50f),
                        fontStyle = FontStyle.Italic
                    )
                ) { append("(${span.text})") }
            }
            cursor = span.range.last + 1
        }
        if (cursor < line.length) {
            withStyle(
                SpanStyle(
                    color = effectiveLyricColor,
                    background = harmonyBg,
                    fontWeight = if (isHarmonyLine) FontWeight.Bold else null
                )
            ) { append(line.substring(cursor)) }
        }
    }
}

/**
 * Returns true for a legacy bare chord line: only chord names and whitespace.
 * Example: `G  Em  C  D`
 */
private fun isBareChordLine(line: String): Boolean {
    val pattern = Regex("""^[\s|]*([A-G][#b]?(m|maj|min|aug|dim|sus|add)?[0-9]?[/\s|]*)+$""")
    return line.isNotBlank() && pattern.matches(line.trim())
}

/**
 * Returns true when a line contains only backtick chords and whitespace —
 * i.e., it is a chord-only line with no lyric content.
 */
private fun isPureBacktickChordLine(line: String): Boolean {
    val stripped = BACKTICK_CHORD_PATTERN.replace(line, "").trim()
    return stripped.isEmpty() && line.isNotBlank()
}

// ─────────────────────────────────────────────────────────────────────────────
// Zoom HUD
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Floating zoom HUD (semi-transparent, bottom-right).
 * Appears on single tap, auto-fades after 3 s.
 */
@Composable
fun FloatingZoomControls(
    currentZoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onZoomOut, enabled = currentZoom > 0.5f) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${(currentZoom * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onZoomIn, enabled = currentZoom < 3.0f) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
