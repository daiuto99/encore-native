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

    private object Keys {
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

        // Section styles — serialised as a JSON object string
        val SECTION_STYLES_JSON     = stringPreferencesKey("ap_section_styles_json")
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    val appPreferences: Flow<AppPreferences> = context.appDataStore.data.map { prefs ->
        AppPreferences(
            sectionStyles           = prefs[Keys.SECTION_STYLES_JSON]
                                        ?.let(::decodeSectionStyles)
                                        ?: AppPreferences.DEFAULT_SECTION_STYLES,
            lyricSize               = prefs[Keys.LYRIC_SIZE]               ?: 14,
            chordSpacing            = prefs[Keys.CHORD_SPACING]            ?: 0f,
            showLeadIndicator       = prefs[Keys.SHOW_LEAD_INDICATOR]      ?: true,
            showTranspositionWarning= prefs[Keys.SHOW_TRANSPOSITION_WARNING] ?: true,
            showChords              = prefs[Keys.SHOW_CHORDS]              ?: true,
            showKeyInfo             = prefs[Keys.SHOW_KEY_INFO]            ?: true,
            darkBgColor             = prefs[Keys.DARK_BG_COLOR]            ?: "#000000",
            lightBgColor            = prefs[Keys.LIGHT_BG_COLOR]           ?: "#F2F2F7"
        )
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

    suspend fun updateSectionStyle(section: String, style: SectionStyle) {
        context.appDataStore.edit { prefs ->
            val current = prefs[Keys.SECTION_STYLES_JSON]
                ?.let(::decodeSectionStyles)
                ?.toMutableMap()
                ?: AppPreferences.DEFAULT_SECTION_STYLES.toMutableMap()
            current[section.lowercase().trim()] = style
            prefs[Keys.SECTION_STYLES_JSON] = encodeSectionStyles(current)
        }
    }

    /**
     * Promotes the current song's visual state to global defaults.
     *
     * Theme-aware: only the dark-mode or light-mode background key is updated
     * depending on [isDarkTheme], preventing a stage dark config from being
     * overwritten by a rehearsal light-mode session and vice-versa.
     *
     * @param song        The song whose state is being promoted.
     * @param isDarkTheme Pass `isSystemInDarkTheme()` from the UI layer.
     */
    suspend fun promoteToGlobal(song: SongEntity, isDarkTheme: Boolean) {
        context.appDataStore.edit { prefs ->
            // Performance mode flags driven by song metadata
            prefs[Keys.SHOW_LEAD_INDICATOR]        = song.isLeadGuitar
            prefs[Keys.SHOW_TRANSPOSITION_WARNING] =
                song.originalKey != null && song.displayKey != song.originalKey

            // Background color — only touch the theme that is currently active
            if (isDarkTheme) {
                prefs[Keys.DARK_BG_COLOR] = "#000000"   // stage default: true black
            } else {
                prefs[Keys.LIGHT_BG_COLOR] = "#F2F2F7"  // rehearsal default: system light grey
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
}
