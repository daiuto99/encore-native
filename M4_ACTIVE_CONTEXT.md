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

## Zen UI — Phase 1 COMPLETE
- **`EncoreTheme.kt`** — `EncoreColors` data class + `DarkEncoreColors` / `LightEncoreColors` / `LocalEncoreColors` in `core:ui`.
- **Dark/Light toggle** — Sun/Moon icon in `EncoreHeader` and Performance slim header.
- **Zen Cards** — 72dp `Surface(RoundedCornerShape(12.dp))` cards, `#1C1C1E` dark / `#FFFFFF` light.
- **Left accent bars** — 4dp, Set 1 `#5AC8FA` / Set 2 `#4CD964` / Set 3 `#AF52DE`.
- **SetColor** updated to Zen pastels for Sets 1–3.

## Schema & Logic Alignment — COMPLETE (this session)
- **DB v5 migration** — `current_key` → `display_key`, added `original_key`, `is_lead_guitar`, `is_verified`, `last_verified_at`; removed `lead_marker`, `harmony_markup`. Full table-recreation migration (SQLite constraint). See `docs/decisions/002-song-schema-and-chart-editor.md`.
- **Performance Header** — "Not Original Key" amber badge when `displayKey != originalKey`; Lead Guitar icon when `isLeadGuitar = true`.
- **Edit Modal** — Removed Key field and Line Highlight buttons; added Lead Guitar toggle; "Edit Chart" button in header navigates to chart editor.
- **`set_assignment`** — skipped; future Projects feature will own cross-set membership via a proper `Project` table.

## Chart Editor Screen — COMPLETE (this session)
- **`SongChartEditorScreen`** — Full-screen markdown editor with `BasicTextField` + `TextFieldValue` for cursor-safe tag insertion.
- **Harmony DSL multi-line fix** — `SongDetailScreen` pre-scans body lines for `[h]`/`[/h]` block boundaries; lines inside a harmony block render orange + bold + underline. Single-line self-contained tags handled inline by `HARMONY_TAG_PATTERN`.
- **Formatting toolbar** — `[h]`, Chord, Section buttons in TopAppBar alongside Cancel/Save (always accessible regardless of keyboard state).
- **Selection caching** — `lastNonCollapsedSelection` restores selection when Android collapses it on button tap, enabling multiple harmony wraps in sequence.
- **Focus management** — `focusManager.clearFocus()` on Save, Cancel, and Back dismisses cursor and Android selection action bar; prevents editor getting stuck in active-but-no-keyboard state.
- **Live highlight** — `HarmonyHighlightTransformation` (`VisualTransformation`) shows orange tint on `[h]...[/h]` spans while editing.
- **Inline chord/section edit** — panel floats above keyboard via `imePadding()`, only shown when active.
- **`SongEditBottomSheet`** — "Select All" suppressed via `LocalTextToolbar` override (`onSelectAllRequested = null`); explicit "Exit" button added below Save.

## Remaining M4 Work
- Single active device session policy
- Wire `SyncStatus` to real Ktor API calls
- Manual **Sync Now** action
- Conflict detection and conflict-resolution UI
- Setlist management screen: create, rename, delete setlists

## Known Facts for Next Session
- `SetlistDetailScreen.kt` is a separate screen the user does not use — do not touch it
- The Library screen (`feature/library`) is the correct location for all set-tab and song-list work
- `SongEditBottomSheet` is in `feature/library` and imported by the `app` module for the Performance screen path
- `SongChartEditorScreen` is in `feature/library`; navigated to via `Routes.SONG_CHART_EDITOR = "chart_editor/{songId}"`
- DB is currently at version 5
- Build filter: `./gradlew assembleDebug 2>&1 | grep -E "FAILED|^e: |BUILD SUCCESSFUL"`
- ADB path: `~/Library/Android/sdk/platform-tools/adb`
