# Milestone 4 — Sync Engine Completion Ledger

## Current Status: 65% Complete — Sync Intelligence, Session Lock, UI Polish, Capo, Theme Colors done; Ktor + full resolution flow pending

**Goal:** Implement cloud-backed sync, hashing logic, and a "Decision Gate" for version conflicts without breaking offline-first reliability.

---

## Conflict Scenarios

| Scenario | Condition | Resolution |
|---|---|---|
| **A** | File changed on server/desktop + Tablet unchanged | Auto-update Tablet (accept remote) |
| **B** | Tablet changed + File unchanged on server/desktop | Auto-update server/desktop (accept local) |
| **C** | Both changed since last sync | Trigger Conflict UI — user decides |

---

## Component Checklist

### Task 1 — Hash Utility [x]
- [x] `core/data/.../sync/FileHashUtils.kt` — `hashMarkdownBody(body)` suspend + `hashMarkdownBodySync` variant
- [x] Trims trailing whitespace before hashing to prevent false conflicts
- [x] Runs on `Dispatchers.IO` via `withContext`

### Task 2 — Schema Update (DB v7 → v9) [x]
- [x] `lastSyncedHash: String?` added to `SongEntity` (v7)
- [x] `isDirty: Boolean` added to `SongEntity` (v7)
- [x] `isLockedByOther: Boolean` added to `SongEntity` (v8)
- [x] `capoEnabled: Boolean` + `capoFret: Int` added to `SongEntity` (v9)
- [x] Room migrations 6→7, 7→8, 8→9 all applied
- [x] `isDirty = true` set in `LibraryViewModel.updateMarkdownBody` and `updateSongMetadata`

### Task 3 — Sync Logic Spike [x]
- [x] `ContentSyncStatus.kt` — sealed class: `UpToDate`, `LocalAhead`, `RemoteAhead(remoteHash)`, `Conflict(localHash, remoteHash)`, `NeverSynced`
- [x] `EncoreApiService.kt` — interface + `RemoteHashResponse` data class
- [x] `FakeSyncProvider.kt` — `SyncScenario` enum + `LockResult` sealed class; per-song overrides; `requestLock` / `releaseLock`; `setLocked(songId, owner)` for testing
- [x] `SongRepository.checkSyncStatus()` — full decision table implemented
- [x] `SongRepository.markSynced()` — writes current hash to `lastSyncedHash`, clears `isDirty`
- [x] `SongRepository.requestEditLock()` — 5s timeout offline escape hatch, updates `isLockedByOther` in DB
- [x] `SongRepository.releaseEditLock()` — clears lock on editor exit

### Task 4 — Sync Progress HUD [x]
- [x] `SyncHudState.kt` — sealed class: `InProgress(current, total)`, `Complete`
- [x] `PerformanceContextBar` accepts `syncHudState: SyncHudState?` — `InProgress` shows spinner + "N/Total"; `Complete` shows "✓ Synced"; `null` shows clock
- [x] `LibraryViewModel.triggerGlobalSync()` — guard against double-run, loops all songs, 100ms delay per song, `Complete` for 3s then null
- [x] **Sync Now** button in Settings → Library Tools wired to `triggerGlobalSync()`
- [x] **Auto-sync on start** — `autoSyncOnStart()` in `LibraryViewModel.init`; 10-minute throttle via `userPrefs.getLastSyncTimestamp()` / `saveLastSyncTimestamp()`

### Task 5 — Conflict Resolution UI [x]
- [x] `ConflictResolutionDialog.kt` scaffold — 400dp, two-column diff (Local green / Server red), 60dp buttons, "Decide Later" cancel
- [x] `SongListItem` CONFLICT badge (⚠ amber) when `syncStatus == SyncStatus.CONFLICT`
- [x] CONFLICT tap intercepts `onClick` and shows warning `AlertDialog` ("Open Anyway" / "Decide Later")
- [ ] Full resolution flow wired to real API (write winner to DB, `markSynced()`) — pending Ktor

### Task 6 — Session Lock [x]
- [x] `LockResult` sealed class: `Acquired` / `LockedBy(owner: String)`
- [x] `FakeSyncProvider.requestLock()` / `releaseLock()` / `setLocked()` for test overrides
- [x] `SongChartEditorScreen` — `LaunchedEffect(songId)` requests lock; `DisposableEffect` releases on exit
- [x] Read-Only amber banner: "Read Only — locked by [owner]" shown when `isLockedByOther = true`
- [x] Save/Cancel hidden, `readOnly = true` on text field when locked
- [x] `isLockedByOther` badge (🔒) on `SongListItem`
- [x] `LibraryViewModel.lockState: StateFlow<LockResult?>` exposed for UI
- [ ] Server-side session enforcement — pending Ktor

### Task 7 — Performance UI Polish [x]
- [x] **Bar order swapped:** ContextBar (top) → Dashboard (below)
- [x] **Set label:** `"SET $setNumber"` uppercase, set color, bold, `letterSpacing = 1.2.sp` — no setlist name
- [x] **Song title:** 19sp Bold Monospace; color = `titleColorOverride ?? setColor`
- [x] **Artist color:** `artistColorOverride ?? encoreColors.artistText`
- [x] **Title/Artist global color overrides:** hex inputs in Settings → Performance HUD with `×` clear button
- [x] **Capo per-song:** `capoEnabled` / `capoFret` in `SongEditBottomSheet`; toggle + fret stepper (1–12)
- [x] **Capo badge in dashboard:** fret number + "CAPO" label in status pill, uses `capoColor` from theme
- [x] **Lead guitar icon color:** `darkLeadIconColor` / `lightLeadIconColor` in AppPreferences; settable in Theme panel
- [x] **Capo badge color:** `darkCapoColor` / `lightCapoColor` in AppPreferences; settable in Theme panel
- [x] **Add-to-Set picker:** `+` button opens set picker dialog; already-in-set entries disabled

---

## Verification Gates

- [ ] App defaults to Light Mode on fresh install ✅ (done)
- [ ] All `IconButton` hit targets remain 60dp ✅ (done)
- [ ] Offline mode: no sync calls attempted, no crashes ✅ (5s timeout escape hatch)
- [ ] Conflict UI displays correctly at 11-inch portrait
- [ ] DB migration: existing data survives all migrations without data loss
- [ ] Ktor API integration passes smoke tests
- [ ] Full conflict resolution flow (Keep Local / Keep Remote) writes correct state

---

## Progress Log

| Date | Task | Status | Notes |
|---|---|---|---|
| 2026-04-02 | Tasks 1–5 scaffold | ✅ Done | DB v7, FileHashUtils, FakeSyncProvider, ContentSyncStatus, SyncHudState, ConflictResolutionDialog |
| 2026-04-03 | Session lock, auto-sync, badges | ✅ Done | DB v8, LockResult, requestEditLock, Read-Only banner, SongListItem badges, autoSyncOnStart |
| 2026-04-03 | Performance UI polish | ✅ Done | Bar swap, SET N label, title color/size, capo per-song (DB v9), icon colors in theme, add-to-set picker |
