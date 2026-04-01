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

## Theme Presets — COMPLETE

### What was built
- `core/data/.../preferences/ThemePreset.kt` — data class: `id`, `name`, `isBuiltIn`, `bgColor`, `lyricColor`, `chordColor`, `harmonyColor`, `sectionStyles`.
- `app/.../settings/BuiltInThemes.kt` — 5 factory presets as hardcoded constants, never stored in DataStore.
  - **Dark bank:** Midnight Mainstage, Neon Night-Shift
  - **Light bank:** Studio Daylight, Bourbon & Vinyl, Solar Flare
- `AppPreferencesRepository` extended: `DARK_USER_PRESETS` / `LIGHT_USER_PRESETS` DataStore keys; `loadPreset`, `savePreset`, `deletePreset`; `encodePresets`/`decodePresets` JSON helpers.
- `AppPreferencesViewModel` extended: `darkUserPresets`/`lightUserPresets` StateFlows; `loadPreset`, `saveCurrentAsPreset`, `deletePreset`.
- `SettingsScreen` ThemePanel preset UI: built-in chips + user preset chips; tap to select → color preview strip (BG/Lyric/Chord/Harmony swatches) → **Use Preset** button commits atomically. **Save New Preset** captures current live theme by name.

### Renderer wiring (SongDetailScreen)
- `chordAccentColor` falls back to `appPreferences.darkChordColor`/`lightChordColor` (via `parseColorSafe`) when not in a set context.
- Lyric color reads from `appPreferences.darkLyricColor`/`lightLyricColor`.
- `buildChordLine` now takes `harmonyColor: Color` parameter; passes `appPreferences.darkHarmonyColor`/`lightHarmonyColor` from call site.
- Added `parseColorSafe(hex)` private helper.

---

## Touch Target Polish — COMPLETE

All interactive icon buttons standardised to **60dp** hit targets across all three main surfaces:

| Surface | Buttons |
|---|---|
| `EncoreHeader` | Dark mode toggle, Settings gear, User avatar |
| `LibraryScreen` | '+' add-to-set pill button (60dp height) |
| `SongDetailScreen` | Close (✕), Dark mode toggle, Edit pencil |

- Icon visuals remain 24dp; the extra space is purely tap surface.
- `IconButton` carries built-in Material ripple — visual feedback consistent on all buttons.
- Spacer between PERFORM pill and icon cluster increased to 8dp on both sides to prevent misfires.

---

## Next — Library Health Tool
- Implement the Library Health Scanner under Settings → Library Tools.
- Scan for: missing metadata (no key, no artist), duplicate titles, chart formatting issues.
- Results shown in a scrollable report inside the right panel.

---

## Known Facts for Next Session
- **DataStore files:** `user_prefs` (auth), `app_prefs` (visual prefs). Do not mix.
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
