# Milestone 3: Performance Engine — Final Summary

**Branch:** `feature/performance-viewer`
**Status:** ✅ COMPLETE
**Closed:** 2026-03-27
**Final Commit:** `e3b4af8`

---

## Overview

Milestone 3 delivered a production-ready Performance Mode and a fully refinished Library/Command Center workflow. The scope expanded significantly from the original plan to address foundational UX gaps discovered during device testing: a navigation crash, broken search logic, invisible keys, and a non-functional "Add to Set" flow. All blocking issues are resolved. The app is considered stage-ready for offline rehearsal testing.

---

## 1. Split-Renderer Architecture

### Problem
`mikepenz/multiplatform-markdown-renderer v0.14.0` silently strips `<span style="color:...">` HTML tags on Android. The original plan (Task 3 in MILESTONE_3_PLAN.md) assumed these would render. They do not.

### Solution — `parseSongSections()` in `SongDetailScreen.kt`

Rather than using a WebView or switching markdown libraries, a custom split-renderer was implemented directly in Compose. The markdown body is parsed into a flat list of sealed `SongSection` objects:

```kotlin
sealed class SongSection {
    data class Header(val text: String, val level: Int, val color: String?) : SongSection()
    data class Body(val markdown: String) : SongSection()
}
```

**Parse logic (`parseSongSections`):**
1. Scan each line of the markdown body.
2. Lines beginning with `#` become `SongSection.Header`. The header text is matched against `DisplayPreferences.DEFAULT_SECTION_COLORS` (case-insensitive substring match) to resolve a hex color string.
3. All other lines accumulate into a `bodyBuffer`. When a header is encountered, the buffer is flushed as a `SongSection.Body`.
4. Chord lines can be filtered out when `DisplayPreferences.showChords = false`.

**Render logic (`SongContent`):**
- `SongSection.Header` → Compose `Text` with `Color(android.graphics.Color.parseColor(hex))`, scaled font size, and `FontWeight.Bold`.
- `SongSection.Body` → `Markdown` composable from mikepenz (handles bold, italics, code spans within body blocks).

This approach fully bypasses the HTML rendering limitation. Headers get design-spec colors; body content stays markdown-native.

**File:** `android/feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailScreen.kt`

---

## 2. Global Set Color Map

Two parallel color systems were established and kept in sync:

### Section Header Colors — `DisplayPreferences.DEFAULT_SECTION_COLORS`
Sourced from `docs/design/display options - colors.png` (design source of truth).

| Section      | Hex       | Use |
|-------------|-----------|-----|
| Intro       | `#3882F6` | Section header text color |
| Verse       | `#F97316` | Section header text color |
| Chorus      | `#EF4444` | Section header text color |
| Bridge      | `#885CF6` | Section header text color |
| Outro       | `#F59E0B` | Section header text color |
| Solo        | `#10B981` | Section header text color |
| Interlude   | `#06B6D4` | Section header text color |
| Instrumental| `#EC4899` | Section header text color |

**File:** `android/core/data/src/main/kotlin/com/encore/core/data/preferences/DisplayPreferences.kt`

### Set Identity Colors — `SetColor.getSetColor()`
Fixed vivid colors used consistently for Set filter chips (MainScreen), Set membership circles (LibraryScreen), and Add-to-Set picker dialogs.

| Set | Hex       | Color  |
|-----|-----------|--------|
| 1   | `#3B82F6` | Blue   |
| 2   | `#F97316` | Orange |
| 3   | `#10B981` | Green  |
| 4   | `#8B5CF6` | Purple |

**File:** `android/core/ui/src/main/kotlin/com/encore/core/ui/theme/SetColor.kt`

---

## 3. Gesture System (Performance Mode)

### Three-Gesture Model in `SongContent`

The performance screen uses two separate `pointerInput` modifiers with distinct keys to allow both gesture systems to coexist without conflict:

```kotlin
.pointerInput("transform") { detectTransformGestures { ... } }   // Pinch-to-zoom
.pointerInput("tap")        { detectTapGestures(onDoubleTap, onTap) } // Tap control
```

Using identical keys (`Unit`) caused only one detector to run. Distinct string keys fix this.

| Gesture | Behavior |
|---------|----------|
| **Single Tap** | Toggles `showControls`. Shows/hides the floating Zoom HUD (bottom-right). |
| **Double Tap** | Calls `viewModel.resetTextSize()` → sets zoom to `1.0f` (100%). |
| **Pinch** | `detectTransformGestures` scales `currentZoom` in real-time, clamped `[0.5, 3.0]`. |

### Auto-Hide Timer
A `LaunchedEffect(showControls)` block delays 3 seconds then sets `showControls = false`. Any interaction that sets `showControls = true` restarts the timer.

### 500ms Debounced Persistence
`SongDetailViewModel.updateTextSize()` cancels and relaunches a `saveZoomJob` coroutine on every call. The job waits 500ms then calls `songRepository.updateZoomLevel(songId, zoom)`. This prevents a DB write on every pinch frame while guaranteeing the final zoom is persisted.

```kotlin
saveZoomJob?.cancel()
saveZoomJob = viewModelScope.launch {
    delay(500L)
    songRepository.updateZoomLevel(currentSongId, clampedMultiplier)
}
```

Zoom is restored from `SongEntity.lastZoomLevel` when `loadSong()` runs.

**Files:**
- `android/feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailScreen.kt`
- `android/feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailViewModel.kt`

---

## 4. Resolved Issues

### "Set not found" Error — RESOLVED
**Root cause:** `addSongToSetNumber()` in `LibraryViewModel` iterated over existing setlists looking for a set with a matching number. If the database was empty (no setlists yet created), or if the matching set number had never been added, the function emitted "Set N not found" and returned.

**Fix:** `SetlistRepository.getOrCreateSetByNumber(setNumber: Int): SetEntity`
- If no setlists exist: auto-creates "My Setlist" with Sets 1–4.
- If setlists exist but none has the requested set number: creates the set in the first setlist.
- Guarantees a valid `SetEntity` is always returned; the ViewModel never hits a "not found" state.

**Files:**
- `android/core/data/src/main/kotlin/com/encore/core/data/repository/SetlistRepository.kt`
- `android/feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt`

### Key Visibility — RESOLVED (two-part fix)

**Part 1 — Backfill for existing songs:** Previously imported songs stored `currentKey = null` because the key parser ran at import time. On subsequent launches the key was never re-parsed. `LibraryViewModel.init` now calls `backfillMissingKeys()`, which:
1. Calls `songRepository.getSongsWithoutKey()` (new DAO query: `WHERE current_key IS NULL`).
2. Re-runs `parseKey(song.markdownBody)` on each result.
3. If a key is found, calls `upsertSong(song.copy(currentKey = key))`.

This runs once per ViewModel creation and is idempotent.

**Part 2 — Position fix in the UI:** `KeyBadge` was rendered inside the title/artist `Row` where it was frequently clipped by `TextOverflow.Ellipsis`. It is now rendered in the right-side metadata zone of the row, directly left of the colored Set Circles:

```
[drag handle]  [Title – Artist]  [G]  [●1][●3]  [+ pill]
```

**Files:**
- `android/core/data/src/main/kotlin/com/encore/core/data/dao/SongDao.kt`
- `android/core/data/src/main/kotlin/com/encore/core/data/repository/SongRepository.kt`
- `android/feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt`
- `android/feature/library/src/main/kotlin/com/encore/feature/library/LibraryScreen.kt`

### Reorder Position Constraint Bug — RESOLVED
`SetlistRepository.reorderSongInSet` previously shifted positions row-by-row in a sequence that could trigger the `UNIQUE(set_id, position)` constraint mid-operation (e.g., moving position 3 to position 4 when position 4 already exists). Replaced with a 2-phase batch algorithm:
1. Set all entry positions to unique negative sentinel values (`-(i+1)`).
2. Set all positions to the correct 0-indexed values.

No intermediate state violates the constraint.

---

## 5. Design Document Verification

**Location:** `docs/design/`

| File | Purpose | Status |
|------|---------|--------|
| `display options - colors.png` | Canonical section header hex codes (Intro → Instrumental) | ✅ Present |
| `display options.png` | Full display options panel layout reference | ✅ Present |
| `main UI.png` | Command Center layout: cards, song list, sets footer | ✅ Present |
| `Screenshot 2026-03-27 at 9.54.10 AM.png` | Runtime zoom HUD screenshot used as visual reference | ⚠️ Not committed |

**Note:** The zoom HUD screenshot was referenced during development but was not formally committed to `docs/design/`. The 3 canonical design files are present and sufficient for the implemented features. If the screenshot is needed for handoff review, it should be added to `docs/design/` and committed.

---

## 6. Full Change Inventory

### New DAO Methods
| File | Method | Purpose |
|------|--------|---------|
| `SongDao` | `getSongsWithoutKey()` | Key backfill query |
| `SongDao` | `getSongsInSetOrdered(setNumber)` | Position-ordered set view |
| `SetDao` | `getBySetlistAndNumber(setlistId, number)` | Direct set lookup |
| `SetlistDao` | `getSetlistsOnce()` | One-shot setlist list |
| `SetEntryDao` | `getEntryBySongAndSet(setId, songId)` | Entry lookup for remove/reorder |
| `SetEntryDao` | `getEntriesForSetList(setId)` | Batch list for reorder |
| `SetEntryDao` | `updatePosition(id, position)` | Per-entry position update |

### New / Updated Repository Methods
| Repository | Method | Change |
|-----------|--------|--------|
| `SongRepository` | `getSongsWithoutKey()` | New |
| `SongRepository` | `getSongsInSetOrdered(setNumber)` | New |
| `SetlistRepository` | `getEntryForSongInSet(setId, songId)` | New |
| `SetlistRepository` | `getOrCreateSetByNumber(setNumber)` | New — auto-creates setlist/set |
| `SetlistRepository` | `reorderSongInSet(entryId, newPos)` | Fixed — 2-phase algorithm |

### Key UI Changes
| Screen | Change |
|--------|--------|
| `SongDetailScreen` | Full-screen, no bars; persistent `✕` back button (top-left, 45% opacity) |
| `CommandCenterScreen` | "Show All" button in song library header clears search + set filter |
| `SongListItem` | Key badge moved right of title/artist, left of set circles |
| `SongListItem` | `+` replaced with outlined pill button (`RoundedCornerShape(50.dp)`, 1dp border) |
| `SongListItem` | Swipe confirmation shows two distinct actions: "Remove from Set N" vs. "Delete from Library" |
| `SongList` | Drag handle `☰` visible when set filter active; long-press to reorder |

---

## 7. State of the App — Ready for Milestone 4

### What works end-to-end
- Import `.md` files via SAF picker → key parsed on import + backfilled for existing songs
- Library search: global search ignores set filter; set filter shows position-ordered songs
- Add song to Set 1–4 via pill button — auto-creates setlist if none exists
- Remove from set (swipe → "Remove from Set N") or delete permanently ("Delete from Library")
- Drag-to-reorder songs within an active set view (long-press drag handle)
- Tap song row → `SongDetailScreen` (full-screen, offline, no chrome)
- Pinch-to-zoom, double-tap reset, single-tap HUD, 3s auto-hide
- Zoom level persisted per song (500ms debounce)
- Section headers colored by type (Verse, Chorus, Bridge, etc.)
- `✕` back button always available in performance mode
- Set filter chips (1–4) + "Show All" button for instant filter reset

### Known Limitations / Deferred to Milestone 4
- **No cloud sync** — all data is local Room DB. `SyncStatus.PENDING_UPLOAD` set but never acted on.
- **No Google Sign-In** — `userId` is hardcoded `"local-user"` throughout.
- **Single setlist model** — "My Setlist" is auto-created; no setlist management UI.
- **Auto-scroll** — infrastructure retained in `SongDetailViewModel` but de-scoped from UI (Post-v1 backlog).
- **Dark mode** — system theme switches correctly but no explicit dark mode toggle.
- **Edit mode** — `markdownBody` is read-only in this milestone. Song editing is Milestone 4+ scope.
- **Conflict resolution UI** — schema supports `ConflictRecord` entities but no UI exists yet.

### Milestone 4 Entry Checklist
- [ ] Google Sign-In flow (single active device session)
- [ ] `SyncStatus` state machine wired to actual Ktor API calls
- [ ] Manual "Sync Now" action with conflict detection UI
- [ ] Setlist management screen (create, rename, delete setlists)
- [ ] Multi-setlist support in Add-to-Set picker

---

*Document generated 2026-03-27 from branch `feature/performance-viewer` at commit `e3b4af8`.*
