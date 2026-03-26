package com.encore.tablet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.encore.core.ui.markdown.MarkdownRenderer
import com.encore.core.ui.markdown.ParsedSong
import com.encore.core.ui.markdown.SongParser
import java.io.IOException

/**
 * Main entry point for Encore Android tablet application.
 *
 * Milestone 1 - Foundation: Demonstrates markdown parser spike with sample song.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EncoreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ParserSpikeScreen()
                }
            }
        }
    }
}

/**
 * Milestone 1 Parser Spike Screen
 *
 * Loads and displays the sample song (Amazing Grace) using the markdown renderer.
 * Tests:
 * - YAML front matter parsing
 * - Chord-over-lyric alignment
 * - Monospace font rendering
 * - Section headings
 */
@Composable
fun ParserSpikeScreen() {
    val context = LocalContext.current
    var parsedSong by remember { mutableStateOf<ParsedSong?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Load sample song from assets
    LaunchedEffect(Unit) {
        try {
            val markdown = context.assets.open("songs/sample-song-01.md")
                .bufferedReader()
                .use { it.readText() }
            parsedSong = SongParser.parse(markdown)
        } catch (e: IOException) {
            error = "Failed to load sample song: ${e.message}"
        }
    }

    when {
        error != null -> {
            ErrorScreen(error = error!!)
        }
        parsedSong != null -> {
            SongDisplayScreen(song = parsedSong!!)
        }
        else -> {
            LoadingScreen()
        }
    }
}

@Composable
fun SongDisplayScreen(song: ParsedSong) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Song metadata header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
                song.key?.let {
                    Text(
                        text = "Key: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = "Milestone 1: Parser Spike Test",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Markdown-rendered song body
        MarkdownRenderer(
            markdown = song.markdownBody,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Loading sample song...",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ErrorScreen(error: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
fun EncoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

@Preview(
    showBackground = true,
    widthDp = 800,
    heightDp = 1280,
    name = "11-inch Tablet Portrait"
)
@Composable
fun SongDisplayPreview() {
    EncoreTheme {
        SongDisplayScreen(
            song = ParsedSong(
                title = "Amazing Grace",
                artist = "John Newton",
                key = "G",
                markdownBody = "# Verse 1\n\n    G              C         G\nAmazing grace, how sweet the sound",
                fullMarkdown = ""
            )
        )
    }
}
