package com.encore.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.encore.core.data.entities.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

// Separate DataStore file from user_prefs (auth). Visual preferences are not user-scoped.
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

/**
 * DataStore-backed repository for global display preferences.
 *
 * Uses its own `app_prefs` DataStore file (separate from auth's `user_prefs`).
 * Visual preferences survive sign-out — they are device-scoped, not user-scoped.
 */
class AppPreferencesRepository(private val context: Context) {

    internal object Keys {
        // Typography & rhythm
        val LYRIC_SIZE              = intPreferencesKey("ap_lyric_size")
        val CHORD_SPACING           = floatPreferencesKey("ap_chord_spacing")

        // Performance mode flags
        val SHOW_LEAD_INDICATOR         = booleanPreferencesKey("ap_show_lead_indicator")
        val SHOW_TRANSPOSITION_WARNING  = booleanPreferencesKey("ap_show_transposition_warning")
        val SHOW_CHORDS                 = booleanPreferencesKey("ap_show_chords")
        val SHOW_KEY_INFO               = booleanPreferencesKey("ap_show_key_info")

        // Dual-theme backgrounds
        val DARK_BG_COLOR           = stringPreferencesKey("ap_dark_bg_color")
        val LIGHT_BG_COLOR          = stringPreferencesKey("ap_light_bg_color")

        // Dual-theme body text colors
        val DARK_LYRIC_COLOR        = stringPreferencesKey("ap_dark_lyric_color")
        val LIGHT_LYRIC_COLOR       = stringPreferencesKey("ap_light_lyric_color")
        val DARK_CHORD_COLOR        = stringPreferencesKey("ap_dark_chord_color")
        val LIGHT_CHORD_COLOR       = stringPreferencesKey("ap_light_chord_color")
        val DARK_HARMONY_COLOR      = stringPreferencesKey("ap_dark_harmony_color")
        val LIGHT_HARMONY_COLOR     = stringPreferencesKey("ap_light_harmony_color")

        // Per-theme section styles — serialised as JSON object strings
        val DARK_SECTION_STYLES_JSON  = stringPreferencesKey("ap_dark_section_styles_json")
        val LIGHT_SECTION_STYLES_JSON = stringPreferencesKey("ap_light_section_styles_json")

        // Per-theme user-saved presets — serialised as JSON array strings
        val DARK_USER_PRESETS       = stringPreferencesKey("ap_dark_user_presets")
        val LIGHT_USER_PRESETS      = stringPreferencesKey("ap_light_user_presets")

        // Font family
        val FONT_FAMILY             = stringPreferencesKey("ap_font_family")

        // Performance indicator icon colors
        val DARK_LEAD_ICON_COLOR    = stringPreferencesKey("ap_dark_lead_icon_color")
        val LIGHT_LEAD_ICON_COLOR   = stringPreferencesKey("ap_light_lead_icon_color")
        val DARK_CAPO_COLOR         = stringPreferencesKey("ap_dark_capo_color")
        val LIGHT_CAPO_COLOR        = stringPreferencesKey("ap_light_capo_color")

        // Title/artist color overrides (null stored as absent key)
        val TITLE_COLOR_OVERRIDE    = stringPreferencesKey("ap_title_color_override")
        val ARTIST_COLOR_OVERRIDE   = stringPreferencesKey("ap_artist_color_override")

        // First-run sentinel — set after Zen Studio defaults are written once
        val DEFAULTS_APPLIED        = booleanPreferencesKey("ap_defaults_applied")
    }

    // ── First-run defaults ───────────────────────────────────────────────────

    /**
     * Writes Zen Studio dark + light section styles on first launch.
     * No-op if already applied (guarded by [Keys.DEFAULTS_APPLIED]).
     * Safe to call from ViewModel init every session.
     */
    suspend fun applyDefaultsIfNeeded(
        darkSections: Map<String, SectionStyle>,
        lightSections: Map<String, SectionStyle>
    ) {
        context.appDataStore.edit { prefs ->
            if (prefs[Keys.DEFAULTS_APPLIED] == true) return@edit
            prefs[Keys.DARK_SECTION_STYLES_JSON]  = encodeSectionStyles(darkSections)
            prefs[Keys.LIGHT_SECTION_STYLES_JSON] = encodeSectionStyles(lightSections)
            prefs[Keys.DEFAULTS_APPLIED] = true
        }
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    val appPreferences: Flow<AppPreferences> = context.appDataStore.data.map { prefs ->
        AppPreferences(
            darkSectionStyles       = prefs[Keys.DARK_SECTION_STYLES_JSON]
                                        ?.let(::decodeSectionStyles)
                                        ?: AppPreferences.DEFAULT_SECTION_STYLES,
            lightSectionStyles      = prefs[Keys.LIGHT_SECTION_STYLES_JSON]
                                        ?.let(::decodeSectionStyles)
                                        ?: AppPreferences.DEFAULT_SECTION_STYLES,
            lyricSize               = prefs[Keys.LYRIC_SIZE]               ?: 14,
            chordSpacing            = prefs[Keys.CHORD_SPACING]            ?: 0f,
            showLeadIndicator       = prefs[Keys.SHOW_LEAD_INDICATOR]      ?: true,
            showTranspositionWarning= prefs[Keys.SHOW_TRANSPOSITION_WARNING] ?: true,
            showChords              = prefs[Keys.SHOW_CHORDS]              ?: true,
            showKeyInfo             = prefs[Keys.SHOW_KEY_INFO]            ?: true,
            darkBgColor             = prefs[Keys.DARK_BG_COLOR]            ?: "#0F1115",
            lightBgColor            = prefs[Keys.LIGHT_BG_COLOR]           ?: "#F6F7F4",
            darkLyricColor          = prefs[Keys.DARK_LYRIC_COLOR]         ?: "#E5E7EB",
            lightLyricColor         = prefs[Keys.LIGHT_LYRIC_COLOR]        ?: "#1E293B",
            darkChordColor          = prefs[Keys.DARK_CHORD_COLOR]         ?: "#7DD3FC",
            lightChordColor         = prefs[Keys.LIGHT_CHORD_COLOR]        ?: "#1D4ED8",
            darkHarmonyColor        = prefs[Keys.DARK_HARMONY_COLOR]       ?: "#5B4A1A",
            lightHarmonyColor       = prefs[Keys.LIGHT_HARMONY_COLOR]      ?: "#FFF3BF",
            fontFamily              = prefs[Keys.FONT_FAMILY]
                                        ?.let { runCatching { SongFontFamily.valueOf(it) }.getOrNull() }
                                        ?: SongFontFamily.SANS_SERIF,
            darkLeadIconColor       = prefs[Keys.DARK_LEAD_ICON_COLOR]   ?: "#7DD3FC",
            lightLeadIconColor      = prefs[Keys.LIGHT_LEAD_ICON_COLOR]  ?: "#2563EB",
            darkCapoColor           = prefs[Keys.DARK_CAPO_COLOR]        ?: "#F59E0B",
            lightCapoColor          = prefs[Keys.LIGHT_CAPO_COLOR]       ?: "#B45309",
            titleColorOverride      = prefs[Keys.TITLE_COLOR_OVERRIDE],
            artistColorOverride     = prefs[Keys.ARTIST_COLOR_OVERRIDE],
        )
    }

    val darkUserPresets: Flow<List<ThemePreset>> = context.appDataStore.data.map { prefs ->
        prefs[Keys.DARK_USER_PRESETS]?.let(::decodePresets) ?: emptyList()
    }

    val lightUserPresets: Flow<List<ThemePreset>> = context.appDataStore.data.map { prefs ->
        prefs[Keys.LIGHT_USER_PRESETS]?.let(::decodePresets) ?: emptyList()
    }

    // ── Write — individual fields ─────────────────────────────────────────────

    suspend fun updateLyricSize(size: Int) {
        context.appDataStore.edit { it[Keys.LYRIC_SIZE] = size }
    }

    suspend fun updateChordSpacing(spacing: Float) {
        context.appDataStore.edit { it[Keys.CHORD_SPACING] = spacing }
    }

    suspend fun updateShowLeadIndicator(show: Boolean) {
        context.appDataStore.edit { it[Keys.SHOW_LEAD_INDICATOR] = show }
    }

    suspend fun updateShowTranspositionWarning(show: Boolean) {
        context.appDataStore.edit { it[Keys.SHOW_TRANSPOSITION_WARNING] = show }
    }

    suspend fun updateShowChords(show: Boolean) {
        context.appDataStore.edit { it[Keys.SHOW_CHORDS] = show }
    }

    suspend fun updateShowKeyInfo(show: Boolean) {
        context.appDataStore.edit { it[Keys.SHOW_KEY_INFO] = show }
    }

    suspend fun updateFontFamily(family: SongFontFamily) {
        context.appDataStore.edit { it[Keys.FONT_FAMILY] = family.name }
    }

    suspend fun updateDarkBgColor(hex: String) {
        context.appDataStore.edit { it[Keys.DARK_BG_COLOR] = hex }
    }

    suspend fun updateLightBgColor(hex: String) {
        context.appDataStore.edit { it[Keys.LIGHT_BG_COLOR] = hex }
    }

    suspend fun updateDarkLyricColor(hex: String) {
        context.appDataStore.edit { it[Keys.DARK_LYRIC_COLOR] = hex }
    }

    suspend fun updateLightLyricColor(hex: String) {
        context.appDataStore.edit { it[Keys.LIGHT_LYRIC_COLOR] = hex }
    }

    suspend fun updateDarkChordColor(hex: String) {
        context.appDataStore.edit { it[Keys.DARK_CHORD_COLOR] = hex }
    }

    suspend fun updateLightChordColor(hex: String) {
        context.appDataStore.edit { it[Keys.LIGHT_CHORD_COLOR] = hex }
    }

    suspend fun updateDarkHarmonyColor(hex: String) {
        context.appDataStore.edit { it[Keys.DARK_HARMONY_COLOR] = hex }
    }

    suspend fun updateLightHarmonyColor(hex: String) {
        context.appDataStore.edit { it[Keys.LIGHT_HARMONY_COLOR] = hex }
    }

    suspend fun updateDarkLeadIconColor(hex: String) {
        context.appDataStore.edit { it[Keys.DARK_LEAD_ICON_COLOR] = hex }
    }

    suspend fun updateLightLeadIconColor(hex: String) {
        context.appDataStore.edit { it[Keys.LIGHT_LEAD_ICON_COLOR] = hex }
    }

    suspend fun updateDarkCapoColor(hex: String) {
        context.appDataStore.edit { it[Keys.DARK_CAPO_COLOR] = hex }
    }

    suspend fun updateLightCapoColor(hex: String) {
        context.appDataStore.edit { it[Keys.LIGHT_CAPO_COLOR] = hex }
    }

    suspend fun updateTitleColorOverride(hex: String?) {
        context.appDataStore.edit { prefs ->
            if (hex == null) prefs.remove(Keys.TITLE_COLOR_OVERRIDE)
            else prefs[Keys.TITLE_COLOR_OVERRIDE] = hex
        }
    }

    suspend fun updateArtistColorOverride(hex: String?) {
        context.appDataStore.edit { prefs ->
            if (hex == null) prefs.remove(Keys.ARTIST_COLOR_OVERRIDE)
            else prefs[Keys.ARTIST_COLOR_OVERRIDE] = hex
        }
    }


    suspend fun updateDarkSectionStyle(section: String, style: SectionStyle) {
        context.appDataStore.edit { prefs ->
            val current = prefs[Keys.DARK_SECTION_STYLES_JSON]
                ?.let(::decodeSectionStyles)
                ?.toMutableMap()
                ?: AppPreferences.DEFAULT_SECTION_STYLES.toMutableMap()
            current[section.lowercase().trim()] = style
            prefs[Keys.DARK_SECTION_STYLES_JSON] = encodeSectionStyles(current)
        }
    }

    suspend fun updateLightSectionStyle(section: String, style: SectionStyle) {
        context.appDataStore.edit { prefs ->
            val current = prefs[Keys.LIGHT_SECTION_STYLES_JSON]
                ?.let(::decodeSectionStyles)
                ?.toMutableMap()
                ?: AppPreferences.DEFAULT_SECTION_STYLES.toMutableMap()
            current[section.lowercase().trim()] = style
            prefs[Keys.LIGHT_SECTION_STYLES_JSON] = encodeSectionStyles(current)
        }
    }

    // ── Preset CRUD ───────────────────────────────────────────────────────────

    /**
     * Atomically writes all preset values into the active theme's DataStore keys.
     * If [isDark] is true, writes to dark keys; otherwise light keys.
     */
    suspend fun loadPreset(preset: ThemePreset, isDark: Boolean) {
        context.appDataStore.edit { prefs ->
            if (isDark) {
                prefs[Keys.DARK_BG_COLOR]            = preset.bgColor
                prefs[Keys.DARK_LYRIC_COLOR]         = preset.lyricColor
                prefs[Keys.DARK_CHORD_COLOR]          = preset.chordColor
                prefs[Keys.DARK_HARMONY_COLOR]        = preset.harmonyColor
                prefs[Keys.DARK_SECTION_STYLES_JSON]  = encodeSectionStyles(preset.sectionStyles)
                preset.leadIconColor?.let { prefs[Keys.DARK_LEAD_ICON_COLOR] = it }
                preset.capoColor?.let    { prefs[Keys.DARK_CAPO_COLOR]       = it }
            } else {
                prefs[Keys.LIGHT_BG_COLOR]            = preset.bgColor
                prefs[Keys.LIGHT_LYRIC_COLOR]         = preset.lyricColor
                prefs[Keys.LIGHT_CHORD_COLOR]          = preset.chordColor
                prefs[Keys.LIGHT_HARMONY_COLOR]        = preset.harmonyColor
                prefs[Keys.LIGHT_SECTION_STYLES_JSON]  = encodeSectionStyles(preset.sectionStyles)
                preset.leadIconColor?.let { prefs[Keys.LIGHT_LEAD_ICON_COLOR] = it }
                preset.capoColor?.let    { prefs[Keys.LIGHT_CAPO_COLOR]       = it }
            }
        }
    }

    /** Saves a user-created preset. Replaces any existing preset with the same [ThemePreset.id]. */
    suspend fun savePreset(preset: ThemePreset, isDark: Boolean) {
        context.appDataStore.edit { prefs ->
            val key = if (isDark) Keys.DARK_USER_PRESETS else Keys.LIGHT_USER_PRESETS
            val current = prefs[key]?.let(::decodePresets)?.toMutableList() ?: mutableListOf()
            current.removeAll { it.id == preset.id }
            current.add(preset)
            prefs[key] = encodePresets(current)
        }
    }

    /** Deletes a user-created preset by id. No-op if id not found. */
    suspend fun deletePreset(id: String, isDark: Boolean) {
        context.appDataStore.edit { prefs ->
            val key = if (isDark) Keys.DARK_USER_PRESETS else Keys.LIGHT_USER_PRESETS
            val current = prefs[key]?.let(::decodePresets)?.toMutableList() ?: return@edit
            current.removeAll { it.id == id }
            prefs[key] = encodePresets(current)
        }
    }

    /**
     * Promotes the current song's visual state to global defaults.
     */
    suspend fun promoteToGlobal(song: SongEntity, isDarkTheme: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.SHOW_LEAD_INDICATOR]        = song.isLeadGuitar
            prefs[Keys.SHOW_TRANSPOSITION_WARNING] =
                song.originalKey != null && song.displayKey != song.originalKey

            if (isDarkTheme) {
                prefs[Keys.DARK_BG_COLOR] = "#000000"
            } else {
                prefs[Keys.LIGHT_BG_COLOR] = "#F2F2F7"
            }
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun encodeSectionStyles(styles: Map<String, SectionStyle>): String {
        val root = JSONObject()
        styles.forEach { (key, style) ->
            root.put(key, JSONObject().apply {
                put("hexColor", style.hexColor)
                put("fontSize", style.fontSize)
                put("isBold",   style.isBold)
            })
        }
        return root.toString()
    }

    private fun decodeSectionStyles(json: String): Map<String, SectionStyle> {
        return try {
            val root = JSONObject(json)
            buildMap {
                root.keys().forEach { key ->
                    val obj = root.getJSONObject(key)
                    put(key, SectionStyle(
                        hexColor = obj.getString("hexColor"),
                        fontSize = obj.optInt("fontSize", 16),
                        isBold   = obj.optBoolean("isBold", true)
                    ))
                }
            }
        } catch (_: Exception) {
            AppPreferences.DEFAULT_SECTION_STYLES
        }
    }

    private fun encodePresets(presets: List<ThemePreset>): String {
        val arr = JSONArray()
        presets.forEach { preset ->
            arr.put(JSONObject().apply {
                put("id",           preset.id)
                put("name",         preset.name)
                put("bgColor",      preset.bgColor)
                put("lyricColor",   preset.lyricColor)
                put("chordColor",   preset.chordColor)
                put("harmonyColor", preset.harmonyColor)
                put("sectionStyles", JSONObject(encodeSectionStyles(preset.sectionStyles)))
                preset.leadIconColor?.let { put("leadIconColor", it) }
                preset.capoColor?.let    { put("capoColor",      it) }
            })
        }
        return arr.toString()
    }

    private fun decodePresets(json: String): List<ThemePreset> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ThemePreset(
                    id            = obj.getString("id"),
                    name          = obj.getString("name"),
                    isBuiltIn     = false,
                    bgColor       = obj.getString("bgColor"),
                    lyricColor    = obj.getString("lyricColor"),
                    chordColor    = obj.getString("chordColor"),
                    harmonyColor  = obj.getString("harmonyColor"),
                    sectionStyles = decodeSectionStyles(obj.getJSONObject("sectionStyles").toString()),
                    leadIconColor = obj.optString("leadIconColor").takeIf { it.isNotEmpty() },
                    capoColor     = obj.optString("capoColor").takeIf { it.isNotEmpty() },
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
