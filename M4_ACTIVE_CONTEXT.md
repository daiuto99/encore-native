# Encore Android — M4 Active Context

## Current Milestone
Milestone 4 — Sync + Account Behavior

## Milestone Goal
Add cloud-backed account and sync behavior without breaking the offline-first local product.

## What Is Already Done

### Completed foundation for M4
- **Performance Mode (v1):** HorizontalPager, Pinch-to-zoom (0.5x–3.0x) with DB persistence, ChordSidekick parser functional.
- **Set Integrity:** Sets 1–4 auto-initialized in DB.
- Google sign-in/out, auth state, `ownerId`, session persistence via DataStore.
- Import flow, global dark background, adaptive song-row colors.
- Gesture/zoom polish, drag-to-reorder, set sort order.

## Zen UI — Phase 1 COMPLETE
- `EncoreTheme.kt`, Dark/Light toggle, Zen Cards, left accent bars, SetColor pastels.

## Schema & Logic Alignment — COMPLETE
- DB v5: `display_key`, `original_key`, `is_lead_guitar`, `is_verified`, `last_verified_at`.
- Performance Header badges (Not Original Key amber, Lead Guitar icon).
- Edit Modal cleaned up; "Edit Chart" button added.

## Chart Editor Screen — COMPLETE
- `SongChartEditorScreen`: `BasicTextField` + `TextFieldValue`, cursor-safe `[h]` insertion.
- Harmony DSL multi-line fix in `SongDetailScreen`.
- Formatting toolbar in TopAppBar; selection caching; `focusManager.clearFocus()` on exit.
- `SongEditBottomSheet`: "Select All" suppressed via `LocalTextToolbar`; Exit button added.

## Global Preference Engine — COMPLETE

### What was built
- `AppPreferences` data class — all global display settings in one place.
- `SectionStyle` data class — per-section color (`hexColor`), `fontSize`, `isBold`.
- `SongFontFamily` enum — `SANS_SERIF` / `MONOSPACE` with `displayName`.
- `AppPreferencesRepository` — Jetpack DataStore backed, own `app_prefs` file (separate from `user_prefs` auth store). Device-scoped; survives sign-out.
- `AppPreferencesViewModel` — `StateFlow<AppPreferences>` via `SharingStarted.Eagerly`; thin write-helper wrappers.
- `promoteToGlobal(song, isDarkTheme)` — theme-aware: only updates dark or light background key based on current system theme.
- `SongDetailScreen` wired to receive `AppPreferences` as a parameter; renders `chordSpacing`, `showLeadIndicator`, `showTranspositionWarning`, `showChords`, `showKeyInfo`.

### Key architecture decisions
- **Separate `app_prefs` DataStore file** — not shared with `user_prefs` (auth). Visual preferences are device-scoped, not user-scoped.
- **Per-theme section styles** — `darkSectionStyles` and `lightSectionStyles` stored as separate JSON keys (`ap_dark_section_styles_json` / `ap_light_section_styles_json`). The performance renderer selects the correct map based on `encoreColors.isDark` at parse time, so colors switch automatically with the dark/light toggle.
- **JSON serialization via `org.json`** — no extra dependency; encode/decode helpers shared across both style maps.
- **`DisplayPreferencesHolder` deleted** — replaced by DataStore-backed flow. `DisplayPreferences.kt` retained as constants-only object.

---

## Settings Screen — COMPLETE

### What was built
- `app/src/main/kotlin/com/encore/tablet/settings/SettingsScreen.kt` — full two-pane tablet layout.
- **Left pane (220dp):** Back button + category nav list with active-state highlight.
- **Right pane:** Panel content for selected category.
- Wired via `Routes.SETTINGS = "settings"` in the NavHost. Gear icon in `EncoreHeader` navigates to it.

### Categories
| Category | Content |
|---|---|
| **Theme** (default) | Dark/Light mode **tab switcher** (not a long list). Each tab: background color hex input + full section styles matrix (color swatches, hex input, font-size slider, bold toggle per section). |
| **Typography & Rhythm** | Lyric Size slider (10–24sp), Chord Spacing slider (0–24dp), Font Family toggle (Sans-Serif / Monospace). |
| **Performance HUD** | Toggles for showLeadIndicator, showTranspositionWarning, showChords, showKeyInfo. |
| **Library Tools** | Placeholder: Library Health Scanner (disabled button, Phase 5). |

---

## Next Session — Theme Presets

### What to build
A save/load preset system for the Theme panel. Designed, not yet built.

### Architecture (ready to implement)

**Data model:**
```kotlin
data class ThemePreset(
    val id: String,      // UUID or slugified name for built-ins
    val name: String,
    val bgColor: String,
    val sectionStyles: Map<String, SectionStyle>,
    val isBuiltIn: Boolean = false   // built-ins are read-only, never stored in DataStore
)
```

**Storage:** Single DataStore key `ap_theme_presets_json` — JSON array of user-saved presets. Built-in presets are hardcoded constants (no storage needed).

**Built-in presets (3, ship with app):**
- `Stage` — true black background, high-contrast vivid section colors
- `Rehearsal` — system light grey background, softer section colors
- `High Contrast` — OLED black, maximum brightness section colors for harsh lighting

Built-ins are read-only (no delete button). User presets appear in a separate "Saved" section with delete. Loading any preset calls the same write path into DataStore.

**Key open question:** Are presets per-mode (dark presets apply to dark tab, light presets to light tab) or mode-agnostic (one preset sets both)?  Per-mode is simpler and matches the current tab structure.

**Estimated scope:** ~200–250 lines. One session. No new Room table, no new navigation, no new ViewModel class.

**Files to create/modify:**
- `core/data/.../preferences/ThemePreset.kt` — NEW
- `AppPreferencesRepository.kt` — add `ap_theme_presets_json` key + CRUD methods
- `AppPreferencesViewModel.kt` — add `savePreset`, `loadPreset`, `deletePreset`
- `SettingsScreen.kt` — add preset UI to ThemePanel (chips/rows, save dialog, built-in section)

---

## Known Facts for Next Session
- **DataStore files:** `user_prefs` (auth, `UserPreferencesRepository`), `app_prefs` (visual prefs, `AppPreferencesRepository`). Do not mix.
- **DB version:** 5
- **`SetlistDetailScreen.kt`** — do not touch (user does not use it)
- **`SongEditBottomSheet`** is in `feature/library`, imported by `app` module
- **`SongChartEditorScreen`** in `feature/library` → `Routes.SONG_CHART_EDITOR = "chart_editor/{songId}"`
- **Build filter:** `./gradlew assembleDebug 2>&1 | grep -E "FAILED|error:|BUILD SUCCESSFUL"`
- **ADB path:** `~/Library/Android/sdk/platform-tools/adb`
- **Settings entry point:** Gear icon in `EncoreHeader` → `Routes.SETTINGS = "settings"` composable in `MainScreen.kt` NavHost

## Remaining M4 Sync Work (after Settings polish)
- Single active device session policy
- Wire `SyncStatus` to real Ktor API calls
- Manual **Sync Now** action
- Conflict detection and conflict-resolution UI
- Setlist management screen
