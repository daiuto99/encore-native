package com.encore.tablet.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.encore.core.data.preferences.AppPreferences
import com.encore.core.data.preferences.SectionStyle
import com.encore.core.data.preferences.SongFontFamily
import com.encore.core.ui.theme.LocalEncoreColors
import com.encore.tablet.preferences.AppPreferencesViewModel
import kotlin.math.roundToInt

private val COLOR_SWATCHES = listOf(
    "#3882F6", "#F97316", "#EF4444", "#885CF6",
    "#F59E0B", "#10B981", "#06B6D4", "#EC4899",
    "#64748B", "#D1D5DB"
)

private enum class SettingsCategory(val label: String) {
    THEME("Theme"),
    TYPOGRAPHY("Typography & Rhythm"),
    PERFORMANCE_HUD("Performance HUD"),
    LIBRARY_TOOLS("Library Tools")
}

private fun parseColorSafe(hex: String): Color =
    try { Color(AndroidColor.parseColor(hex)) } catch (_: Exception) { Color.Gray }

@Composable
fun SettingsScreen(
    viewModel: AppPreferencesViewModel,
    onNavigateBack: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsState()
    val encoreColors = LocalEncoreColors.current
    var selectedCategory by remember { mutableStateOf(SettingsCategory.THEME) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(encoreColors.screenBackground)
    ) {
        // ── Left panel: category nav ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(encoreColors.cardBackground)
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                        tint = encoreColors.iconTint)
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = encoreColors.titleText
                )
            }
            HorizontalDivider(color = encoreColors.divider)
            Spacer(Modifier.height(8.dp))
            enumValues<SettingsCategory>().forEach { cat ->
                val selected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else encoreColors.titleText
                    )
                }
            }
        }

        // ── Vertical divider ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(0.5.dp)
                .fillMaxHeight()
                .background(encoreColors.divider)
        )

        // ── Right panel: content ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (selectedCategory) {
                SettingsCategory.THEME          -> ThemePanel(prefs, viewModel)
                SettingsCategory.TYPOGRAPHY     -> TypographyPanel(prefs, viewModel)
                SettingsCategory.PERFORMANCE_HUD -> PerformanceHudPanel(prefs, viewModel)
                SettingsCategory.LIBRARY_TOOLS  -> LibraryToolsPanel()
            }
        }
    }
}

// ── Theme Panel (bg colors + per-theme header style matrices) ─────────────────

@Composable
private fun ThemePanel(prefs: AppPreferences, viewModel: AppPreferencesViewModel) {
    val encoreColors = LocalEncoreColors.current
    var selectedTab by remember { mutableStateOf(0) }
    val isDark = selectedTab == 0

    val sections = (if (isDark) prefs.darkSectionStyles else prefs.lightSectionStyles)
        .entries.toList()
    val bgColor = if (isDark) prefs.darkBgColor else prefs.lightBgColor
    val onBgUpdate: (String) -> Unit = if (isDark)
        { hex -> viewModel.updateDarkBgColor(hex) }
    else
        { hex -> viewModel.updateLightBgColor(hex) }
    val onSectionUpdate: (String, SectionStyle) -> Unit = if (isDark)
        { name, style -> viewModel.updateDarkSectionStyle(name, style) }
    else
        { name, style -> viewModel.updateLightSectionStyle(name, style) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed: panel header
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Theme", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = encoreColors.titleText)
            Text("Background and header styles for each display mode",
                style = MaterialTheme.typography.bodySmall, color = encoreColors.subtleText)
        }

        // Fixed: tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = encoreColors.cardBackground,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                text = { Text("Dark Mode") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                text = { Text("Light Mode") })
        }

        // Scrollable: content for selected tab
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsCard {
                    BgColorRow(label = "Background Color", hexColor = bgColor, onUpdate = onBgUpdate)
                }
            }
            items(sections, key = { "${selectedTab}_${it.key}" }) { (name, style) ->
                SectionStyleRow(
                    sectionName = name,
                    style = style,
                    onUpdate = { onSectionUpdate(name, it) }
                )
            }
        }
    }
}

@Composable
private fun BgColorRow(
    label: String,
    hexColor: String,
    onUpdate: (String) -> Unit
) {
    val encoreColors = LocalEncoreColors.current
    var hexInput by remember(hexColor) { mutableStateOf(hexColor) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(parseColorSafe(hexColor))
                .border(1.dp, encoreColors.divider, RoundedCornerShape(6.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = encoreColors.titleText,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = hexInput,
            onValueChange = { v ->
                hexInput = v
                if (v.length == 7 && v.startsWith("#")) {
                    try { AndroidColor.parseColor(v); onUpdate(v) }
                    catch (_: Exception) {}
                }
            },
            singleLine = true,
            modifier = Modifier.width(148.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun SectionStyleRow(
    sectionName: String,
    style: SectionStyle,
    onUpdate: (SectionStyle) -> Unit
) {
    val encoreColors = LocalEncoreColors.current
    var hexInput by remember(style.hexColor) { mutableStateOf(style.hexColor) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = encoreColors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = encoreColors.cardElevation),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: color dot + section name + bold toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(parseColorSafe(style.hexColor))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = sectionName.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = encoreColors.titleText,
                    modifier = Modifier.weight(1f)
                )
                Text("Bold", style = MaterialTheme.typography.labelSmall,
                    color = encoreColors.subtleText)
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = style.isBold,
                    onCheckedChange = { onUpdate(style.copy(isBold = it)) }
                )
            }
            Spacer(Modifier.height(10.dp))
            // Row 2: color swatches
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                COLOR_SWATCHES.forEach { hex ->
                    val isSelected = hex.equals(style.hexColor, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(parseColorSafe(hex))
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .clickable { hexInput = hex; onUpdate(style.copy(hexColor = hex)) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Row 3: hex input + size slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { v ->
                        hexInput = v
                        if (v.length == 7 && v.startsWith("#")) {
                            try { AndroidColor.parseColor(v); onUpdate(style.copy(hexColor = v)) }
                            catch (_: Exception) {}
                        }
                    },
                    label = { Text("Hex Color") },
                    singleLine = true,
                    modifier = Modifier.width(148.dp),
                    textStyle = MaterialTheme.typography.bodySmall
                        .copy(fontFamily = FontFamily.Monospace),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = parseColorSafe(style.hexColor)
                    )
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Size: ${style.fontSize}sp",
                        style = MaterialTheme.typography.labelSmall,
                        color = encoreColors.subtleText
                    )
                    Slider(
                        value = style.fontSize.toFloat(),
                        onValueChange = { onUpdate(style.copy(fontSize = it.roundToInt())) },
                        valueRange = 10f..24f,
                        steps = 13
                    )
                }
            }
        }
    }
}

// ── Typography & Rhythm Panel ─────────────────────────────────────────────────

@Composable
private fun TypographyPanel(prefs: AppPreferences, viewModel: AppPreferencesViewModel) {
    val encoreColors = LocalEncoreColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { PanelHeader("Typography & Rhythm", "Adjust font size, chord spacing, and font family") }

        item {
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Lyric Size", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, color = encoreColors.titleText,
                                modifier = Modifier.weight(1f))
                            Text("${prefs.lyricSize}sp", style = MaterialTheme.typography.labelMedium,
                                color = encoreColors.subtleText)
                        }
                        Slider(
                            value = prefs.lyricSize.toFloat(),
                            onValueChange = { viewModel.updateLyricSize(it.roundToInt()) },
                            valueRange = 10f..24f,
                            steps = 13
                        )
                    }
                    HorizontalDivider(color = encoreColors.divider)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Chord Spacing", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, color = encoreColors.titleText,
                                modifier = Modifier.weight(1f))
                            Text("${"%.1f".format(prefs.chordSpacing)}dp",
                                style = MaterialTheme.typography.labelMedium,
                                color = encoreColors.subtleText)
                        }
                        Slider(
                            value = prefs.chordSpacing,
                            onValueChange = { viewModel.updateChordSpacing(it) },
                            valueRange = 0f..24f
                        )
                    }
                }
            }
        }

        item {
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Font Family", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = encoreColors.titleText)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        enumValues<SongFontFamily>().forEach { family ->
                            val isSelected = prefs.fontFamily == family
                            OutlinedButton(
                                onClick = { viewModel.updateFontFamily(family) },
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else encoreColors.divider
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary
                                                  else encoreColors.titleText
                                )
                            ) {
                                Text(
                                    text = family.displayName,
                                    fontFamily = if (family == SongFontFamily.MONOSPACE)
                                        FontFamily.Monospace else FontFamily.SansSerif,
                                    fontWeight = if (isSelected) FontWeight.SemiBold
                                                else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Performance HUD Panel ─────────────────────────────────────────────────────

@Composable
private fun PerformanceHudPanel(prefs: AppPreferences, viewModel: AppPreferencesViewModel) {
    val encoreColors = LocalEncoreColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { PanelHeader("Performance HUD", "Toggle indicators shown during performance mode") }

        item {
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HudToggleRow(
                        label = "Lead Guitar Indicator",
                        description = "Show a guitar icon badge when a song is marked as lead guitar",
                        checked = prefs.showLeadIndicator,
                        onCheckedChange = { viewModel.updateShowLeadIndicator(it) }
                    )
                    HorizontalDivider(color = encoreColors.divider)
                    HudToggleRow(
                        label = "Transposition Warning",
                        description = "Show a badge when a song is displayed in a different key",
                        checked = prefs.showTranspositionWarning,
                        onCheckedChange = { viewModel.updateShowTranspositionWarning(it) }
                    )
                    HorizontalDivider(color = encoreColors.divider)
                    HudToggleRow(
                        label = "Show Chords",
                        description = "Render chord lines in the chart viewer",
                        checked = prefs.showChords,
                        onCheckedChange = { viewModel.updateShowChords(it) }
                    )
                    HorizontalDivider(color = encoreColors.divider)
                    HudToggleRow(
                        label = "Show Key Info",
                        description = "Display key and BPM in the song header",
                        checked = prefs.showKeyInfo,
                        onCheckedChange = { viewModel.updateShowKeyInfo(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HudToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val encoreColors = LocalEncoreColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier
            .weight(1f)
            .padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, color = encoreColors.titleText)
            Spacer(Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = encoreColors.subtleText)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Library Tools Panel ───────────────────────────────────────────────────────

@Composable
private fun LibraryToolsPanel() {
    val encoreColors = LocalEncoreColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item { PanelHeader("Library Tools", "Utilities for managing your song library") }
        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)) {
                        Text("Library Health Scanner",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = encoreColors.titleText)
                        Spacer(Modifier.height(4.dp))
                        Text("Scan for missing metadata, duplicate titles, and chart formatting issues",
                            style = MaterialTheme.typography.bodySmall,
                            color = encoreColors.subtleText)
                    }
                    OutlinedButton(onClick = {}, enabled = false) { Text("Run Scan") }
                }
            }
        }
    }
}

// ── Shared Composables ────────────────────────────────────────────────────────

@Composable
private fun PanelHeader(title: String, subtitle: String) {
    val encoreColors = LocalEncoreColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = encoreColors.titleText)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = encoreColors.subtleText)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = encoreColors.divider)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val encoreColors = LocalEncoreColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = encoreColors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = encoreColors.cardElevation),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
