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

## Set Export / Import (Web Companion Bridge) — COMPLETE

### What was built
- **`EncoreSetExport.kt`** — data classes documenting the JSON shape (`core/data/.../transfer/`).
- **`LibraryViewModel.exportSetlistToUri(context, setlistId, outputUri)`** — serializes a setlist to `.encore.json` via SAF `CreateDocument`.
- **`LibraryViewModel.importSetFromJson(context, uri)`** — parses `.encore.json`, deduplicates songs by title+artist, creates new named setlist.
- **Export UI:** Share icon per row in the Load Set dialog → system file picker to choose save location.
- **Import UI:** "Import Set (.json)" button added to Import modal → system file picker filtered to JSON.

### Key behaviour
- Deduplication: existing songs (matched by `LOWER(title+artist)`) are reused; `markdownBody` is **not** overwritten on match — preserves local edits.
- New songs are created in the library as a side effect of import.
- Status snackbar: `Imported "Name" (N new, M matched)`.

### Spec / docs
- Full web companion integration spec: `docs/api/set-export-format.md`
- Architecture decision: `docs/decisions/004-set-export-import-format.md`

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

## Performance Context Bar — COMPLETE

### What was built
- `PerformanceContextBar` composable (private, in `SongDetailScreen.kt`) — 44dp floating card below `PerformanceDashboard`.
- Layout: **← prev pill** | **set name (set color)** | **next pill →** | 1dp divider | **HH:MM:SS live clock**
- Pills show truncated title or `"..."` when at first/last song; clicking animates pager.
- Card hidden when `setName` is empty (single-song / no-set mode).
- Scroll top padding: **84dp → 144dp**.

### ViewModel additions (`SongDetailViewModel.kt`)
- `setName: StateFlow<String>` — resolved from `setlistRepository.getSetlistWithSets()?.setlist?.name`, falls back to `"Set $N"`.
- `prevSong: StateFlow<SongEntity?>` and `nextSong: StateFlow<SongEntity?>` — populated in `loadSong()` and `onPageChanged()`.

### Key decisions
- **Set color:** `SetColor.getSetColor(setNumber)` — no DB change needed; `SetlistEntity` has no color field.
- **Set name:** threaded through ViewModel, not a composable param.

---

## Section Cards — COMPLETE

### What was built
- Each song section (Intro, Verse, Chorus, etc.) now renders as a subtle floating card in `SongContent` (`SongDetailScreen.kt`).
- The flat `sections.forEachIndexed` loop was replaced with a **grouping pass** that pairs each `SongSection.Header` with its subsequent `SongSection.Body` items.
- `SectionBodyLines` extracted as a private `@Composable` function — reused by both the card path and the headerless (pre-section) path.

### Card visual design
| Property | Value | How to tune |
|---|---|---|
| Background tint | `sectionColor.copy(alpha = 0.07f)` | Raise alpha for more presence (try 0.10–0.12) |
| Left accent bar width | `4.dp` | Increase for bolder separation |
| Left accent bar alpha | `sectionColor.copy(alpha = 0.38f)` | Raise for a more vivid stripe |
| Corner radius | `RoundedCornerShape(8.dp)` | Match to taste |
| Internal padding | `start=12dp, top=10dp, end=10dp, bottom=12dp` | Adjust breathing room |
| Gap between cards | `vp.sectionTopPaddingDp` (currently `20dp` via `ViewerPreferences`) | Change `sectionTopPaddingDp` default |

### Architecture notes
- Grouping is `remember(sections)` — recomputes only when song content changes, not on zoom.
- `sectionColor` resolved same priority order as before: `SectionStyle` map → HTML span color → `chordAccentColor` fallback.
- `lyricColor` and `harmonyColor` hoisted above the render loop (were re-parsed per line previously).
- Card is a single `Column` with `drawBehind` — NOT a `Row` + `IntrinsicSize.Min`. `drawBehind` fires after layout so the background and accent bar always match the Column's actual measured height, including at any zoom level.
- Cards only appear when a `Header` exists; body-only content at song start renders flat.

### Why drawBehind (not Row + IntrinsicSize)
`IntrinsicSize.Min` computes height before zoom is applied, causing cards to clip zoomed content. `drawBehind` draws after final measurement so it always matches actual content height.

---

## Zen UI Visual & Logic Refinement — COMPLETE

### What was built
- **Light Mode default:** `MainScreen.kt` — `isDarkMode` initialised to `false`.
- **Section card interior:** `bgColor` changed from `sectionColor.copy(alpha=0.07f)` → `encoreColors.cardBackground` (white in light / dark in dark). Only the 4dp left accent bar and heading text carry the section colour.
- **Performance Context Bar** rework:
  - Height: 44dp → **52dp**
  - Set identity label: `"Set $setNumber — $setName"` (dynamic, coloured via `SetColor.getSetColor`)
  - Nav pill corner radius: `RoundedCornerShape(50)` → `RoundedCornerShape(12.dp)`
  - Nav pill font: 11sp → 13sp; horizontal padding: 10dp → 14dp
  - Pill symmetry: each pill wrapped in `Box(Modifier.weight(1f))` — set name stays perfectly centred regardless of title lengths
  - Clock stays `FontFamily.Monospace` in a `Modifier.width(76.dp)` fixed container
- **Control Pill corner radius:** `RoundedCornerShape(50)` → `RoundedCornerShape(12.dp)`
- **Card translucency:** both `PerformanceDashboard` and `PerformanceContextBar` Surface colours use `.copy(alpha = 0.98f)` so content scrolls cleanly behind them
- **Scroll top padding:** updated 144dp → **152dp** to clear the taller context bar

### Harmony rendering
- All `TextDecoration.Underline` removed from harmony lines and `[h]...[/h]` inline spans
- Harmony lines (`isHarmonyLine=true`): `background = harmonyColor.copy(alpha = 0.18f)` applied to full line
- Inline `[h]text[/h]` spans: `background = harmonyColor.copy(alpha = 0.22f)` on the span only
- Both use `harmonyColor` from `AppPreferences` (dark/light display settings)

---

## Song Edit Sheet — Zoom Reset + Clear Harmonies — COMPLETE

### What was built
- **`SongEditBottomSheet`** (in `feature/library/LibraryScreen.kt`): two toggle buttons between the Harmony Mode switch and Save.
  - **Zoom Reset** (blue arm colour) — when armed + Save: sets `lastZoomLevel = 1.0f` on the entity.
  - **Clear Harmonies** (red arm colour) — when armed + Save: strips all `[h]`/`[/h]` tags from `markdownBody` via `Regex("""\[/?h\]""")`, keeping inner text.
  - Both default unarmed; reset to unarmed when sheet opens for a different song.
- **`LibraryViewModel.updateSongMetadata`** extended with `resetZoom: Boolean` and `clearHarmonies: Boolean`; both applied in a single `upsertSong` call (no race condition).
- `onSave` lambda signature expanded at both call sites (`LibraryScreen.kt` and `MainScreen.kt`).

---

## Known Facts for Next Session
- **DataStore files:** `user_prefs` (auth), `app_prefs` (visual prefs). Do not mix.
- **DB version:** 5
- **`SetlistDetailScreen.kt`** — do not touch (user does not use it)
- **`SongEditBottomSheet`** is in `feature/library`, imported by `app` module. `onSave` now has 6 params: title, artist, isLeadGuitar, isHarmonyMode, resetZoom, clearHarmonies.
- **`SongChartEditorScreen`** in `feature/library` → `Routes.SONG_CHART_EDITOR = "chart_editor/{songId}"`
- **Build filter:** `./gradlew assembleDebug 2>&1 | grep -E "FAILED|error:|BUILD SUCCESSFUL"`
- **ADB path:** `~/Library/Android/sdk/platform-tools/adb`
- **Settings entry point:** Gear icon in `EncoreHeader` → `Routes.SETTINGS = "settings"` composable in `MainScreen.kt` NavHost
- **Performance card scroll padding:** currently `152dp` in `SongDetailScreen.kt` (8dp + 68dp dashboard + 8dp + 52dp context bar + 8dp gap)
- **Light mode is now the default** — `MainScreen.kt:126` `mutableStateOf(false)`
- **Icon sizes:** all `IconButton` = 60dp hit target, icon visual = 20-24dp
- **Guitar pick icon:** `feature/performance/src/main/res/drawable/ic_guitar_pick.xml`
- **Harmony highlight:** `SpanStyle(background = harmonyColor.copy(alpha))` — no underline anywhere

## Remaining M4 Sync Work
- Single active device session policy
- Wire `SyncStatus` to real Ktor API calls
- Manual **Sync Now** action
- Conflict detection and conflict-resolution UI
- Setlist management screen
