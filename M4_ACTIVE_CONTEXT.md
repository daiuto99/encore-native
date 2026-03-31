# Encore Android — M4 Active Context

## Current Milestone
Milestone 4 — Sync + Account Behavior

## Milestone Goal
Add cloud-backed account and sync behavior without breaking the offline-first local product.

## What Is Already Done

### Completed foundation for M4
- **Performance Mode (v1):** HorizontalPager, Pinch-to-zoom (0.5x–3.0x) with DB persistence, and ChordSidekick parser are functional.
- **Set Integrity:** Sets 1–4 are auto-initialized in DB to prevent selection crashes.
- Google sign-in and sign-out are implemented
- Auth state transitions between loading, unauthenticated, and authenticated states
- `ownerId` support has been added to song storage
- Session persistence is implemented with DataStore
- Import flow uses standard Android file-picker behavior
- Global dark background and adaptive song-row color work are complete
- **Swipe/zoom gesture fix:** `onZoomChange` deferred to finger-lift — eliminates pager stutter
- **Set sort order:** All DAO queries now `ORDER BY created_at ASC`
- **`reorderSongInSet(entryId, newPosition)`** wired in ViewModel and Repository
- **Performance Mode gesture & zoom polish (COMPLETE):**
  - Double-tap to reset zoom: custom `PointerEventPass.Initial` detector bypasses HorizontalPager scroll interception
  - `beyondBoundsPageCount = 1` pre-renders adjacent songs for instant swipe
  - `Crossfade(pageSong?.id, tween(250))` — keyed on song identity, not entity fields; no re-fade on DB updates
  - `zoomPerSong: mutableStateMapOf<String, Float>` at screen level — per-song in-session zoom, survives pager swipes
  - All gesture closures use `currentSong.id` (concrete DB identity), not pager slot — immune to mid-swipe desync
  - `didZoom` flag in pinch loop — single-finger taps never overwrite zoom via `onZoomChange`
  - +/- HUD buttons read/write `zoomPerSong` as single source of truth; ViewModel is write-only for DB persistence
  - `zoomPerSong.clear()` on `DisposableEffect` dispose — no stale entries across sessions
  - `LaunchedEffect(song.id)` replaces `LaunchedEffect(textSizeMultiplier)` — `currentZoom` only resets on song change
- **Song drag-to-reorder on Library screen (COMPLETE):**
  - ≡ handle visible when a Set tab is active; long-press triggers haptic + drag
  - `mutableStateListOf` local shadow list drives LazyColumn — no DB reads during drag
  - Items swap in real-time as finger crosses row boundaries; DB written once on release
  - `animateItemPlacement(spring(StiffnessMediumLow))` for smooth gap-fill animation
  - 150ms debounce blocks `LaunchedEffect(songs)` sync until animation settles
  - Dragged row shows scale(1.03f) lift, 8dp shadow, and red border glow
  - `view.parent.requestDisallowInterceptTouchEvent(true)` prevents SwipeToDismissBox from stealing the gesture

## Remaining M4 Work
- Single active device session policy
- Wire `SyncStatus` to real Ktor API calls
- Manual **Sync Now** action
- Conflict detection and conflict-resolution UI
- Setlist management screen: create, rename, delete setlists

## Current Recommendation
Work M4 in this order:
1. Define sync state machine and API contract
2. Implement Manual Sync Now flow against real API calls
3. Conflict detection and resolution UI

## Known Facts for Next Session
- `SetlistDetailScreen.kt` is a separate screen the user does not use for the main workflow — do not touch it
- The Library screen (`feature/library`) is the correct location for all set-tab and song-list work
- Build filter: `./gradlew assembleDebug 2>&1 | grep -E "FAILED|^e: |BUILD SUCCESSFUL"`
- ADB path: `~/Library/Android/sdk/platform-tools/adb`
