# Encore Android — Code Review Findings

All security, stability, scalability, and correctness issues found during code reviews.
Each session is dated. Findings are tracked with status.

**Status key:** `OPEN` · `FIXED` · `WONTFIX` · `ACCEPTED-RISK`

---

## Master Summary (all sessions)

### ⚠ Release Gates — MUST fix before any release, second user, or Play Store distribution

| # | Finding | File |
|---|---|---|
| RG-1 | **[CRITICAL]** Service account private key shipped inside the APK — extractable in ~10 s with `apktool` | `GcpSyncProvider.kt:78` |
| RG-2 | **[HIGH]** No cross-user GCS path enforcement — client controls `userId` prefix, service account key grants full bucket access | `GcpSyncProvider.kt:405` |

Both items are `ACCEPTED-RISK` only for the current single-user sideloaded configuration. They become blocking the moment a second user exists or the APK is distributed outside the device owner.

---

### Open findings — prioritised fix list

| Priority | Severity | Finding | File | Session |
|---|---|---|---|---|
| 1 | HIGH | `SyncProvider` called directly in `LibraryViewModel`, bypassing repository layer — backend swap or retry logic in the repo won't cover set sync | `LibraryViewModel.kt:63,751` | 2 |
| 2 | HIGH | `appContainer.songRepository` passed directly into `SetlistDetailScreen` composable — composables must not hold repo refs | `Navigation.kt:121` (now `MainScreen.kt`) | 2 |
| 3 | MEDIUM | `LibraryViewModel` god object (1,232 lines, 15+ StateFlows) — dominant maintainability risk | `LibraryViewModel.kt` | 2 |
| 4 | MEDIUM | `"local-user"` hardcoded userId in all import paths — will cause dedup failures and orphaned records if multi-user arrives | `LibraryViewModel.kt:433,530,1147` | 2 |
| 5 | MEDIUM | `SongDetailViewModel._songCache` grows unbounded — all 96 songs accumulate in memory if user pages through entire set | `SongDetailViewModel.kt:103` | 2 |
| 6 | MEDIUM | DB pre-population uses fire-and-forget `CoroutineScope` — no error handler; silent failure on first launch | `EncoreDatabase.kt:242` | 2 |
| 7 | LOW | `SongChartEditorScreen` back-press with unsaved edits discards changes without a confirmation prompt | `SongChartEditorScreen.kt` | 3 |
| 8 | LOW | `SetlistSelectionDialog` composable lives in `Navigation.kt` — wrong module, no navigation logic | `Navigation.kt:157` | 2 |
| 9 | LOW | `importSetFromJson` accepts JSON with no version check, no song-count cap, no `markdownBody` length limit | `LibraryViewModel.kt:1139` | 2 |

---

### Fixed findings (18 total)

| Severity | Finding | Fixed in Session |
|---|---|---|
| CRITICAL | `fallbackToDestructiveMigration()` active — silent DB wipe on missing migration | 2 |
| CRITICAL | Manifest read-modify-write race — concurrent uploads silently dropped entries from manifest | 1 |
| HIGH | `requestLock` not atomic — two clients could both acquire the same song lock | 1 |
| HIGH | Token cache check not thread-safe — concurrent coroutines all missed cache and hammered token endpoint | 1 |
| HIGH | Metadata-only edits bypassed conflict detection — `isDirty` stayed false, silently overwrote web edits | 1 |
| HIGH | `loadSetlistAsCurrent` skipped cloud upload — set changes went stale until next unrelated mutation | 1 |
| HIGH | `SongDetailViewModel.loadSetlist` mutated Set 1 without uploading to GCS — web app stayed stale | 2 |
| HIGH | `prepareConflictResolution` loaded entire library to find one song (`getAllSongsOnce` instead of `getSongById`) | 2 |
| HIGH | `getSongForPage` called without `remember` — new Room subscription on every pager recomposition | 3 |
| MEDIUM | Verbose logs leaked token prefix and full error bodies from Google token endpoint | 1 |
| MEDIUM | `pullSongFromCloud` applied YAML values to DB without length validation | 1 |
| MEDIUM | `deleteSong` had no GCS cleanup — deleted songs accumulated in bucket indefinitely | 1 |
| MEDIUM | `triggerGlobalSync` aborted entire sync on any first-song transient failure | 1 |
| MEDIUM | `uploadAllSetsInBackground` read stale `StateFlow` snapshot on cold launch — silently uploaded nothing | 1 |
| MEDIUM | Web-sourced set entries could create orphaned DB references on songs deleted locally | 1 |
| MEDIUM | Dead navigation code (`EncoreNavHost`, `SetlistSelectionDialog`) left in `Navigation.kt` | 2 |
| MEDIUM | `song!!` force-unwrap on delegated property — smart cast not guaranteed across recompositions | 3 |
| MEDIUM | `currentZoom` stale after double-tap reset — next pinch started from wrong baseline | 3 |
| MEDIUM | `isBareChordLine` compiled a new `Regex` on every call — 96 wasted allocations per render pass | 3 |

> Note: the LOW fix (orphaned DB references) is counted once above. Total fixed: **18** unique items across 3 sessions.

---

### Accepted-risk findings (2 total — both are release gates)

| Severity | Finding |
|---|---|
| CRITICAL | Service account private key in APK — see RG-1 above |
| HIGH | No cross-user GCS path enforcement — see RG-2 above |

---

## Session detail

The rest of this document contains the full per-session findings with context, root cause, and fix notes.

---

## Session 1 — Security + Sync Correctness (2026-04-10)

**Scope:** `GcpSyncProvider.kt`, `SongRepository.kt`, `AuthRepository.kt`, `UserPreferencesRepository.kt`, `AppContainer.kt`, `LibraryViewModel.kt` (sync paths)

---

### Security

---

#### [CRITICAL] Service account private key shipped inside the APK
**Status:** ACCEPTED-RISK — single-user sideloaded app; no Play Store distribution. Revisit before multi-user rollout.
**File:** `core/data/src/main/kotlin/com/encore/core/data/sync/GcpSyncProvider.kt:78`

```kotlin
context.assets.open("gcp_service_account.json")
```

The `assets/` folder is embedded verbatim in the APK. `apktool d encore.apk` exposes the key in plain text in ~10 seconds. The file is gitignored, which only protects the repo — not the distributed binary. The service account has `devstorage.read_write` on the entire bucket. A leaked key means anyone can read, write, or delete every user's songs.

**Mitigation options (pick one):**
- Move auth to a thin backend proxy — tablet sends Google ID token, backend exchanges for a GCS token scoped to that user's prefix only. Also fixes the cross-user access issue below.
- Encrypt the key at build time using a `BuildConfig` passphrase (raises the bar, not a full solution).
- Use Tink's Android Keystore integration to wrap the key at install time.

---

#### [CRITICAL] Manifest read-modify-write race condition
**Status:** FIXED 2026-04-10 — `mutateManifest(token, action)` wraps all manifest writes under `manifestMutex`; both `updateManifest` and `deleteSong` delegate to it.
**File:** `core/data/src/main/kotlin/com/encore/core/data/sync/GcpSyncProvider.kt:383–398`

`updateManifest` does: read → merge → write. Two concurrent song uploads (e.g., `triggerGlobalSync` iterating quickly while `uploadSongInBackground` also fires) can both read the old manifest, each write their single entry, and the second write silently discards the first song's entry from the manifest. The cache invalidation at the end doesn't help because both operations are in-flight simultaneously before either writes back.

**Fix:** Serialize manifest writes with a `Mutex`, or switch to per-song manifest objects instead of a single shared JSON blob.

---

#### [HIGH] `requestLock` is not atomic — two clients can both acquire
**Status:** FIXED 2026-04-10 — replaced read-then-write with `tryCreateObject` using GCS `ifGenerationMatch=0` precondition. One caller gets 201, any concurrent caller gets 412.  
**File:** `core/data/src/main/kotlin/com/encore/core/data/sync/GcpSyncProvider.kt:188–212`

Read-then-write is two separate GCS calls. If two devices call `requestLock` simultaneously, both see no existing lock and both write their own lock. Last write wins, but neither receives `LockedBy`. GCS has no compare-and-swap primitive for objects.

**Fix options:**
- Use GCS object generation preconditions (`ifGenerationMatch=0` on upload — GCS rejects the second writer).
- Accept that lock is advisory only and document explicitly.

---

#### [HIGH] Token cache check is not thread-safe
**Status:** FIXED 2026-04-10 — `token()` now wraps the cache check + fetch under `tokenMutex`.  
**File:** `core/data/src/main/kotlin/com/encore/core/data/sync/GcpSyncProvider.kt:107–119`

`cachedToken` and `tokenExpiresAt` are `@Volatile` but the read-check-then-write sequence is not atomic. Under concurrent calls (global sync + background poller firing simultaneously), multiple coroutines can all miss the cache and all call `fetchServiceAccountToken()`. Not a correctness bug (GCS honors all tokens), but causes unnecessary token endpoint calls.

**Fix:** Wrap the token fetch in a `Mutex`.

---

#### [HIGH] No cross-user access control — GCS paths are client-controlled
**Status:** ACCEPTED-RISK — single user means no other prefixes to cross into. Gap must be closed before any second user is onboarded.  
**File:** `core/data/src/main/kotlin/com/encore/core/data/sync/GcpSyncProvider.kt:405–407`

`songObjectPath` is `"$userId/songs/$songId.md"` where `userId` is the Google account `id` sourced from the client. Since the service account key is embedded in the APK, any user who extracts it can craft requests to any other user's `userId` prefix. There is no server-side enforcement that user X can only write to prefix X. Inherently resolves with the key-in-APK fix.

---

#### [MEDIUM] Verbose debug logs leak token prefix and full error bodies
**Status:** FIXED 2026-04-10 — removed `prefix=${tok.take(10)}` log; `fetchServiceAccountToken` error path no longer logs or throws with the response body.  
**File:** `core/data/src/main/kotlin/com/encore/core/data/sync/GcpSyncProvider.kt:118`, `:164`

```kotlin
Log.d(TAG, "token() — acquired; prefix=${tok.take(10)}…")
Log.e(TAG, "fetchServiceAccountToken — FAILED ${response.code}: $responseBody")
```

`Log.d` is stripped in release builds only if ProGuard is configured to do so. Error bodies on 401/403 responses from Google's token endpoint can include partial key identifiers. Should be removed or gated on `BuildConfig.DEBUG`.

---

#### [MEDIUM] `pullSongFromCloud` applies YAML values without validation
**Status:** FIXED 2026-04-10 — added `.take(200)` on title/artist and `.take(20)` on key fields.  
**File:** `core/data/src/main/kotlin/com/encore/core/data/repository/SongRepository.kt:448–460`

`title`, `artist`, `displayKey`, `originalKey` from the cloud document are written directly to the DB with no length or character validation. A malformed or tampered cloud file could inject unexpected values. Low real-world risk given single-user nature of the app.

---

### Sync Correctness

---

#### [HIGH] Metadata-only edits bypass conflict detection
**Status:** FIXED 2026-04-10  
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt:698–731`  
**Related:** `core/data/src/main/kotlin/com/encore/core/data/repository/SongRepository.kt:371–384`

`updateSongMetadata` sets `isDirty = existing.isDirty || bodyChanged`. If only title, artist, capo, or lead guitar flag changes — not the markdown body — `isDirty` stays false.

`checkSyncStatus` uses `isDirty` to detect conflicts: `isDirty && remoteHash != lastSyncedHash → Conflict`. A metadata-only edit with `isDirty = false` is never flagged as a conflict even if the web app concurrently edited the song body. The tablet then uploads and silently overwrites the web version.

**Fix:** `updateSongMetadata` should always set `isDirty = true` on any field change.

---

#### [HIGH] `loadSetlistAsCurrent` and `saveCurrentSetAs` skip cloud upload
**Status:** FIXED 2026-04-10 — `loadSetlistAsCurrent` now calls `uploadAllSetsInBackground()`. `saveCurrentSetAs` creates a named copy and does not modify the working sets, so no cloud upload needed there.  
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt:383–400`, `:361–377`

Every other set-mutation function calls `uploadAllSetsInBackground()`. These two do not. Loading a saved setlist or saving the current set as named leaves the cloud state stale until the next unrelated mutation triggers an upload.

---

#### [MEDIUM] `deleteSong` has no GCS cleanup
**Status:** FIXED 2026-04-10  
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt:694–696`

```kotlin
fun deleteSong(song: SongEntity) {
    viewModelScope.launch { songRepository.deleteSong(song) }
}
```

The GCS object at `{userId}/songs/{songId}.md` is never deleted. The manifest is never updated to remove the entry. Deleted songs accumulate in GCS indefinitely and `pullRemoteChanges` will keep trying to reconcile songs that no longer exist locally.

---

#### [MEDIUM] `triggerGlobalSync` aborts only when the first song fails
**Status:** FIXED 2026-04-10 — replaced first-song abort with `consecutiveUploadFailures >= 2` guard; a single transient failure no longer cancels the entire sync.  
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt:850–858`

The early-abort guard (`if (!uploaded && index == 0)`) only fires when the first song fails. Songs 2–N failures are ignored silently. Additionally, if song 1 fails transiently (timeout, brief connectivity loss), the entire sync is cancelled, the timestamp is not saved, and the next startup triggers a full re-sync.

---

#### [MEDIUM] `uploadAllSetsInBackground` reads a stale StateFlow snapshot
**Status:** FIXED 2026-04-10 — now queries `setlistRepository.getSetsForSetlist(setlistId).first()` directly instead of `availableSets.value`.  
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt:762–765`

```kotlin
availableSets.value.forEach { set -> uploadSetToCloud(set.number) }
```

`availableSets` is a `WhileSubscribed(5000)` StateFlow. On cold launch, before the DB query resolves, `availableSets.value` is `emptyList()`. Any set mutation that fires immediately on startup will silently upload nothing.

---

#### [LOW] Web-sourced set entries can create orphaned DB references
**Status:** FIXED 2026-04-10 — `checkAndApplyWebSetChanges` now filters incoming song IDs through `getSongById` before calling `replaceSetContents`.  
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt:777–780`

`setlistRepository.replaceSetContents(set.id, songIds)` replaces set entries with IDs sourced from the web. If a song ID in the web set doesn't exist locally (e.g., deleted on tablet before the set file was updated), Room will create a dangling reference or foreign key violation.

---

---

## Session 2 — Architecture + ViewModel Health (2026-04-10)

**Scope:** `LibraryViewModel.kt`, `SongDetailViewModel.kt`, `AppContainer.kt`, `ViewModelFactory.kt`, `Navigation.kt`, `MainScreen.kt`, `EncoreDatabase.kt`

---

### Architecture

---

#### [CRITICAL] `fallbackToDestructiveMigration()` is active
**Status:** FIXED 2026-04-10 — line removed from `EncoreDatabase.getDatabase()`.
**File:** `core/data/src/main/kotlin/com/encore/core/data/db/EncoreDatabase.kt:220`

If a migration is ever missing or throws, Room silently wipes the entire database instead of crashing. For an offline-first app where Room is the source of truth, this is a silent data-loss bomb. All 9 migrations are present, so there is no legitimate reason for this fallback to exist.

**Fix:** Remove the line.

---

#### [HIGH] `SyncProvider` leaks through `LibraryViewModel` bypassing the repository layer
**Status:** OPEN
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt:63`, `:751`

`uploadSetData`/`downloadSetData` are called directly on the injected `SyncProvider` inside `LibraryViewModel`, bypassing `SongRepository`. ViewModels should only talk to repositories. Any backend swap or retry logic added to the repository layer won't apply to set sync.

**Fix:** Route set upload/download through the repository layer.

---

#### [HIGH] `SongDetailViewModel.loadSetlist` mutates Set 1 without uploading to cloud
**Status:** FIXED 2026-04-10 — added `uploadSetInBackground(1, newIds)` call after mutation; `userPrefs` + `syncProvider` added as optional deps to `SongDetailViewModel`.
**File:** `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailViewModel.kt:279`

Mirror of the Session 1 bug fixed in `LibraryViewModel.loadSetlistAsCurrent`. When a setlist is loaded from performance mode, Set 1 is rewritten in Room but never uploaded to GCS. Web app stays stale. `saveCurrentSet` in the same file also duplicates `LibraryViewModel.saveCurrentSetAs` — two independent implementations of the same mutation.

**Fix:** `loadSetlist` must call an upload after mutating Set 1. Ideally both methods delegate to a shared path rather than duplicating.

---

#### [HIGH] `appContainer.songRepository` passed directly into a composable
**Status:** OPEN
**File:** `app/src/main/kotlin/com/encore/tablet/navigation/Navigation.kt:121`

`SetlistDetailScreen` receives a repository reference directly. Composables must not hold repository references — they should observe ViewModel state only.

**Fix:** Move the repository access into `SetlistViewModel`.

---

#### [MEDIUM] `LibraryViewModel` is a god object (1,232 lines)
**Status:** OPEN — architecture debt, not a bug; resolve in a dedicated refactor session
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt`

Song list, search, filter, SAF import, folder sync, 4-set management, GCS sync + polling + conflict, setlist export/import, lock management, key backfill — all in one class with 15+ StateFlows. Not a fix-now item but the dominant maintainability risk in the codebase.

---

#### [MEDIUM] `"local-user"` hardcoded userId in all import paths
**Status:** OPEN
**Files:** `LibraryViewModel.kt:433`, `:530`, `:1147`

Songs imported via file picker, folder sync, or JSON set import are created with `userId = "local-user"`. GCS paths use the real Google account ID. After sign-in, newly imported songs have a mismatched userId vs. the authenticated user, causing dedup failures and orphaned DB records if multi-user ever arrives.

---

#### [MEDIUM] Dual navigation graph — `EncoreNavHost` in `Navigation.kt` appears to be dead code
**Status:** FIXED 2026-04-10 — confirmed never called; deleted `EncoreNavHost` and `SetlistSelectionDialog` (which was only used inside it). `Routes` object retained as it is imported by `MainScreen.kt`.
**File:** `app/src/main/kotlin/com/encore/tablet/navigation/Navigation.kt`

`MainScreen.kt` has the live NavHost (inline, with all routes). `Navigation.kt` defines `EncoreNavHost` which is missing the chart editor and settings routes. If it's not referenced, it should be deleted.

---

### ViewModel Health

---

#### [HIGH] `prepareConflictResolution` loads the entire library to find one song
**Status:** FIXED 2026-04-10 — replaced `getAllSongsOnce().find { it.id == songId }` with `getSongById(songId)`.
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt:1191`

```kotlin
val song = songRepository.getAllSongsOnce().find { it.id == songId }
```

`getAllSongsOnce()` returns all songs. `getSongById(songId)` does a targeted `WHERE id = ?`. Wrong tool used.

**Fix:** Replace with `songRepository.getSongById(songId)`.

---

#### [MEDIUM] `SongDetailViewModel._songCache` grows unbounded
**Status:** OPEN
**File:** `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailViewModel.kt:103`

Every paged song is added to an in-memory `Map<String, SongEntity>` and never evicted. Each entry holds the full `markdownBody`. For a 96-song library paged through during a show, all 96 entities accumulate in memory.

---

#### [MEDIUM] DB pre-population uses a fire-and-forget bare coroutine scope
**Status:** OPEN
**File:** `core/data/src/main/kotlin/com/encore/core/data/db/EncoreDatabase.kt:242`

```kotlin
CoroutineScope(Dispatchers.IO).launch { prepopulateDatabase(...) }
```

No `SupervisorJob`, no error handler, no lifecycle. A failed insert on first launch is silently swallowed.

---

#### [LOW] `SetlistSelectionDialog` composable lives in `Navigation.kt`
**Status:** OPEN
**File:** `app/src/main/kotlin/com/encore/tablet/navigation/Navigation.kt:157`

A UI composable with no navigation logic. Should live in `feature/library` or a shared UI module.

---

#### [LOW] `importSetFromJson` has minimal JSON structure validation
**Status:** OPEN
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt:1139`

No version check, no max-song-count guard, `markdownBody` of arbitrary length accepted without bounds. Low risk given the controlled import surface (user-selected file).

---

## Session 3 — UI Stability + Lifecycle (2026-04-10)

**Scope:** `SongDetailScreen.kt`, `SongDetailViewModel.kt`, `SongChartEditorScreen.kt`

---

### UI / Compose Lifecycle

---

#### [HIGH] `getSongForPage` creates a new `Flow` on every pager-page recomposition
**Status:** FIXED 2026-04-10 — wrapped in `remember(pageSongId)` so the Room subscription is stable across recompositions.
**File:** `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailScreen.kt:341`

`viewModel.getSongForPage(pageSongId).collectAsState(initial = null)` was called without `remember`. On every recomposition of a pager page, a new `Flow` was created and collection restarted — cancelling the previous subscription, briefly emitting `null`, then re-fetching from Room. For the active page this also caused redundant `_songCache` writes on every recompose. The `beyondBoundsPageCount = 1` pre-rendering made this worse: adjacent off-screen pages recomposed on every swipe frame.

**Fix:** `remember(pageSongId) { viewModel.getSongForPage(pageSongId) }.collectAsState(initial = null)`.

---

#### [MEDIUM] `song!!` force-unwrap in `PerformanceDashboard` call site
**Status:** FIXED 2026-04-10 — extracted `val currentSong = song` before the null-check branch; `PerformanceDashboard` now receives the smart-cast `currentSong` directly.
**File:** `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailScreen.kt:409`

```kotlin
PerformanceDashboard(song = song!!, ...)
```

`song` is a delegated `StateFlow` property (`val song by viewModel.song.collectAsState()`). Kotlin's smart cast does not apply to delegated properties — each read calls the delegate's `getValue()` operator. The outer `if (song == null)` check and the `song!!` usage are two separate reads. In practice they're in the same composition pass and safe, but the `!!` is a hard crash if the value ever races to null (e.g., if `collectAsState` re-reads mid-recompose). The fix eliminates the crash path with zero behavioural cost.

---

#### [MEDIUM] `currentZoom` stale after double-tap reset in `SongContent`
**Status:** FIXED 2026-04-10 — `LaunchedEffect(song.id)` replaced with `LaunchedEffect(song.id, textSizeMultiplier)`.
**File:** `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailScreen.kt:602`

`SongContent` stores the pinch-gesture baseline in `var currentZoom by remember { mutableFloatStateOf(textSizeMultiplier) }`. The `LaunchedEffect(song.id)` only re-synced `currentZoom` when the song changed. After a double-tap reset (`viewModel.resetTextSize()` → parent recomposes with `textSizeMultiplier = 1.0f`), the `LaunchedEffect` did not re-run and `currentZoom` retained the old value. The next pinch gesture started from the wrong baseline (old zoom instead of 1.0).

Adding `textSizeMultiplier` as a second key re-syncs `currentZoom` whenever the parent-propagated value changes. During an active pinch, `awaitEachGesture` suspends the coroutine on the Main thread so the effect cannot interleave with the gesture loop.

---

#### [MEDIUM] `isBareChordLine` compiles a `Regex` on every invocation
**Status:** FIXED 2026-04-10 — pattern extracted to `private val BARE_CHORD_PATTERN` at file scope.
**File:** `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailScreen.kt:1881`

```kotlin
private fun isBareChordLine(line: String): Boolean {
    val pattern = Regex("""...""")   // compiled on every call
    ...
}
```

`isBareChordLine` is called once per body line per render pass (`SectionBodyLines`). For a 96-line song, every render allocated and compiled 96 `Regex` objects. Pattern moved to a top-level `val` compiled once at class load.

---

#### [LOW] `SongChartEditorScreen` discards unsaved edits without warning
**Status:** OPEN — UX gap, not a crash path; resolve when polish pass covers the editor.
**File:** `feature/library/src/main/kotlin/com/encore/feature/library/SongChartEditorScreen.kt`

Back navigation while `isDirty = true` exits without a "discard changes?" prompt. Since the app is portrait-only there is no configuration-change risk, and the GCS lock is released via `DisposableEffect` on exit. The only consequence is silently lost edits.

---

---

## Session Log

| Session | Date | Scope | Found | Fixed | Open after |
|---|---|---|---|---|---|
| 1 | 2026-04-10 | Security + Sync Correctness | 11 | 9 | 2 (accepted-risk) |
| 2 | 2026-04-10 | Architecture + ViewModel Health | 10 | 5 | 7 |
| 3 | 2026-04-10 | UI Stability + Lifecycle | 5 | 4 | 1 |
| **Total** | | | **26** | **18** | **9 open + 2 accepted-risk** |

## Score by Severity

| Severity | Open | Fixed | Accepted-Risk |
|---|---|---|---|
| CRITICAL | 0 | 2 | 1 |
| HIGH | 2 | 7 | 1 |
| MEDIUM | 4 | 8 | 0 |
| LOW | 3 | 1 | 0 |
| **Total** | **9** | **18** | **2** |

> ⚠ RELEASE GATE: The 2 accepted-risk items (key-in-APK + no cross-user GCS enforcement) MUST be resolved before any further release, multi-user onboarding, or Play Store distribution. All other open findings are architectural debt — none are crash paths in the current single-user configuration.
