package com.encore.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.encore.core.ui.theme.LocalEncoreColors

// ─────────────────────────────────────────────────────────────────────────────
// Visual transformation — live [h]...[/h] highlight in the editor
// ─────────────────────────────────────────────────────────────────────────────

private val EDITOR_HARMONY_PATTERN = Regex("""\[h\](.*?)\[/h\]""", RegexOption.DOT_MATCHES_ALL)

private val HarmonyHighlightTransformation = VisualTransformation { text ->
    val annotated = buildAnnotatedString {
        append(text)
        EDITOR_HARMONY_PATTERN.findAll(text.text).forEach { match ->
            addStyle(
                SpanStyle(background = Color(0xFFFF9F0A).copy(alpha = 0.15f)),
                match.range.first,
                match.range.last + 1
            )
            match.groups[1]?.range?.let { inner ->
                addStyle(
                    SpanStyle(color = Color(0xFFFF9F0A), fontWeight = FontWeight.SemiBold),
                    inner.first,
                    inner.last + 1
                )
            }
        }
    }
    TransformedText(annotated, OffsetMapping.Identity)
}

// ─────────────────────────────────────────────────────────────────────────────
// Cursor context — what is the cursor currently on?
// ─────────────────────────────────────────────────────────────────────────────

// Matches `[ChordName]` backtick notation
private val CHORD_PATTERN = Regex("""`\[([^\]]+)\]`""")

private sealed class CursorContext {
    object Default : CursorContext()
    /** Cursor is inside a `[Chord]` span. */
    data class InChord(val chord: String, val matchRange: IntRange) : CursorContext()
    /** Cursor is on a line starting with one or more `#`. */
    data class InSection(val headerText: String, val lineStart: Int, val hashes: String) : CursorContext()
}

private fun detectContext(fieldValue: TextFieldValue): CursorContext {
    val cursor = fieldValue.selection.min
    val text = fieldValue.text

    // Current line bounds
    val lineStart = (text.lastIndexOf('\n', cursor - 1) + 1).coerceAtLeast(0)
    val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
    val currentLine = text.substring(lineStart, lineEnd)
    val trimmed = currentLine.trimStart()

    // Section header?
    if (trimmed.startsWith("#")) {
        val hashes = trimmed.takeWhile { it == '#' }
        val headerText = trimmed.drop(hashes.length).trimStart()
        return CursorContext.InSection(headerText, lineStart, hashes)
    }

    // Inside a `[chord]` span?
    for (match in CHORD_PATTERN.findAll(text)) {
        if (cursor in match.range.first..(match.range.last + 1)) {
            return CursorContext.InChord(match.groupValues[1], match.range)
        }
    }

    return CursorContext.Default
}

// ─────────────────────────────────────────────────────────────────────────────
// Cursor-safe insertion helpers
// ─────────────────────────────────────────────────────────────────────────────

internal fun TextFieldValue.wrapSelection(prefix: String, suffix: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val inner = text.substring(start, end)
    val newText = text.substring(0, start) + prefix + inner + suffix + text.substring(end)
    val newCursor = if (selection.collapsed) {
        TextRange(start + prefix.length)
    } else {
        TextRange(start + prefix.length + inner.length + suffix.length)
    }
    return copy(text = newText, selection = newCursor)
}

internal fun TextFieldValue.prependLine(prefix: String): TextFieldValue {
    val lineStart = text.lastIndexOf('\n', (selection.min - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    return copy(
        text = newText,
        selection = TextRange(selection.start + prefix.length, selection.end + prefix.length)
    )
}

// Replace a chord in-place, keeping cursor at end of new chord
private fun TextFieldValue.replaceChord(matchRange: IntRange, newChord: String): TextFieldValue {
    val replacement = "`[$newChord]`"
    val newText = text.substring(0, matchRange.first) + replacement + text.substring(matchRange.last + 1)
    val newCursor = matchRange.first + replacement.length
    return copy(text = newText, selection = TextRange(newCursor))
}

// Replace header text on the current line, keeping cursor at end
private fun TextFieldValue.replaceHeader(lineStart: Int, hashes: String, newText: String): TextFieldValue {
    val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
    val replacement = "$hashes $newText"
    val updatedText = text.substring(0, lineStart) + replacement + text.substring(lineEnd)
    val newCursor = lineStart + replacement.length
    return copy(text = updatedText, selection = TextRange(newCursor))
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongChartEditorScreen(
    songId: String,
    viewModel: LibraryViewModel,
    onNavigateBack: () -> Unit
) {
    val encoreColors = LocalEncoreColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val songFlow = remember(songId) { viewModel.getSongFlow(songId) }
    val song by songFlow.collectAsState(initial = null)

    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var initialized by remember { mutableStateOf(false) }
    var lastNonCollapsedSelection by remember { mutableStateOf(TextRange.Zero) }

    LaunchedEffect(song?.id) {
        if (song != null && !initialized) {
            fieldValue = TextFieldValue(song!!.markdownBody)
            initialized = true
        }
    }

    var isDirty by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val context by remember { derivedStateOf { detectContext(fieldValue) } }

    // Inline edit state — lifted here so TopAppBar actions can trigger it
    var inlineEditActive by remember(context) { mutableStateOf(false) }
    var inlineEditValue by remember(context) {
        val ctx = context
        mutableStateOf(
            when (ctx) {
                is CursorContext.InChord -> ctx.chord
                is CursorContext.InSection -> ctx.headerText
                else -> ""
            }
        )
    }

    Scaffold(
        containerColor = encoreColors.screenBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = song?.title ?: "Edit Chart",
                        fontWeight = FontWeight.SemiBold,
                        color = encoreColors.titleText
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = encoreColors.iconTint
                        )
                    }
                },
                actions = {
                    // [h] Harmony — always available
                    FormatButton(
                        label = "[h]",
                        isAccent = true,
                        onClick = {
                            val sel = if (!fieldValue.selection.collapsed) fieldValue.selection
                                      else lastNonCollapsedSelection
                            fieldValue = fieldValue.copy(selection = sel).wrapSelection("[h]", "[/h]")
                            isDirty = true
                        }
                    )
                    // Edit Chord — when cursor is inside a chord
                    if (context is CursorContext.InChord) {
                        FormatButton(
                            label = "Chord",
                            onClick = { inlineEditActive = true }
                        )
                    }
                    // Edit Section — when cursor is on a header line
                    if (context is CursorContext.InSection) {
                        FormatButton(
                            label = "Section",
                            onClick = { inlineEditActive = true }
                        )
                    }
                    if (isDirty) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                song?.let { fieldValue = TextFieldValue(it.markdownBody) }
                                isDirty = false
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ) {
                            Text("Cancel", color = encoreColors.artistText)
                        }
                        Button(
                            onClick = {
                                song?.let { viewModel.updateMarkdownBody(it.id, fieldValue.text) }
                                isDirty = false
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = encoreColors.screenBackground
                )
            )
        }
    ) { padding ->
        // Editor fills full height — keyboard overlays content (scrolls behind it).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Scrollable editor
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(encoreColors.screenBackground)
                    .verticalScroll(scrollState)
            ) {
                BasicTextField(
                    value = fieldValue,
                    onValueChange = {
                        if (!it.selection.collapsed) lastNonCollapsedSelection = it.selection
                        fieldValue = it
                        isDirty = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = encoreColors.titleText
                    ),
                    visualTransformation = HarmonyHighlightTransformation,
                    cursorBrush = SolidColor(encoreColors.titleText),
                    decorationBox = { innerTextField -> innerTextField() }
                )
            }

            // Inline edit panel floats above keyboard when active
            if (inlineEditActive) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    InlineEditPanel(
                        context = context,
                        inlineEditValue = inlineEditValue,
                        onInlineValueChange = { inlineEditValue = it },
                        onConfirm = {
                            val updated = when (val ctx = context) {
                                is CursorContext.InChord ->
                                    fieldValue.replaceChord(ctx.matchRange, inlineEditValue.trim())
                                is CursorContext.InSection ->
                                    fieldValue.replaceHeader(ctx.lineStart, ctx.hashes, inlineEditValue.trim())
                                else -> fieldValue
                            }
                            fieldValue = updated
                            isDirty = true
                            inlineEditActive = false
                        },
                        onDismiss = { inlineEditActive = false }
                    )
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline edit panel (shown above keyboard for chord/section editing)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InlineEditPanel(
    context: CursorContext,
    inlineEditValue: String,
    onInlineValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val encoreColors = LocalEncoreColors.current
    val label = when (context) {
        is CursorContext.InChord -> "Chord"
        is CursorContext.InSection -> "Section"
        else -> ""
    }

    Surface(color = encoreColors.cardBackground) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = encoreColors.titleText.copy(alpha = 0.10f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$label:",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = encoreColors.artistText
                    )
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = encoreColors.screenBackground,
                    border = BorderStroke(1.dp, encoreColors.titleText.copy(alpha = 0.20f)),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = inlineEditValue,
                        onValueChange = onInlineValueChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = encoreColors.titleText
                        ),
                        cursorBrush = SolidColor(encoreColors.titleText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        decorationBox = { innerTextField -> innerTextField() }
                    )
                }
                IconButton(onClick = onConfirm, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = Color(0xFF4CD964),
                        modifier = Modifier.size(20.dp)
                    )
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text("✕", color = encoreColors.artistText, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun FormatButton(
    label: String,
    enabled: Boolean = true,
    isAccent: Boolean = false,
    onClick: () -> Unit
) {
    val encoreColors = LocalEncoreColors.current
    val accentColor = Color(0xFFFF9F0A)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isAccent) accentColor else encoreColors.titleText,
            disabledContentColor = encoreColors.titleText.copy(alpha = 0.25f)
        ),
        border = BorderStroke(
            1.dp,
            when {
                !enabled -> encoreColors.titleText.copy(alpha = 0.12f)
                isAccent -> accentColor.copy(alpha = 0.50f)
                else -> encoreColors.titleText.copy(alpha = 0.25f)
            }
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
