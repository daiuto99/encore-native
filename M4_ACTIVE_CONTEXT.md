# Encore Android — M4 Active Context

## Current Milestone
Milestone 4 — Sync + Account Behavior

## Milestone Goal
Add cloud-backed account and sync behavior without breaking the offline-first local product.

---

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
- **Session lock:** `LaunchedEffect(songId)` calls `requestEditLock`; `DisposableEffect` releases on exit. Amber "Read Only — locked by [owner]" banner shown when `isLockedByOther = true`; Save/Cancel hidden and `readOnly = true` on text field.

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
- **Per-theme section styles** — `darkSectionStyles` and `lightSectionStyles` stored as separate JSON keys. The performance renderer selects the correct map based on `encoreColors.isDark` at parse time.
- **JSON serialization via `org.json`** — no extra dependency; encode/decode helpers shared across both style maps.

---

## Settings Screen — COMPLETE

### Categories
| Category | Content |
|---|---|
| **Theme** (default) | Dark/Light tab switcher. Each tab: background color hex input, **Lead Guitar Icon** + **Capo Badge** color hex inputs, full section styles matrix. |
| **Typography & Rhythm** | Lyric Size slider (10–24sp), Chord Spacing slider (0–24dp), Font Family toggle. |
| **Performance HUD** | Toggles: showLeadIndicator, showTranspositionWarning, showChords, showKeyInfo. **Song Title & Artist Colors** card (global hex overrides for title/artist; blank = use set color). |
| **Library Tools** | Library Health Scanner + Cloud Sync (Sync Now button). |

---

## Theme Presets — COMPLETE
- `ThemePreset.kt` — data class: `id`, `name`, `isBuiltIn`, `bgColor`, `lyricColor`, `chordColor`, `harmonyColor`, `sectionStyles`.
- `BuiltInThemes.kt` — 5 factory presets (Midnight Mainstage, Neon Night-Shift, Studio Daylight, Bourbon & Vinyl, Solar Flare).
- `AppPreferencesRepository` extended: `DARK_USER_PRESETS` / `LIGHT_USER_PRESETS` DataStore keys; `loadPreset`, `savePreset`, `deletePreset`.

---

## Performance Dashboard — COMPLETE (Floating Card, second bar)

### Layout (left → right inside card)
- **Key Anchor:** `background(harmonyColor×13%)` + `border(1dp, harmonyColor×35%)`, root 20sp ExtraBold Monospace, scale 9sp below
- **Identity:** title **19sp** Bold Monospace (color = `titleColorOverride ?? setColor`) + artist bodySmall (color = `artistColorOverride ?? encoreColors.artistText`)
- **Status Pill:** `Surface(RoundedCornerShape(50))` — guitar pick icon (20dp, `leadIconColor` tinted) + CAPO badge (fret number + "CAPO" label, `capoColor` tinted, shown when `song.capoEnabled`) + BPM column.
- **Transposition warning:** ⚠ emoji (amber) if `displayKey ≠ originalKey`
- **1dp vertical divider** (60% height)
- **Control Pill:** `Surface(RoundedCornerShape(12.dp))` — three 60dp `IconButton`s (☀/🌙, ✏, ✕)

### Icon colors (per-theme in AppPreferences)
- `darkLeadIconColor` / `lightLeadIconColor` — guitar pick icon tint (default = harmony color)
- `darkCapoColor` / `lightCapoColor` — capo fret number + label tint (default = harmony color)
- Configurable in **Settings → Theme** alongside background and section colors.

---

## Performance Context Bar — COMPLETE (top bar, first bar)

### What was built
- `PerformanceContextBar` composable — 52dp floating card **above** `PerformanceDashboard`.
- Layout: **← prev pill** | **SET N** (set color, uppercase, bold, letter-spaced) | **next pill →** | 1dp divider | **HH:MM:SS live clock** (or sync HUD when active)
- Set label is `"SET $setNumber"` only — no setlist name shown.
- Pills show truncated title or `"..."` when at first/last song; clicking animates pager.
- Card hidden when `setName` is empty (single-song / no-set mode).

### Bar order (top → bottom)
1. `PerformanceContextBar` — navigation, SET N label, clock
2. `PerformanceDashboard` — song title, key anchor, status pill, controls

---

## Sync Engine Spike — COMPLETE

### What was built
- `FileHashUtils.kt` — MD5 of `markdownBody.trimEnd()`; suspend + sync variants.
- `EncoreApiService.kt` — interface + `RemoteHashResponse` data class.
- `FakeSyncProvider.kt` — `SyncScenario` enum + `LockResult` sealed class (`Acquired` / `LockedBy(owner)`); per-song overrides for sync scenario and lock state.
- `ContentSyncStatus.kt` — sealed class: `UpToDate`, `LocalAhead`, `RemoteAhead(remoteHash)`, `Conflict(localHash, remoteHash)`, `NeverSynced`.
- `SyncHudState.kt` — sealed class: `InProgress(current, total)`, `Complete`.
- `SongRepository.checkSyncStatus()`, `markSynced()`, `requestEditLock()`, `releaseEditLock()`.
- `ConflictResolutionDialog.kt` scaffold — 400dp, two-column diff, "Keep Local" / "Keep Remote" / "Decide Later".

### Auto-sync on start
- `LibraryViewModel.init` calls `autoSyncOnStart()` — reads `userPrefs.getLastSyncTimestamp()`, skips if fewer than 10 minutes have passed, else runs `triggerGlobalSync()` and writes new timestamp.

### Session lock
- `SongChartEditorScreen` requests lock on open via `LaunchedEffect(songId) { viewModel.requestEditLock(songId) }`.
- `DisposableEffect` releases lock on exit.
- Lock timeout: 5 seconds — if no server response, `Acquired` is returned silently (offline safety).
- `isLockedByOther` persisted to DB; drives "Read Only" banner in editor.

---

## SongListItem Badges — COMPLETE
- `⚠` badge shown when `song.syncStatus == SyncStatus.CONFLICT`.
- `🔒` badge shown when `song.isLockedByOther == true`.
- Tapping a CONFLICT song shows an `AlertDialog` ("Open Anyway" / "Decide Later") before navigating.

---

## Song Edit Sheet — COMPLETE

### Current `onSave` signature (8 params)
```
title: String, artist: String, isLeadGuitar: Boolean, isHarmonyMode: Boolean,
resetZoom: Boolean, clearHarmonies: Boolean, capoEnabled: Boolean, capoFret: Int
```

### Controls in sheet
- Title / Artist text fields
- Lead Guitar toggle
- Harmony Mode toggle
- Zoom Reset button (arms blue → clears `lastZoomLevel` to 1.0f on Save)
- Clear Harmonies button (arms red → strips `[h]`/`[/h]` tags on Save)
- **Capo toggle** — shows/hides fret stepper (1–12)
- **Capo fret stepper** — `+` / `−` IconButtons, fret displayed as large number

### Capo architecture
- Per-song: `capoEnabled: Boolean` and `capoFret: Int` on `SongEntity` (DB v9 migration).
- `LibraryViewModel.updateSongMetadata` accepts and persists both fields.
- `PerformanceDashboard` reads directly from `song.capoEnabled` / `song.capoFret`.

---

## Add-to-Set Picker — COMPLETE
- Tapping `+` on a song opens an `AlertDialog` listing all available sets.
- Sets the song is already in are grayed out and disabled.
- Selection calls `viewModel.addSongToSetNumber(songId, setNumber)`.

---

## DB Schema — Current Version: 9

| Migration | Change |
|---|---|
| 1→2 | `last_zoom_level` |
| 2→3 | `owner_id` |
| 3→4 | `is_harmony_mode`, `highlight_style` |
| 4→5 | Rename `current_key` → `display_key`; add `original_key`, `is_lead_guitar`, `is_verified`, `last_verified_at` |
| 5→6 | `validation_errors` |
| 6→7 | `last_synced_hash`, `is_dirty` |
| 7→8 | `is_locked_by_other` |
| 8→9 | `capo_enabled`, `capo_fret` |

---

## Known Facts for Next Session
- **DataStore files:** `user_prefs` (auth), `app_prefs` (visual prefs). Do not mix.
- **DB version:** 9
- **`SetlistDetailScreen.kt`** — do not touch (user does not use it)
- **`SongEditBottomSheet`** `onSave` has **8 params**: title, artist, isLeadGuitar, isHarmonyMode, resetZoom, clearHarmonies, capoEnabled, capoFret
- **`SongChartEditorScreen`** in `feature/library` → `Routes.SONG_CHART_EDITOR = "chart_editor/{songId}"`
- **Build filter:** `./gradlew assembleDebug 2>&1 | grep -E "FAILED|error:|BUILD SUCCESSFUL"`
- **ADB path:** `~/Library/Android/sdk/platform-tools/adb`
- **Performance card scroll padding:** `152dp` in `SongDetailScreen.kt`
- **Light mode is the default** — `MainScreen.kt` `isDarkMode = mutableStateOf(false)`
- **Icon sizes:** all `IconButton` = 60dp hit target, icon visual = 20-24dp
- **Guitar pick icon:** `feature/performance/src/main/res/drawable/ic_guitar_pick.xml`
- **Bar order:** ContextBar (top) → Dashboard (below); both in a `Column` aligned to `Alignment.TopStart`
- **Song title color:** `appPreferences.titleColorOverride ?: setColor`; 19sp Bold Monospace
- **Set label:** `"SET $setNumber"` uppercase, set color, `letterSpacing = 1.2.sp`
- **Capo icon colors:** `darkLeadIconColor` / `lightLeadIconColor` (guitar pick), `darkCapoColor` / `lightCapoColor` (capo badge) — in Theme settings alongside bg color

## Remaining M4 Sync Work
- Wire `SyncStatus` to real Ktor API calls (replace FakeSyncProvider)
- Full conflict resolution flow (write winner to DB, call `markSynced()`)
- Single active device session enforcement on server side
- Setlist management screen
