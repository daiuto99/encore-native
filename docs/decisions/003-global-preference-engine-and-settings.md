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

### 7. Built-in theme presets (designed, not yet built)

Three factory presets will ship with the app as hardcoded constants (`BuiltInThemes` object). They are never written to DataStore — loading one calls the same write path as user-created presets. User-saved presets are stored under `ap_theme_presets_json` as a JSON array.

Built-ins are rendered in a separate "Built-in" UI section with no delete button. User presets appear in a "Saved" section with delete. The design question of per-mode vs. mode-agnostic presets is deferred to implementation time; per-mode is preferred to match the current tab structure.

---

## Files

| File | Change |
|---|---|
| `core/data/.../preferences/AppPreferences.kt` | NEW — `SongFontFamily`, `SectionStyle`, `AppPreferences` data classes |
| `core/data/.../preferences/AppPreferencesRepository.kt` | NEW — DataStore-backed repo, JSON helpers, `promoteToGlobal` |
| `core/data/.../preferences/DisplayPreferences.kt` | Stripped — removed `DisplayPreferencesHolder` singleton; kept constants only |
| `app/.../preferences/AppPreferencesViewModel.kt` | NEW — `StateFlow<AppPreferences>`, write-helper wrappers |
| `app/.../di/AppContainer.kt` | Added `appPreferencesRepository` lazy val |
| `app/.../di/ViewModelFactory.kt` | Wired `AppPreferencesViewModel`; added `AppPreferencesRepository` to `LibraryViewModel` |
| `app/.../settings/SettingsScreen.kt` | NEW — two-pane Settings UI |
| `app/.../navigation/Navigation.kt` | Added `Routes.SETTINGS = "settings"` |
| `app/.../ui/HeaderComponent.kt` | Gear icon wired to `onSettingsClick` callback |
| `app/.../ui/MainScreen.kt` | Settings composable in NavHost; `onSettingsClick` callback threaded through |
| `feature/performance/.../SongDetailScreen.kt` | `AppPreferences` parameter replaces `DisplayPreferencesHolder`; `isDark` passed to `parseSongSections` |
