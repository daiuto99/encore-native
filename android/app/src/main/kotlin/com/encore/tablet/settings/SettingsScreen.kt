package com.encore.tablet.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.preferences.AppPreferences
import com.encore.core.data.preferences.SectionStyle
import com.encore.core.data.preferences.SongFontFamily
import com.encore.core.data.preferences.ThemePreset
import com.encore.core.ui.theme.LocalEncoreColors
import com.encore.tablet.audit.LibraryAuditViewModel
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
    auditViewModel: LibraryAuditViewModel,
    onEditSong: (SongEntity) -> Unit,
    onNavigateBack: () -> Unit,
    onSyncNow: () -> Unit = {}
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
                SettingsCategory.LIBRARY_TOOLS  -> LibraryHealthPanel(auditViewModel, onEditSong, onSyncNow)
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

    val darkUserPresets by viewModel.darkUserPresets.collectAsState()
    val lightUserPresets by viewModel.lightUserPresets.collectAsState()

    val sections = (if (isDark) prefs.darkSectionStyles else prefs.lightSectionStyles)
        .entries.toList()
    val bgColor = if (isDark) prefs.darkBgColor else prefs.lightBgColor
    val leadIconColor = if (isDark) prefs.darkLeadIconColor else prefs.lightLeadIconColor
    val capoColor = if (isDark) prefs.darkCapoColor else prefs.lightCapoColor
    val onBgUpdate: (String) -> Unit = if (isDark)
        { hex -> viewModel.updateDarkBgColor(hex) }
    else
        { hex -> viewModel.updateLightBgColor(hex) }
    val onLeadIconUpdate: (String) -> Unit = if (isDark)
        { hex -> viewModel.updateDarkLeadIconColor(hex) }
    else
        { hex -> viewModel.updateLightLeadIconColor(hex) }
    val onCapoUpdate: (String) -> Unit = if (isDark)
        { hex -> viewModel.updateDarkCapoColor(hex) }
    else
        { hex -> viewModel.updateLightCapoColor(hex) }
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
            item(key = "presets_$selectedTab") {
                PresetSection(
                    builtInPresets = if (isDark) BuiltInThemes.DARK else BuiltInThemes.LIGHT,
                    userPresets = if (isDark) darkUserPresets else lightUserPresets,
                    isDark = isDark,
                    viewModel = viewModel
                )
            }
            item(key = "bg_$selectedTab") {
                SettingsCard {
                    BgColorRow(label = "Background Color", hexColor = bgColor, onUpdate = onBgUpdate)
                }
            }
            item(key = "icon_colors_$selectedTab") {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BgColorRow(
                            label = "Lead Guitar Icon",
                            hexColor = leadIconColor,
                            onUpdate = onLeadIconUpdate
                        )
                        HorizontalDivider(color = encoreColors.divider)
                        BgColorRow(
                            label = "Capo Badge",
                            hexColor = capoColor,
                            onUpdate = onCapoUpdate
                        )
                    }
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

        // ── Song Title / Artist Color Overrides ───────────────────────────────
        item {
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Song Title & Artist Colors",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = encoreColors.titleText
                    )
                    Text(
                        "Override the default set color used for song titles. Leave blank to use the set's color.",
                        style = MaterialTheme.typography.bodySmall,
                        color = encoreColors.subtleText
                    )
                    HorizontalDivider(color = encoreColors.divider)
                    ColorOverrideRow(
                        label = "Title Color",
                        currentHex = prefs.titleColorOverride,
                        onUpdate = { viewModel.updateTitleColorOverride(it) }
                    )
                    HorizontalDivider(color = encoreColors.divider)
                    ColorOverrideRow(
                        label = "Artist Color",
                        currentHex = prefs.artistColorOverride,
                        onUpdate = { viewModel.updateArtistColorOverride(it) }
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

@Composable
private fun ColorOverrideRow(
    label: String,
    currentHex: String?,
    onUpdate: (String?) -> Unit
) {
    val encoreColors = LocalEncoreColors.current
    var hexInput by remember(currentHex) { mutableStateOf(currentHex ?: "") }
    val previewColor = currentHex?.let {
        runCatching { parseColorSafe(it) }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Color swatch — shows the override color, or a dash pattern if unset
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(previewColor ?: encoreColors.divider)
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
                if (v.isBlank()) {
                    onUpdate(null)
                } else if (v.length == 7 && v.startsWith("#")) {
                    try { AndroidColor.parseColor(v); onUpdate(v) }
                    catch (_: Exception) {}
                }
            },
            placeholder = { Text("Set color", style = MaterialTheme.typography.bodySmall, color = encoreColors.subtleText) },
            singleLine = true,
            modifier = Modifier.width(148.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            trailingIcon = if (currentHex != null) {
                {
                    IconButton(onClick = { hexInput = ""; onUpdate(null) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear override", modifier = Modifier.size(16.dp))
                    }
                }
            } else null
        )
    }
}

// ── Library Health Panel ──────────────────────────────────────────────────────

@Composable
private fun LibraryHealthPanel(
    auditViewModel: LibraryAuditViewModel,
    onEditSong: (SongEntity) -> Unit,
    onSyncNow: () -> Unit = {}
) {
    val context = LocalContext.current
    val encoreColors = LocalEncoreColors.current
    val invalidSongs by auditViewModel.invalidSongs.collectAsState()
    val isScanning by auditViewModel.isScanning.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { PanelHeader("Library Health", "Scan charts for metadata gaps and formatting issues") }

        // ── Cloud Sync card ───────────────────────────────────────────────────
        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cloud Sync",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = encoreColors.titleText
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Check all songs against the server and resolve conflicts",
                            style = MaterialTheme.typography.bodySmall,
                            color = encoreColors.subtleText
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = onSyncNow) {
                        Text("Sync Now")
                    }
                }
            }
        }

        // ── Summary card ─────────────────────────────────────────────────────
        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val label = when {
                            isScanning -> "Scanning library…"
                            invalidSongs.isEmpty() -> "No issues found"
                            else -> "${invalidSongs.size} song${if (invalidSongs.size == 1) "" else "s"} with issues"
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                isScanning -> encoreColors.subtleText
                                invalidSongs.isEmpty() -> encoreColors.titleText
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Checks: missing title/artist/key, unclosed [h] tags, non-standard sections",
                            style = MaterialTheme.typography.bodySmall,
                            color = encoreColors.subtleText
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(
                        onClick = { auditViewModel.runScan(context) },
                        enabled = !isScanning
                    ) {
                        Text(if (isScanning) "Scanning…" else "Run Scan")
                    }
                }
            }
        }

        // ── Issue list ───────────────────────────────────────────────────────
        if (invalidSongs.isNotEmpty()) {
            item {
                Text(
                    text = "Issues",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = encoreColors.subtleText,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            items(invalidSongs, key = { it.id }) { song ->
                AuditIssueRow(song = song, onEditSong = onEditSong)
            }
        }
    }
}

@Composable
private fun AuditIssueRow(
    song: SongEntity,
    onEditSong: (SongEntity) -> Unit
) {
    val encoreColors = LocalEncoreColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditSong(song) },
        colors = CardDefaults.cardColors(containerColor = encoreColors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = encoreColors.cardElevation),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = encoreColors.titleText
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = encoreColors.subtleText
                )
                Spacer(Modifier.height(6.dp))
                // Each error is delimited by " • " in the stored string
                song.validationErrors?.split(" • ")?.forEach { error ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Open editor",
                tint = encoreColors.subtleText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Preset UI ─────────────────────────────────────────────────────────────────

@Composable
private fun PresetSection(
    builtInPresets: List<ThemePreset>,
    userPresets: List<ThemePreset>,
    isDark: Boolean,
    viewModel: AppPreferencesViewModel
) {
    val encoreColors = LocalEncoreColors.current
    var selectedPreset by remember(isDark) { mutableStateOf<ThemePreset?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                viewModel.saveCurrentAsPreset(name, isDark)
                showSaveDialog = false
            }
        )
    }

    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Built-in presets ──────────────────────────────────────────────
            Text(
                text = "Built-in",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = encoreColors.subtleText
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                builtInPresets.forEach { preset ->
                    PresetChip(
                        preset = preset,
                        isSelected = selectedPreset?.id == preset.id,
                        onClick = { selectedPreset = preset }
                    )
                }
            }

            HorizontalDivider(color = encoreColors.divider)

            // ── User presets ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = encoreColors.subtleText,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showSaveDialog = true }) {
                    Text("Save New Preset", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (userPresets.isEmpty()) {
                Text(
                    text = "No saved presets yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = encoreColors.subtleText
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    userPresets.forEach { preset ->
                        PresetChip(
                            preset = preset,
                            isSelected = selectedPreset?.id == preset.id,
                            onClick = { selectedPreset = preset },
                            onDelete = { viewModel.deletePreset(preset.id, isDark) }
                        )
                    }
                }
            }

            // ── Color preview + Use Preset ────────────────────────────────────
            val preview = selectedPreset
            if (preview != null) {
                HorizontalDivider(color = encoreColors.divider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Color swatches for the selected preset
                    listOf(
                        "BG"  to preview.bgColor,
                        "Lyric" to preview.lyricColor,
                        "Chord" to preview.chordColor,
                        "Harmony" to preview.harmonyColor
                    ).forEach { (label, hex) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(parseColorSafe(hex))
                                    .border(0.5.dp, encoreColors.divider, CircleShape)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = encoreColors.subtleText
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            viewModel.loadPreset(preview, isDark)
                            selectedPreset = null
                        }
                    ) {
                        Text("Use Preset")
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    preset: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val encoreColors = LocalEncoreColors.current
    val primary = MaterialTheme.colorScheme.primary
    val borderColor = if (isSelected) primary else encoreColors.divider
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected) primary.copy(alpha = 0.08f)
                else encoreColors.cardBackground
            )
            .border(borderWidth, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Color preview dots (bg + chord)
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(parseColorSafe(preset.bgColor))
                .border(0.5.dp, encoreColors.divider, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(parseColorSafe(preset.chordColor))
        )
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) primary else encoreColors.titleText
        )
        if (onDelete != null) {
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete preset",
                    tint = encoreColors.subtleText,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
