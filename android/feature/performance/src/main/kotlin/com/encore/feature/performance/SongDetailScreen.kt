package com.encore.feature.performance

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.encore.core.data.preferences.DisplayPreferences
import com.encore.core.data.preferences.DisplayPreferencesHolder
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import kotlinx.coroutines.delay

/**
 * Song Detail Screen - Full-Screen Performance Mode.
 *
 * Features:
 * - Full-screen layout with no app chrome (no top/bottom bars)
 * - Tap-to-reveal floating zoom controls (bottom-right)
 * - Double-tap to reset zoom to 100%
 * - Pinch-to-zoom for text size adjustment
 * - Per-song zoom level persistence
 * - Section headers rendered with design-spec colors
 * - Auto-hide controls after 3 seconds
 *
 * Milestone 3: Performance Engine - Production Mode
 */
@Composable
fun SongDetailScreen(
    viewModel: SongDetailViewModel,
    songId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song by viewModel.song.collectAsState()
    val textSizeMultiplier by viewModel.textSizeMultiplier.collectAsState()

    var showControls by remember { mutableStateOf(false) }

    LaunchedEffect(songId) {
        viewModel.loadSong(songId)
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    BackHandler {
        onNavigateBack()
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            song == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                SongContent(
                    song = song!!,
                    scrollState = scrollState,
                    textSizeMultiplier = textSizeMultiplier,
                    onZoomChange = { multiplier ->
                        viewModel.updateTextSize(multiplier)
                    },
                    onSingleTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = {
                        viewModel.resetTextSize()
                        showControls = true
                    }
                )

                // Persistent back button — top-left, always visible, subtle
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Back to library",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Floating zoom HUD (bottom-right corner) — appears on single tap, fades after 3s
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

/**
 * Song content with section-based colored headers, pinch-to-zoom, and tap gestures.
 *
 * Section headers (Intro, Verse, Chorus, etc.) are rendered as Compose Text with
 * their design-spec hex colors from DisplayPreferences. Body content is rendered
 * with the Markdown library. This avoids relying on HTML <span> tag rendering support.
 */
@Composable
fun SongContent(
    song: com.encore.core.data.entities.SongEntity,
    scrollState: androidx.compose.foundation.ScrollState,
    textSizeMultiplier: Float,
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

    LaunchedEffect(textSizeMultiplier) {
        currentZoom = textSizeMultiplier
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Pinch-to-zoom: separate key so both gesture detectors coexist
            .pointerInput("transform") {
                detectTransformGestures { _, _, zoom, _ ->
                    currentZoom = (currentZoom * zoom).coerceIn(0.5f, 3.0f)
                    onZoomChange(currentZoom)
                }
            }
            // Tap detection: uses onDoubleTap directly (correct API usage)
            .pointerInput("tap") {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onTap = { onSingleTap() }
                )
            }
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        sections.forEachIndexed { index, section ->
            when (section) {
                is SongSection.Header -> {
                    val color = section.color?.let {
                        try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
                    } ?: MaterialTheme.colorScheme.primary

                    Text(
                        text = section.text,
                        color = color,
                        fontSize = when (section.level) {
                            1 -> (28f * textSizeMultiplier).sp
                            2 -> (24f * textSizeMultiplier).sp
                            else -> (20f * textSizeMultiplier).sp
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            top = if (index > 0) 16.dp else 0.dp,
                            bottom = 4.dp
                        )
                    )
                }
                is SongSection.Body -> {
                    if (section.markdown.isNotBlank()) {
                        Markdown(
                            content = section.markdown,
                            colors = DefaultMarkdownColors(
                                text = MaterialTheme.colorScheme.onBackground,
                                codeText = MaterialTheme.colorScheme.onBackground,
                                linkText = MaterialTheme.colorScheme.primary,
                                codeBackground = MaterialTheme.colorScheme.surfaceVariant,
                                inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant,
                                dividerColor = MaterialTheme.colorScheme.outline
                            ),
                            typography = DefaultMarkdownTypography(
                                text = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * textSizeMultiplier).sp,
                                    lineHeight = (MaterialTheme.typography.bodyLarge.lineHeight.value * textSizeMultiplier).sp
                                ),
                                code = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * textSizeMultiplier).sp
                                ),
                                quote = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (MaterialTheme.typography.bodyMedium.fontSize.value * textSizeMultiplier).sp
                                ),
                                paragraph = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * textSizeMultiplier).sp,
                                    lineHeight = (MaterialTheme.typography.bodyLarge.lineHeight.value * textSizeMultiplier).sp
                                ),
                                ordered = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * textSizeMultiplier).sp
                                ),
                                bullet = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * textSizeMultiplier).sp
                                ),
                                list = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * textSizeMultiplier).sp
                                ),
                                h1 = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = (MaterialTheme.typography.headlineLarge.fontSize.value * textSizeMultiplier).sp
                                ),
                                h2 = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = (MaterialTheme.typography.headlineMedium.fontSize.value * textSizeMultiplier).sp
                                ),
                                h3 = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = (MaterialTheme.typography.headlineSmall.fontSize.value * textSizeMultiplier).sp
                                ),
                                h4 = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = (MaterialTheme.typography.titleLarge.fontSize.value * textSizeMultiplier).sp
                                ),
                                h5 = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = (MaterialTheme.typography.titleMedium.fontSize.value * textSizeMultiplier).sp
                                ),
                                h6 = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = (MaterialTheme.typography.titleSmall.fontSize.value * textSizeMultiplier).sp
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * A parsed segment of a song — either a colored section header or a block of body content.
 */
sealed class SongSection {
    data class Header(val text: String, val level: Int, val color: String?) : SongSection()
    data class Body(val markdown: String) : SongSection()
}

/**
 * Parse markdown into a flat list of [SongSection]s.
 *
 * Each `# Header` line becomes a [SongSection.Header] with its design-spec color.
 * Body lines between headers are grouped into [SongSection.Body] blocks.
 * Chord lines are filtered out when [DisplayPreferences.showChords] is false.
 */
private fun parseSongSections(
    markdown: String,
    preferences: DisplayPreferences
): List<SongSection> {
    val result = mutableListOf<SongSection>()
    val bodyBuffer = mutableListOf<String>()

    fun flushBody() {
        val bodyMarkdown = bodyBuffer.joinToString("\n")
        if (bodyMarkdown.isNotBlank()) {
            result.add(SongSection.Body(bodyMarkdown))
        }
        bodyBuffer.clear()
    }

    for (line in markdown.lines()) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("#")) {
            flushBody()

            val level = trimmed.takeWhile { it == '#' }.length
            val headerText = trimmed.drop(level).trimStart()
            val color = if (preferences.showKeyInfo) {
                DisplayPreferences.getSectionColor(headerText, preferences)
            } else null

            result.add(SongSection.Header(text = headerText, level = level, color = color))
        } else {
            // Filter chord lines when showChords is off
            if (preferences.showChords || !isChordLine(line)) {
                bodyBuffer.add(line)
            }
        }
    }

    flushBody()
    return result
}

/**
 * Floating zoom HUD (semi-transparent overlay, bottom-right).
 * Appears on single tap, auto-fades after 3s.
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
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onZoomOut,
                enabled = currentZoom > 0.5f
            ) {
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

            IconButton(
                onClick = onZoomIn,
                enabled = currentZoom < 3.0f
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Detect if a line is a chord line (heuristic: contains chord notation).
 */
private fun isChordLine(line: String): Boolean {
    val chordPattern = """^[\s\|]*([A-G][#b]?m?[\s\|]*)+$""".toRegex()
    return chordPattern.matches(line.trim())
}
