package com.encore.feature.performance

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.preferences.DisplayPreferences
import com.encore.core.data.preferences.DisplayPreferencesHolder
import com.encore.core.ui.theme.SetColor
import kotlin.math.abs
import kotlinx.coroutines.delay

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
@Composable
fun SongDetailScreen(
    viewModel: SongDetailViewModel,
    songId: String,
    setNumber: Int = -1,
    onNavigateBack: () -> Unit,
    onNavigateToSong: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val song by viewModel.song.collectAsState()
    val textSizeMultiplier by viewModel.textSizeMultiplier.collectAsState()
    val prevSongId by viewModel.prevSongId.collectAsState()
    val nextSongId by viewModel.nextSongId.collectAsState()

    var showControls by remember { mutableStateOf(false) }

    // Keep screen on during performance
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
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

    val scrollState = rememberScrollState()
    val chordAccentColor = if (setNumber > 0) SetColor.getSetColor(setNumber) else null

    // Stable refs inside pointerInput lambdas
    val latestPrevSongId by rememberUpdatedState(prevSongId)
    val latestNextSongId by rememberUpdatedState(nextSongId)
    val latestOnNavigateToSong by rememberUpdatedState(onNavigateToSong)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput("swipe") {
                var accumX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accumX = 0f },
                    onDragEnd = {
                        if (abs(accumX) > 80.dp.toPx()) {
                            if (accumX > 0) {
                                latestPrevSongId?.let { latestOnNavigateToSong?.invoke(it) }
                            } else {
                                latestNextSongId?.let { latestOnNavigateToSong?.invoke(it) }
                            }
                        }
                        accumX = 0f
                    }
                ) { _, dragAmount -> accumX += dragAmount }
            }
    ) {
        when {
            song == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            else -> {
                // ── Scrollable song body ─────────────────────────────────────
                SongContent(
                    song = song!!,
                    scrollState = scrollState,
                    textSizeMultiplier = textSizeMultiplier,
                    chordAccentColor = chordAccentColor,
                    onZoomChange = { viewModel.updateTextSize(it) },
                    onSingleTap = { showControls = !showControls },
                    onDoubleTap = { viewModel.resetTextSize(); showControls = true }
                )

                // ── Slim header (✕ + title + artist) ────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.80f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Back",
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song!!.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (song!!.artist != "Unknown Artist") {
                            Text(
                                text = song!!.artist,
                                color = Color.White.copy(alpha = 0.50f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // ── Prev song arrow ──────────────────────────────────────────
                if (prevSongId != null && onNavigateToSong != null) {
                    IconButton(
                        onClick = { onNavigateToSong.invoke(prevSongId!!) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous song",
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // ── Next song arrow ──────────────────────────────────────────
                if (nextSongId != null && onNavigateToSong != null) {
                    IconButton(
                        onClick = { onNavigateToSong.invoke(nextSongId!!) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next song",
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // ── Floating zoom HUD ────────────────────────────────────────
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    FloatingZoomControls(
                        currentZoom = textSizeMultiplier,
                        onZoomIn = {
                            viewModel.updateTextSize(textSizeMultiplier + 0.1f)
                            showControls = true
                        },
                        onZoomOut = {
                            viewModel.updateTextSize(textSizeMultiplier - 0.1f)
                            showControls = true
                        }
                    )
                }
            }
        }
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
    modifier: Modifier = Modifier
) {
    var currentZoom by remember { mutableFloatStateOf(textSizeMultiplier) }
    val displayPreferences = remember { DisplayPreferencesHolder.getPreferences() }
    val sections = remember(song.markdownBody, displayPreferences) {
        parseSongSections(song.markdownBody, displayPreferences)
    }
    val vp = remember { ViewerPreferences() }

    LaunchedEffect(textSizeMultiplier) { currentZoom = textSizeMultiplier }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput("transform") {
                detectTransformGestures { _, _, zoom, _ ->
                    currentZoom = (currentZoom * zoom).coerceIn(0.5f, 3.0f)
                    onZoomChange(currentZoom)
                }
            }
            .pointerInput("tap") {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { onSingleTap() }
                )
            }
            .verticalScroll(scrollState)
            // top padding clears the slim header (~48 dp) + small gap
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 52.dp)
    ) {
        sections.forEachIndexed { index, section ->
            when (section) {
                // ── Key badge ────────────────────────────────────────────────
                is SongSection.KeyLabel -> {
                    Text(
                        text = "Key  ${section.key}",
                        color = chordAccentColor ?: Color.White.copy(alpha = vp.keyBadgeAlpha),
                        fontSize = (vp.keyBadgeFontSizeSp * textSizeMultiplier).sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // ── Section header (HTML span or legacy # markdown) ──────────
                is SongSection.Header -> {
                    // Priority: SectionStyle map (configurable) → HTML span color → fallback
                    val styleEntry = vp.sectionStyles[normalizeSectionName(section.text)]

                    val headerColor = styleEntry?.color
                        ?: section.color?.let {
                            try { Color(android.graphics.Color.parseColor(it)) }
                            catch (_: Exception) { null }
                        }
                        ?: (chordAccentColor ?: MaterialTheme.colorScheme.primary)

                    val headerFontSizeSp = styleEntry?.fontSize?.value
                        ?: when (section.level) {
                            1    -> vp.h1FontSizeSp
                            2    -> vp.h2FontSizeSp
                            else -> vp.hnFontSizeSp
                        }

                    Text(
                        text = section.text,
                        color = headerColor,
                        fontSize = (headerFontSizeSp * textSizeMultiplier).sp,
                        fontWeight = if (styleEntry?.isBold != false) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(
                            top = if (index > 0) vp.sectionTopPaddingDp.dp else 0.dp,
                            bottom = vp.sectionBottomPaddingDp.dp
                        )
                    )
                }

                // ── Body block (lyrics + chords) ─────────────────────────────
                is SongSection.Body -> {
                    if (section.markdown.isNotBlank()) {
                        section.markdown.lines().forEach { rawLine ->
                            if (rawLine.isBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                            } else {
                                Text(
                                    text = buildChordLine(
                                        rawLine,
                                        chordAccentColor,
                                        Color.White.copy(alpha = vp.lyricAlpha)
                                    ),
                                    fontSize = (vp.bodyFontSizeSp * textSizeMultiplier).sp,
                                    lineHeight = (vp.lineHeightSp * textSizeMultiplier).sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
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
    preferences: DisplayPreferences
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
                DisplayPreferences.getSectionColor(headerText, preferences)
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

// Fallback accent when no set context
private val DEFAULT_CHORD_COLOR = Color(0xFF3B82F6) // Blue

/**
 * Build an [AnnotatedString] for a single body line.
 *
 * - Strips Markdown bold (`**`) markers.
 * - `` `[Chord]` `` segments → [chordColor] (brackets visible, backticks stripped).
 * - Legacy bare chord lines → entire line in [chordColor].
 * - Lyric text → [lyricColor].
 */
private fun buildChordLine(
    rawLine: String,
    chordColor: Color?,
    lyricColor: Color
): AnnotatedString {
    val effectiveChordColor = chordColor ?: DEFAULT_CHORD_COLOR

    // Strip markdown bold markers so `**Key:**` lines don't show asterisks
    val line = rawLine.replace("**", "")

    // Entire line is a legacy bare chord line → colour it whole
    if (isBareChordLine(line)) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = effectiveChordColor)) { append(line) }
        }
    }

    // No backtick chords → plain lyric line
    if (!BACKTICK_CHORD_PATTERN.containsMatchIn(line)) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = lyricColor)) { append(line) }
        }
    }

    // Mixed or chord-only line with backtick notation
    return buildAnnotatedString {
        var cursor = 0
        for (match in BACKTICK_CHORD_PATTERN.findAll(line)) {
            // Lyric text before this chord
            if (match.range.first > cursor) {
                withStyle(SpanStyle(color = lyricColor)) {
                    append(line.substring(cursor, match.range.first))
                }
            }
            // Chord text — brackets kept, backticks stripped
            withStyle(SpanStyle(color = effectiveChordColor)) {
                append("[${match.groupValues[1]}]")
            }
            cursor = match.range.last + 1
        }
        // Remaining lyric text after the last chord
        if (cursor < line.length) {
            withStyle(SpanStyle(color = lyricColor)) { append(line.substring(cursor)) }
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
