# ADR 003 — Global Preference Engine & Settings Screen

**Date:** 2026-03-31
**Status:** Implemented
**Branch:** `feature/setlist-engine`

---

## Context

The app needed a persistent, device-scoped store for visual display preferences (section header colors, font sizes, chord spacing, performance HUD toggles, background colors). Previously these lived in a `DisplayPreferencesHolder` singleton that reset on every launch and had no UI to edit them.

---

## Decisions

### 1. Separate `app_prefs` DataStore file

Visual preferences use their own Jetpack DataStore file (`app_prefs`), separate from the auth store (`user_prefs` owned by `UserPreferencesRepository`). This prevents visual prefs from being wiped on sign-out and keeps the two concerns decoupled.

```kotlin
private val Context.appDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "app_prefs")
```

### 2. Per-theme section style maps

`AppPreferences` stores two independent section style maps: `darkSectionStyles` and `lightSectionStyles`. Each is serialized to a separate DataStore key (`ap_dark_section_styles_json` / `ap_light_section_styles_json`).

**Why separate:** The color palette that reads well on a true-black stage screen differs from one that reads well on a light rehearsal screen. A single map would force the user to reconfigure every time they switch modes.

**Rendering:** `parseSongSections()` in `SongDetailScreen` receives `isDark: Boolean` (sourced from `LocalEncoreColors.current.isDark` at the call site) and selects the correct map via `AppPreferences.getSectionColor(name, prefs, isDark)`. Section colors switch automatically when the dark/light toggle is tapped.

### 3. JSON serialization for Map<String, SectionStyle>

DataStore Preferences does not support `Map` values natively. Each style map is serialized to a JSON object string using `org.json.JSONObject` (already on the Android platform — no extra dependency). Encode/decode helpers are private to `AppPreferencesRepository`.

```
Key: "ap_dark_section_styles_json"
Value: {"verse":{"hexColor":"#F97316","fontSize":16,"isBold":true}, ...}
```

Decode failures return `DEFAULT_SECTION_STYLES` silently.

### 4. SongFontFamily enum

```kotlin
enum class SongFontFamily(val displayName: String) {
    SANS_SERIF("Sans-Serif"),
    MONOSPACE("Monospace")
}
```

Stored as enum name string in DataStore (`ap_font_family`). Decoded with `runCatching { SongFontFamily.valueOf(it) }`, defaulting to `SANS_SERIF` on error. Wiring to actual lyric rendering is deferred (the preference is persisted and exposed, but the font family toggle in the renderer is a future task).

### 5. promoteToGlobal is theme-aware

`AppPreferencesRepository.promoteToGlobal(song, isDarkTheme)` updates only the dark or light background key based on the `isDarkTheme` parameter (passed from the UI layer via `isSystemInDarkTheme()`). This prevents a stage (dark) configuration from being silently overwritten by a daytime rehearsal session.

### 6. Settings Screen — two-pane tablet layout

`SettingsScreen.kt` lives in the `app` module (not a `feature/` module) because it reads from `AppPreferencesViewModel` which is `app`-scoped. Two-pane layout: 220dp fixed left nav, fills-remaining right panel.

The Theme category uses a `TabRow` (Dark Mode / Light Mode) rather than a single long scrollable list. This keeps the background color and section style matrix for each mode independently browsable without scrolling past the other.

### 7. Built-in theme presets

Five factory presets ship as hardcoded constants in `BuiltInThemes` (`app` module). They are never written to DataStore and cannot be deleted. Loading one calls the same atomic `loadPreset()` write path as user-created presets.

**Dark bank:** Midnight Mainstage, Neon Night-Shift
**Light bank:** Studio Daylight, Bourbon & Vinyl, Solar Flare

User-saved presets are stored per-mode under `ap_dark_user_presets` / `ap_light_user_presets` as JSON arrays. Per-mode storage matches the Dark Mode / Light Mode tab structure and prevents cross-contamination between stage and rehearsal configurations.

`ThemePreset` bundles `bgColor`, `lyricColor`, `chordColor`, `harmonyColor`, and `sectionStyles`. All are written atomically in a single DataStore `edit {}` block.

Preset UX: tapping a chip selects it and shows a 4-swatch color preview (BG / Lyric / Chord / Harmony). "Use Preset" commits. "Save New Preset" captures the current live theme by name with a UUID id.

### 8. Renderer color wiring (SongDetailScreen)

Three previously hardcoded colors were wired to `AppPreferences`:

- **Chord color:** `chordAccentColor` falls back to `appPreferences.darkChordColor` / `lightChordColor` (via `parseColorSafe`) instead of null when not in a set context.
- **Lyric color:** passed as `parseColorSafe(darkLyricColor / lightLyricColor).copy(alpha)` instead of `encoreColors.lyricText`.
- **Harmony color:** `buildChordLine` now takes an explicit `harmonyColor: Color` parameter; call site passes `darkHarmonyColor` / `lightHarmonyColor` from prefs.

`parseColorSafe(hex: String): Color` is a private file-level helper using `android.graphics.Color.parseColor`, falling back to `Color.Gray`.

### 9. Touch target standardisation

All icon buttons across `EncoreHeader`, `LibraryScreen`, and `SongDetailScreen` use **60dp** `Modifier.size` on the `IconButton` wrapper, with icon visuals at 24dp. `OutlinedButton` for the song-list '+' pill is 60dp tall. `IconButton` carries Material3's built-in ripple — no custom indication needed.

---

## Files

| File | Change |
|---|---|
| `core/data/.../preferences/AppPreferences.kt` | `SongFontFamily`, `SectionStyle`, `AppPreferences` — 6 new per-theme color fields |
| `core/data/.../preferences/ThemePreset.kt` | NEW — `ThemePreset` data class |
| `core/data/.../preferences/AppPreferencesRepository.kt` | Extended — per-theme color keys, `loadPreset`/`savePreset`/`deletePreset`, JSON array helpers |
| `core/data/.../preferences/DisplayPreferences.kt` | Stripped — `DisplayPreferencesHolder` singleton removed |
| `app/.../preferences/AppPreferencesViewModel.kt` | Extended — `darkUserPresets`/`lightUserPresets` StateFlows; preset CRUD methods |
| `app/.../settings/BuiltInThemes.kt` | NEW — 5 factory presets as hardcoded constants |
| `app/.../settings/SettingsScreen.kt` | Extended — preset UI added to ThemePanel (`PresetSection`, `PresetChip`, `SavePresetDialog`) |
| `app/.../di/AppContainer.kt` | Added `appPreferencesRepository` lazy val |
| `app/.../di/ViewModelFactory.kt` | Wired `AppPreferencesViewModel` |
| `app/.../navigation/Navigation.kt` | `Routes.SETTINGS = "settings"` |
| `app/.../ui/HeaderComponent.kt` | Gear icon wired; icon buttons at 60dp; 8dp spacer around PERFORM |
| `app/.../ui/MainScreen.kt` | Settings composable in NavHost |
| `feature/library/.../LibraryScreen.kt` | '+' pill button height 60dp |
| `feature/performance/.../SongDetailScreen.kt` | Renderer wired to prefs colors; `buildChordLine` harmonyColor param; all header IconButtons at 60dp |
