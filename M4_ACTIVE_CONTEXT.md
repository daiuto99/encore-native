# Encore — Active Context (M5 complete → Web Library Management)

## Current Work
**Web Library Management + Library Health Suggested Fixes**
Branch: `milestone-5/ui-redesign`

### M5 Status: ✅ COMPLETE (committed 2026-04-27, commit `98691f0`)
See `docs/milestones/M5_SUMMARY.md` for full details.

## Recently Completed
### Web: Library Health + Quick Edit (2026-04-27)
- `checkSongHealth`: chord annotations (`[Am]`, `[G7]`) no longer flagged as non-standard sections
- `normalizeSectionKey`: strips leading `#`, ordinals (`1st`, `2nd`), trailing digits before matching
- `KNOWN_SECTIONS` expanded: post-chorus, guitar solo, fade out, riff, fill, ending
- `suggestFixes`: one-click fix buttons per issue — title from filename/body heading, artist from body annotation, key from first chord, unclosed [h] tag removal, section renames with alias map
- "Fix all (N)" button applies every available fix across all flagged songs in one pass
- **Quick Edit modal**: title, artist, key (major+minor dropdown), BPM, lead guitar toggle, capo toggle + fret stepper
  - Opens from ✏ Edit button on health tab rows and sidebar hover pencil
  - "Delete" button with confirmation screen — removes from GCS + songs list
  - "+ New" button in sidebar creates blank song and opens Quick Edit
- `CloudLibraryService.deleteFile(path, token)` — GCS DELETE with 404 tolerance
- Toggles use inline `style` (not Tailwind classes) to avoid JIT purge of dynamic class names

### Web: Dark/Light Theme + Auth Fix (2026-04-27)
- Light mode default, Sun/Moon toggle, `darkMode: 'class'`
- Reconnect button replaces "Session expired" warning

## Next
- Bulk song upload (drag-drop multiple .md files)
- Song body editor improvements

---

## Prior Milestone Goal (M4 — COMPLETE)
Sync + Account Behavior — cloud-backed account and sync without breaking offline-first product.

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

## Performance Bar — COMPLETE (Tasks 4.6–4.11)

### Shipped layout
```
Row 1 (52dp): [SET 1] [SET 2] [SET 3]  •  9:45 PM  ☀  [  🔍  ]  ✏  ✕
Row 2 (80dp):  ← Prev Song    [G]  SONG TITLE · Artist  [status pill]    Next Song →
```

### Row 1 details
- **SET tabs:** outlined pill only (`RoundedCornerShape(50)`), no fill. Active = set color border (80% alpha) + set color text. Inactive = dim gray border + dim text.
- **Clock:** 12-hour AM/PM, updates every 30s. Replaced by sync HUD when syncing.
- **Dark mode toggle:** ☀/🌙 `IconButton` (48dp).
- **Search:** 72dp×48dp wide `Box` — extra hit zone.
- **Edit ✏:** `IconButton` (48dp) — only shown when `setNumber <= 0` (song detail / library mode). Navigates directly to chart editor.
- **Exit ✕:** 48dp `IconButton` at far right. No overflow `···` menu.

### Row 2 details (Box layout — title truly centered)
- **Container:** `Box` (not `Row`) so center group floats independently of prev/next width.
- **Prev/Next pills:** outlined pill (`RoundedCornerShape(50)`), no fill, 1dp dim border, 12sp text, 16dp chevron. Anchored `CenterStart` / `CenterEnd`. `widthIn(max = 200.dp)`.
- **Center group:** `Row` with `fillMaxWidth().padding(horizontal = 210.dp)` + `horizontalArrangement = Arrangement.Center`. Contains: key circle + title + artist + status pill + transposition warning.
- **Key circle:** 54dp filled circle, set color. Root + scale label in white. Hidden when no key.
- **Song title:** 22sp SemiBold, `widthIn(max = 320.dp)`, `titleColorOverride ?: setColor`. Ellipsis on overflow.
- **Artist:** 15sp Normal, `widthIn(max = 180.dp)`. Hidden when "Unknown Artist".
- **Status pill:** `RoundedCornerShape(50)` — lead icon + capo + BPM (tappable tap tempo).
- **Bottom divider:** 2dp `HorizontalDivider` using `setColor.copy(alpha = 0.55f dark / 0.40f light)` — creates visual bridge to first section.

### Architecture
- Single `PerformanceBar` composable (~line 839 in `SongDetailScreen.kt`).
- `onEditChart: ((String) -> Unit)?` param on `SongDetailScreen` — wired in `MainScreen.kt` to `Routes.chartEditor(songId)`.
- `stripLeadingTitle()` removes `# Title`, `[Title]`, and parenthetical variants from body before render.

### Icon colors (per-theme in AppPreferences)
- `darkLeadIconColor` / `lightLeadIconColor` — guitar pick tint
- `darkCapoColor` / `lightCapoColor` — capo badge tint
- Configurable in Settings → Theme.

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

### Current `onSave` signature (10 params)
```
title: String, artist: String, isLeadGuitar: Boolean, isHarmonyMode: Boolean,
resetZoom: Boolean, clearHarmonies: Boolean, capoEnabled: Boolean, capoFret: Int,
displayKey: String?, bpm: Int?
```

### Controls in sheet
- Title / Artist text fields
- Lead Guitar toggle
- Harmony Mode toggle
- Zoom Reset button (arms blue → clears `lastZoomLevel` to 1.0f on Save)
- Clear Harmonies button (arms red → strips `[h]`/`[/h]` tags on Save)
- **Capo toggle** — shows/hides fret stepper (1–12)
- **Capo fret stepper** — `+` / `−` IconButtons, fret displayed as large number
- **Key transposition stepper** — `−` / key / `+` ; sharp going up, flat going down; reset button if changed
- **BPM field** — numeric `OutlinedTextField` (max 3 digits); pre-populated from markdown body; saves `bpm: N` line to body

### Capo architecture
- Per-song: `capoEnabled: Boolean` and `capoFret: Int` on `SongEntity` (DB v9 migration).
- `LibraryViewModel.updateSongMetadata` accepts and persists both fields.
- `PerformanceDashboard` reads directly from `song.capoEnabled` / `song.capoFret`.

### Key transposition architecture
- `originalKey` (immutable, import-time) / `displayKey` (mutable user preference) on `SongEntity`.
- `TranspositionUtils.kt` in `feature/performance`: `semitoneShift`, `transposeBody`, `isChordLine`.
- `SongDetailScreen` `SongContent` block: `semitones = semitoneShift(originalKey, displayKey)` → `transposeBody` applied before `parseSongSections`.
- `stepKey(key, delta)` private fun in `LibraryScreen.kt`: +1 uses sharps, -1 uses flats.
- Backfill: `LibraryViewModel.backfillMissingKeys()` sets `originalKey = song.originalKey ?: key` on init.
- Key picker guard: `val baseKey = song.originalKey ?: song.displayKey` — shows stepper even when only `displayKey` is set.

### BPM architecture
- BPM lives in markdown body only — no DB column.
- `parseBpmFromBody()` private fun in `LibraryScreen.kt` — same regex as `parseBpm` in `SongDetailScreen`.
- `LibraryViewModel.writeBpmToMarkdown()` — replaces existing BPM line or inserts after first non-blank line.
- `SongDetailViewModel.writeBpmToMarkdown()` — same logic, used by `saveTapBpm`.

### Tap tempo architecture
- `SongDetailViewModel`: `_tapTimestamps: MutableStateFlow<List<Long>>`, `tapBpm: StateFlow<Int?>` (rolling 4-tap avg), `recordTap()`, `clearTapTempo()`, `saveTapBpm(bpm: Int)`.
- 3s inactivity auto-resets tap sequence.
- `PerformanceDashboard` params: `tapBpm`, `onTap`, `onSaveTapBpm`.
- BPM column in status pill: tappable (`clickable { onTap() }`); shows live tap BPM (primary color) + "TAP" label while tapping; ✓ `IconButton` appears to save.
- `saveTapBpm` upserts song + updates `_song.value` directly for immediate dashboard refresh.

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

## Track 4 Design Strategy — UI Rework

### References
- **Primary:** Original Encore Replit prototype → [github.com/daiuto99/Encore](https://github.com/daiuto99/Encore)
- **Secondary:** Apple Music

### Design Direction
The current app reads as a utility screen (true black + flat graphite, monospace titles, sets buried at the bottom). The target is a deep, layered feel — like a real music product.

**The three systemic problems and their fixes:**

| Problem | Root Cause | Fix |
|---|---|---|
| Screen feels shallow / no depth | True black background; no surface hierarchy | Deep navy palette: `#060E1F` bg, `#0D1829` cards |
| Titles feel like a terminal | Monospace Bold used for song title + artist | Sans-serif for title/artist; Monospace only for chords, key root, BPM |
| Sets feel disconnected / buried | Sets panel at bottom of library; no forward flow | Sets tabs sticky at top, shown contextually by mode |
| Performance top area clunky | Two chrome rows eat real estate; no action priority | Collapse to one smart bar; secondary actions behind tap |

### What to Keep (do not change)
- Left blue accent bars on library song rows
- Key pill + info badge on the right of library song rows
- Key anchor box (letter + scale) in performance view top-left
- Left accent bars on song detail section blocks
- Theme preset system — keep as power-user feature; fix only the defaults

### Phase Sequence
1. **Phase 1 (visual system)** — palette + typography + logo. Confirm on device before Phase 2.
2. **Phase 2 (sets nav)** — move sets to top, contextual visibility, set membership clarity in library.
3. **Phase 3 (performance consolidation)** — single bar, action priority. Requires confirming on-stage action list with user first.

### Phase 3 Confirmed Bar Layout
```
┌────────────────────────────────────────────────────────┐
│  SET 1   SET 2   SET 3        8:36 PM      🔍   ···   │  ← Row 1: sticky
└────────────────────────────────────────────────────────┘
 ← Prev Song Name                    Next Song Name →       ← Row 2: nav
```
- **Left:** Set tabs (always visible)
- **Center:** Live clock
- **Right:** Search icon (for song requests), overflow `···` → Dark/Light + Exit
- **Row 2:** Prev/Next with adjacent song names
- **Removed from chrome:** Edit song (library only)
- **Moved into chart:** Key anchor, BPM, capo badge, guitar pick — small header at top of song content

---

## Known Facts for Next Session
- **DataStore files:** `user_prefs` (auth), `app_prefs` (visual prefs). Do not mix.
- **DB version:** 9
- **`SetlistDetailScreen.kt`** — do not touch (user does not use it)
- **`SongEditBottomSheet`** `onSave` has **10 params**: title, artist, isLeadGuitar, isHarmonyMode, resetZoom, clearHarmonies, capoEnabled, capoFret, displayKey, bpm
- **`SongChartEditorScreen`** in `feature/library` → `Routes.SONG_CHART_EDITOR = "chart_editor/{songId}"`
- **Build filter:** `./gradlew assembleDebug 2>&1 | grep -E "FAILED|error:|BUILD SUCCESSFUL"`
- **ADB path:** `~/Library/Android/sdk/platform-tools/adb`
- **Performance chart scroll padding:** `128dp` in `SongDetailScreen.kt` (was 152dp pre-4.6)
- **Light mode is the default** — `MainScreen.kt` `isDarkMode = mutableStateOf(false)`
- **Performance bar touch targets:** Row 1 icons = 48dp; Row 2 prev/next = 56dp; search = 72dp×48dp wide Box
- **Guitar pick icon:** `feature/performance/src/main/res/drawable/ic_guitar_pick.xml`
- **Performance chrome:** Single `PerformanceBar` composable (~line 856 in `SongDetailScreen.kt`). Dead `PerformanceContextBar` + `PerformanceDashboard` stubs below it — remove in 4.7 cleanup.
- **Song title in bar:** 22sp SemiBold Default; color = `titleColorOverride ?: setColor`
- **Set switching in performance:** `viewModel.getFirstSongIdForSet(n, callback)` → `onNavigateToSongInSet(songId, n)`
- **Capo icon colors:** `darkLeadIconColor` / `lightLeadIconColor` (guitar pick), `darkCapoColor` / `lightCapoColor` (capo badge) — in Theme settings
- **V1.1 status:** Tracks 1–5 fully COMPLETE. Track 6 doable tasks (6.3, 6.4) COMPLETE. Remaining: 6.1/6.2 (backend proxy auth) — deferred, blocked on backend work.
- **AppConstants.LOCAL_USER_ID** — `"local-user"` placeholder constant in `core/data/AppConstants.kt`. Used everywhere instead of bare string literals. Replace with real user ID when Track 6.1 ships.
- **Web transposition:** `renderPerformance(body, semitones, displayKey)` in `App.tsx` — full TS port of TranspositionUtils. Inline `\`[chord]\`` and legacy chord-line formats both transposed. Performance modal passes `semitoneShift(original_key, display_key)` automatically.

---

## GCP Cloud Sync — LIVE (Phase 3 Complete)

### What was built
- **`SyncProvider.kt`** — unified interface wrapping `EncoreApiService` + lock/manifest/upload/download ops + `authConsentEvents: SharedFlow<IntentSender>`.
- **`GcpSyncProvider.kt`** — production implementation backed by Google Cloud Storage REST API.
  - Auth: RSA-SHA256 JWT service account — no OAuth2, no Play Services, no user consent flow.
  - Service account: `encore-tablet-sync@encore-cloud-leo-2026.iam.gserviceaccount.com`
  - Credentials: `assets/gcp_service_account.json` (gitignored)
  - Token cached 55 min, auto-refreshed on expiry.
  - Bucket: `gs://encore-cloud-leo-2026-songs`
  - Object layout: `{userId}/songs/{songId}.md`, `locks/{songId}.lock`, `system/library_health.json`
- **`SongRepository.uploadSongToCloud(userId, songId)`** — uploads markdown body, calls `markSynced()`.
- **`LibraryViewModel.triggerGlobalSync()`** — iterates all songs, uploads each, early-aborts on first failure, saves timestamp on completion.
- **Sync HUD** — `InProgress(current, total)` counter → `Complete` (✓ Synced in green) → null.
- **Last synced timestamp** — displayed in Settings → Library Tools using `SimpleDateFormat`.
- **Consent launcher** — at `MainScreen` level (always active regardless of nav route).
- **`AppContainer`** — `GcpSyncProvider(context)` — no Account lambda needed.

### Confirmed working
- Files land in `gs://encore-cloud-leo-2026-songs/{userId}/songs/{songId}.md` ✓
- Manifest at `system/library_health.json` updated per upload ✓

### Known facts
- `FakeSyncProvider` still wired — swap in `AppContainer` for offline/debug testing.
- `play-services-auth` removed from `app/build.gradle.kts` and `core/data/build.gradle.kts` (6.4 COMPLETE).

---

---

## Bidirectional Sync — COMPLETE (2026-04-04)

### What was built
- **Web → Android path**: Web app calls `CloudLibraryService.updateLibraryManifest(songPath)` after every save — writes epoch-ms timestamp as `hash` in `system/library_health.json`. Android `checkSyncStatus` compares `remote.serverUpdatedAt > song.lastSyncedAt` (timestamp-based RemoteAhead) so mismatched hash formats (web=epoch string, Android=MD5) are handled correctly.
- **Immediate saves**: Both apps save to GCS on every edit — no manual sync required.
- **Startup check**: `autoSyncOnStart()` throttle reduced from 10 min to 60 seconds.
- **Background poller**: `startRemoteChangePoller()` runs every 2 minutes in `LibraryViewModel.init`; calls `pullRemoteChanges()` which checks all songs for RemoteAhead and pulls, then applies web set changes.
- **60s manifest cache**: `GcpSyncProvider` caches the manifest JSON with a 60s TTL so a 96-song startup check costs 1 GCS read, not 96.
- **Fire-and-forget uploads**: `uploadSongInBackground(songId)` — `viewModelScope.launch` so UI is never blocked.

### Key files changed
- `GcpSyncProvider.kt` — manifest cache (`manifestCache`, `manifestCachedAt`, 60s TTL); `uploadSetData`/`downloadSetData` for `{userId}/sets/set_N.json`; `setObjectPath` helper; `invalidateManifestCache()` after write.
- `SyncProvider.kt` — added `uploadSetData`/`downloadSetData` to interface.
- `FakeSyncProvider.kt` — no-op stubs for the new interface methods.
- `SongRepository.kt` — `checkSyncStatus` gains timestamp-based RemoteAhead guard.
- `LibraryViewModel.kt` — poller, fire-and-forget uploads, `lastSeenSetUpdatedAt` map, set sync helpers; `updateMarkdownBody`/`updateSongMetadata` call `uploadSongInBackground`.
- `CloudLibraryService.js` — `updateLibraryManifest`, `loadSetFile`, `saveSetFile`.
- `App.tsx` — `handleSaveSong` calls manifest update; set state replaced with `liveSets`/`activeLiveSet`; `refreshCloudLibrary` loads set files; "Live Sets" right column.

---

## Set Sync — COMPLETE (2026-04-04)

### Protocol
- Set files live at `{userId}/sets/set_{N}.json`: `{"version":1,"updatedAt":epoch,"source":"tablet"|"web","songIds":["uuid1",...]}`
- Tablet writes `source:"tablet"` on every mutation; web writes `source:"web"`.
- Android poller applies only changes where `source=="web"` and `updatedAt > lastSeenSetUpdatedAt[N]` (in-memory, not persisted; `source` field guards direction on restart).
- `SetlistRepository.replaceSetContents(setId, songIds)` — clears all entries, re-inserts in order.
- `LibraryViewModel.uploadAllSetsInBackground()` — called by every set-mutation function.
- All set-mutation functions in `LibraryViewModel` now upload: `addToPerformSet`, `removeFromPerformSet`, `reorderPerformSet`, `addSongToSetNumber`, `removeSongFromSetNumber`, `reorderSong`, `deleteSet`, `clearAllSets`.

### Web Live Sets
- Replaced "Setlist Architect" export panel with **Live Sets** section.
- Shows tabs per set (1–N), songs in order with ✕ remove, library picker to add.
- Changes call `CloudLibraryService.saveSetFile` immediately.

---

## Clear All Sets — COMPLETE (2026-04-04)
- `SetlistRepository.clearAllSets(setlistId)` — deletes sets 2+, clears Set 1 entries.
- `LibraryViewModel.clearAllSets()` — calls repo method, resets active set filter.
- Settings screen: "Clear All Sets" card in Library Tools, red outlined button, `AlertDialog` confirmation, red confirm button.

---

## Conflict Resolution — WIRED (2026-04-04)

### What was built
- `SongRepository.markConflict(songId)` — sets `syncStatus = CONFLICT` on DB entity (shows ⚠ badge)
- `SongRepository.fetchRemoteMarkdownBody(userId, songId)` — downloads + strips YAML, returns body for diff display only (no DB write)
- `SongRepository.markSynced()` + `pullSongFromCloud()` — both now reset `syncStatus = SYNCED` on resolution
- `SongDao.observeById(id)` — new reactive `Flow<SongEntity?>` for single-song observation
- `SongRepository.observeSong(songId)` — exposes reactive flow to VMs
- `ConflictResolutionDialog` moved from `feature/performance` → `feature/library` (only used there)
- `LibraryViewModel`: `ConflictResolutionState`, `conflictToResolve: StateFlow`, `prepareConflictResolution`, `resolveConflictKeepLocal`, `resolveConflictKeepRemote`, `dismissConflictResolution`
- `uploadSongInBackground` now runs `checkSyncStatus` AFTER DB write (inside coroutine) — fixes race condition where `isDirty=true` wasn't visible before upload check
- `pullRemoteChanges` + `triggerGlobalSync` both call `markConflict()` on `ContentSyncStatus.Conflict`
- Screen-level `ConflictResolutionDialog` in both `LibraryScreen` and `LibraryListContent`
- `SongDetailViewModel.getSongForPage` replaced one-shot cache with `observeSong` Flow — performance view now live-updates when DB changes

### Manifest cache race condition — FIXED (2026-04-10)
- **Root cause:** `uploadSongInBackground` called `checkSyncStatus` which served the 60s cached manifest. If the web app edited within the cache window the tablet saw a stale hash, resolved to `LocalAhead`, and silently overwrote the web version.
- **Fix:** `SyncProvider.invalidateCache()` added to the interface; `GcpSyncProvider` exposes existing private method; `SongRepository.invalidateRemoteCache()` delegates to it. `uploadSongInBackground` calls `invalidateRemoteCache()` before `checkSyncStatus` to force a fresh GCS read. Background poller and global sync retain the 60s cache.

---

## Firebase Web App — DEPLOYED (2026-04-04)

### Location
- **Canonical source:** `Encore-Firebase/Encore-Firebase/` (the inner directory — this is the one that runs on localhost:5174)
- **Live URL:** https://encore-cloud-leo-2026-8a467.web.app
- **Firebase project:** `encore-cloud-leo-2026-8a467`
- **Firebase config files** (`firebase.json`, `.firebaserc`) live inside `Encore-Firebase/Encore-Firebase/`

### Deploy command (run from `Encore-Firebase/Encore-Firebase/`)
```bash
npm run build
firebase deploy --only hosting
```

### Notes
- `.env` file holds Firebase API keys — gitignored, must be present locally to build
- `node_modules/` and `dist/` are gitignored
- Old copies (`encore-desktop-manager/`, `encore-firebase-hosted/`, `edm/`) are gitignored at root

---

## Remaining M4 Sync Work
- **Session lock enforcement** — GCS lock objects written but not server-enforced
- Setlist management screen
