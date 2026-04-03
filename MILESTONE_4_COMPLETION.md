# Milestone 4 — Sync Engine Completion Ledger

## Current Status: 40% Complete — Sync Intelligence Layer done; Conflict UI scaffold done; Ktor + full resolution flow pending

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

### Task 2 — Schema Update (DB v7) [x]
- [x] `lastSyncedHash: String?` added to `SongEntity`
- [x] `isDirty: Boolean` added to `SongEntity` (default false)
- [x] Room migration `6 → 7` — two `ALTER TABLE` statements
- [x] `isDirty = true` set in `LibraryViewModel.updateMarkdownBody` and `updateSongMetadata` (when body changes)

### Task 3 — Sync Logic Spike [x]
- [x] `core/data/.../sync/ContentSyncStatus.kt` — sealed class: `UpToDate`, `LocalAhead`, `RemoteAhead(remoteHash)`, `Conflict(localHash, remoteHash)`, `NeverSynced`
- [x] `core/data/.../sync/EncoreApiService.kt` — interface + `RemoteHashResponse` data class
- [x] `core/data/.../sync/FakeSyncProvider.kt` — `SyncScenario` enum (SYNCED / REMOTE_AHEAD / CONFLICT), per-song override map, `setScenario()` / `clearOverrides()`
- [x] `SongRepository.checkSyncStatus(songId, apiService)` — full decision table implemented
- [x] `SongRepository.markSynced(songId)` — writes current hash to `lastSyncedHash`, clears `isDirty`

### Task 4 — Sync Progress HUD [x]
- [x] `core/data/.../sync/SyncHudState.kt` — sealed class: `InProgress(current, total)`, `Complete`
- [x] `PerformanceContextBar` accepts `syncHudState: SyncHudState?` parameter
- [x] `InProgress` → spinner + "N/Total" in place of clock; `Complete` → "✓ Synced"; `null` → clock
- [ ] Manual **Sync Now** trigger from Settings screen — pending Ktor wiring

### Task 5 — Conflict Resolution UI [ ]
- [ ] `ConflictResolutionDialog` composable
  - 400dp width, centred, `RoundedCornerShape(12.dp)`
  - Diff view: additions in green (SetColor pastel), deletions in red
  - "Keep Local" / "Keep Remote" buttons — 60dp hit targets
- [x] `feature/performance/.../ConflictResolutionDialog.kt` scaffold — 400dp, two-column diff, 60dp buttons, "Decide Later" cancel
- [ ] Wired into `ContentSyncStatus.Conflict` in ViewModel — pending Ktor
- [ ] On resolution: writes winner to DB, calls `markSynced()` — pending Ktor

### Task 6 — Single Active Device Session [ ]
- [ ] Session token stored in DataStore `user_prefs`
- [ ] Server rejects sync from stale session tokens
- [ ] UI: "Another device is active" warning

---

## Verification Gates (must pass before merge)
- [ ] App defaults to Light Mode on fresh install
- [ ] All `IconButton` hit targets remain 60dp
- [ ] Offline mode: no sync calls attempted, no crashes
- [ ] Conflict UI displays correctly at 11-inch portrait
- [ ] DB migration: existing data survives `6 → 7` upgrade without data loss

---

## Progress Log

| Date | Task | Status | Notes |
|---|---|---|---|
| 2026-04-02 | Tasks 1–5 scaffold | ✅ Done | DB v7, FileHashUtils, FakeSyncProvider, ContentSyncStatus, SyncHudState, ConflictResolutionDialog |
