package com.encore.feature.performance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Song Detail Screen - Performance / Teleprompter Mode.
 *
 * Features:
 * - Markdown rendering with HTML <span> tag support for colored sections
 * - Auto-scroll engine based on Duration metadata (default 3 minutes)
 * - Pinch-to-zoom for text size adjustment
 * - Play/pause controls
 * - Zoom in/out buttons
 *
 * Milestone 3: Performance Engine
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    viewModel: SongDetailViewModel,
    songId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song by viewModel.song.collectAsState()
    val isAutoScrolling by viewModel.isAutoScrolling.collectAsState()
    val textSizeMultiplier by viewModel.textSizeMultiplier.collectAsState()
    val scrollSpeedPxPerSecond by viewModel.scrollSpeedPxPerSecond.collectAsState()

    // Load song on first composition
    LaunchedEffect(songId) {
        viewModel.loadSong(songId)
    }

    // Scroll state for manual and auto-scrolling
    val scrollState = rememberScrollState()

    // Auto-scroll effect
    LaunchedEffect(isAutoScrolling, scrollSpeedPxPerSecond) {
        if (isAutoScrolling && scrollSpeedPxPerSecond > 0) {
            while (isActive && scrollState.value < scrollState.maxValue) {
                val frameTime = 16 // ~60fps
                val scrollAmount = (scrollSpeedPxPerSecond * frameTime / 1000f).toInt()
                scrollState.scrollTo(scrollState.value + scrollAmount)
                delay(frameTime.toLong())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = song?.title ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        song?.let {
                            Text(
                                text = "${it.artist} • Key: ${it.currentKey ?: "Unknown"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Zoom out button
                    IconButton(
                        onClick = {
                            viewModel.updateTextSize(textSizeMultiplier - 0.1f)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Zoom Out"
                        )
                    }

                    // Zoom level indicator
                    Text(
                        text = "${(textSizeMultiplier * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Zoom in button
                    IconButton(
                        onClick = {
                            viewModel.updateTextSize(textSizeMultiplier + 0.1f)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom In"
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Auto-scroll toggle
                    IconButton(
                        onClick = { viewModel.toggleAutoScroll() }
                    ) {
                        Icon(
                            imageVector = if (isAutoScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isAutoScrolling) "Pause Auto-Scroll" else "Start Auto-Scroll",
                            tint = if (isAutoScrolling) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                song == null -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    // Song content with pinch-to-zoom
                    SongContent(
                        song = song!!,
                        scrollState = scrollState,
                        textSizeMultiplier = textSizeMultiplier,
                        onZoomChange = { multiplier ->
                            viewModel.updateTextSize(multiplier)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Song content with markdown rendering and pinch-to-zoom.
 */
@Composable
fun SongContent(
    song: com.encore.core.data.entities.SongEntity,
    scrollState: androidx.compose.foundation.ScrollState,
    textSizeMultiplier: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentZoom by remember { mutableFloatStateOf(textSizeMultiplier) }

    // Update currentZoom when textSizeMultiplier changes externally (button clicks)
    LaunchedEffect(textSizeMultiplier) {
        currentZoom = textSizeMultiplier
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    // Apply zoom transformation
                    currentZoom = (currentZoom * zoom).coerceIn(0.5f, 3.0f)
                    onZoomChange(currentZoom)
                }
            }
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Song metadata card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize * textSizeMultiplier
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * textSizeMultiplier
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
                song.currentKey?.let { key ->
                    Text(
                        text = "Key: $key",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * textSizeMultiplier
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // Markdown content
        // Note: The mikepenz markdown renderer automatically handles HTML tags including <span>
        Markdown(
            content = song.markdownBody,
            colors = DefaultMarkdownColors(
                text = MaterialTheme.colorScheme.onSurface,
                codeText = MaterialTheme.colorScheme.onSurface,
                linkText = MaterialTheme.colorScheme.primary,
                codeBackground = MaterialTheme.colorScheme.surfaceVariant,
                inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant,
                dividerColor = MaterialTheme.colorScheme.outline
            ),
            typography = DefaultMarkdownTypography(
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
                ),
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
                    fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * textSizeMultiplier).sp
                ),
                ordered = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * textSizeMultiplier).sp
                ),
                bullet = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * textSizeMultiplier).sp
                ),
                list = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value * textSizeMultiplier).sp
                )
            )
        )
    }
}
