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

## Phase 4 — Horizontal Setlist Engine — COMPLETE

### What was built
- **In-memory staging via Set 1 DB:** `addToPerformSet(songId)` stages directly (no dialog); status message "Staged (N in set)" shown in library.
- **SAVE SET:** Name dialog in header → `saveCurrentSetAs(name)`.
- **LOAD SET:** Setlist picker dialog in header → `loadSetlistAsCurrent(setlistId)`.
- **HorizontalPager nav in performance mode** with swipe between songs; "Page X of Y" fade indicator auto-hides after 2s.
- **Bug fixes:** Ghost delete (swipe positionalThreshold → 75%), header overlap in performance mode, scroll jank in library (`buildAnnotatedString` wrapped in `remember`), sets tray `navigationBarsPadding()`.

---

## Performance Dashboard — COMPLETE (Floating Card)

### Architecture
- Replaced slim header Row with `PerformanceDashboard` composable — a floating `Surface(RoundedCornerShape(12dp))` card pinned at top.
- Card height: **68dp**, floats with **8dp** inset on all sides, 1dp `divider`-color border matching library card style.
- Song content scroll top padding: **84dp** (8dp gap + 68dp card + 8dp content gap).

### Layout (left → right inside card)
- **Key Anchor:** `background(harmonyColor×13%)` + `border(1dp, harmonyColor×35%)`, root 20sp ExtraBold Monospace, scale 9sp below
- **Identity:** title 16sp Bold Monospace + artist bodySmall (weight(1f))
- **Status Pill:** `Surface(RoundedCornerShape(50))` — guitar pick icon (20dp, harmony tinted, `ic_guitar_pick.xml`) + BPM column. 12dp spacer between pill and divider.
- **Transposition warning:** ⚠ emoji (amber) if `displayKey ≠ originalKey`
- **1dp vertical divider** (60% height)
- **12dp spacer** on both sides of divider (equal, symmetric)
- **Control Pill:** `Surface(RoundedCornerShape(50))` — three 60dp `IconButton`s (☀/🌙, ✏, ✕) with 20dp icons

### Data parsers (private, in SongDetailScreen.kt)
- `parseBpm(markdown)` — regex for `**BPM:** 120` / `**Tempo:** 120`
- `splitKey(displayKey)` — splits "Dm" → ("D","m"), "D Major" → ("D","maj")
- `stripLeadingTitle(markdown, title)` — drops first non-blank line if it matches song title

### Resources
- `feature/performance/src/main/res/drawable/ic_guitar_pick.xml` — custom 24×24 vector: rounded-triangle pick body + music note cutout, `fillType="evenOdd"`, works at any tint color.
- `feature/performance/src/main/res/drawable/ic_electric_guitar.xml` — previous guitar silhouette (superseded by pick icon, kept for reference).

---

## Next Feature — Performance Mode Context Bar (PLANNED, not built)

### What it is
A **second floating card** above the existing Performance Dashboard showing set-level context and navigation.

### Layout (left → right)
- **Previous Song pill** (← arrow + truncated song title, or "..." if first song in set)
- **Set Name** centered with set color
- **Next Song pill** (truncated song title + → arrow, or "..." if last song in set)
- **Current Time** — right-aligned, centered under the Control Pill column, live clock updating every second

### Key implementation notes
- Prev/Next wired to `pagerState` — `coroutineScope.launch { pagerState.animateScrollToPage(page ± 1) }`
- Current time: `LaunchedEffect(Unit) { while(true) { currentTime = ...; delay(1000) } }`
- **Set name threading:** Need to verify if `setName` is passed to `SongDetailScreen` or needs to be added to ViewModel/param chain
- **Set color:** Need to check if `SetlistEntity` has a color field before assuming it can be shown
- Scroll top padding will increase from **84dp → ~144dp** (additional ~52dp card + gap)
- Edge case: first song → left pill disabled/"..."; last song → right pill disabled/"..."

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
- **Performance card scroll padding:** currently `84dp` in `SongDetailScreen.kt`
- **Icon sizes:** all `IconButton` = 60dp hit target, icon visual = 20-24dp
- **Guitar pick icon:** `feature/performance/src/main/res/drawable/ic_guitar_pick.xml`

## Remaining M4 Sync Work (after UI polish)
- Single active device session policy
- Wire `SyncStatus` to real Ktor API calls
- Manual **Sync Now** action
- Conflict detection and conflict-resolution UI
- Setlist management screen
