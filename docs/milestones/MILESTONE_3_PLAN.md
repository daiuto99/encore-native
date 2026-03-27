# Milestone 3: Performance Engine

**Goal:** Build a production-ready Performance Mode for live shows with persistent zoom, rich HTML rendering, and optimized UX. Focus on data integrity and visual clarity for stage performance.

**Status:** Foundation Complete - Starting Refinement (0/4 tasks)
**Last Updated:** 2026-03-27

---

## 🎯 Priority Shift: Production-Ready Performance Mode

**Previous Scope (Prototype):**
- ✅ Basic markdown rendering
- ✅ Pinch-to-zoom (ephemeral state)
- ✅ Auto-scroll engine (experimental)

**New Scope (Production):**
- 🔄 Performance Mode UI refinement
- 🔄 Per-song zoom level persistence
- 🔄 Full CSS/HTML rendering for colored sections
- 🔄 De-scope auto-scroll (move to Post-v1 backlog)

---

## Task Overview

| Task | Description | Status | Priority | Files |
|------|-------------|--------|----------|-------|
| 1 | Performance Mode UI Refinement | 🔄 IN PROGRESS | P0 | `feature/performance/` |
| 2 | Per-Song Zoom Level Persistence | ⏳ NEXT | P0 | `core/data/entities/` |
| 3 | Full CSS/HTML Support | ⏳ PENDING | P1 | `feature/performance/` |
| 4 | De-scope Auto-Scroll | ⏳ PENDING | P2 | `feature/performance/` |

---

## 🔄 Task 1: Performance Mode UI Refinement - IN PROGRESS

**Date Started:** 2026-03-27

### Goal:
Transform the prototype Song Detail Screen into a production-ready Performance Mode optimized for live stage use on 11-inch tablets in portrait orientation.

### Requirements:

#### 1. Full-Screen Performance Mode
- **No Top Bar:** Maximize screen real estate for song content
- **Gesture-Based Navigation:** Swipe from left edge to go back (Android standard)
- **Status Bar Hiding:** Immersive mode for live performance
- **Tap-to-Reveal Controls:** Tap anywhere to show/hide floating zoom controls

#### 2. Floating Zoom Controls (Overlay)
- **Position:** Bottom-right corner, semi-transparent background
- **Controls:**
  - Zoom percentage badge (e.g., "100%")
  - Zoom In button (+)
  - Zoom Out button (-)
  - Reset button (optional)
- **Auto-Hide:** Fade out after 3 seconds of inactivity
- **Tap-to-Show:** Tap anywhere on screen to reveal controls

#### 3. Song Metadata Overlay (Optional)
- **Position:** Top of screen, semi-transparent
- **Content:** Title, Artist, Key
- **Behavior:** Fade out after 3 seconds, tap to reveal
- **Alternative:** Remove entirely, show only on song load

#### 4. Pinch-to-Zoom (Existing - Keep)
- ✅ Multi-touch gesture detection
- ✅ Real-time text scaling (0.5x - 3.0x)
- ✅ All typography scales proportionally

### Files to Modify:
- `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailScreen.kt`
  - Remove Scaffold and TopAppBar
  - Add Box layout with full-screen content
  - Add floating zoom controls (AnimatedVisibility)
  - Implement tap gesture for control reveal
  - Add auto-hide timer (3 seconds)

### Files to Create:
- `feature/performance/src/main/kotlin/com/encore/feature/performance/components/FloatingZoomControls.kt` (optional - inline is fine)

### Acceptance Criteria:
- [ ] No top bar or navigation chrome visible by default
- [ ] Tap anywhere on screen reveals zoom controls
- [ ] Zoom controls fade out after 3 seconds of inactivity
- [ ] Pinch-to-zoom continues to work
- [ ] Back gesture (swipe from left edge) navigates back
- [ ] Zoom percentage displayed in overlay
- [ ] Clean, distraction-free performance view

---

## ⏳ Task 2: Per-Song Zoom Level Persistence - NEXT

**Status:** Blocked by Task 1
**Priority:** P0 (Critical for UX)

### Goal:
Persist zoom level per song so musicians can set preferred text size once and have it remembered across app sessions.

### Requirements:

#### 1. Schema Update
Add `zoomLevel` field to SongEntity:
```kotlin
@Entity(tableName = "songs")
data class SongEntity(
    // ... existing fields ...
    val zoomLevel: Float = 1.0f  // Default 100% zoom
)
```

#### 2. Database Migration
Create migration from version N to N+1:
- Add `zoom_level` column with default value 1.0
- Update `EncoreDatabase` version number
- Add migration strategy in database builder

#### 3. ViewModel Integration
Update `SongDetailViewModel`:
- Load zoom level from song entity on `loadSong()`
- Save zoom level to repository on `updateTextSize()`
- Debounce save operations (500ms) to avoid excessive writes

#### 4. Repository Update
Add to `SongRepository`:
```kotlin
suspend fun updateZoomLevel(songId: String, zoomLevel: Float): Result<Unit>
```

### Files to Modify:
- `core/data/src/main/kotlin/com/encore/core/data/entities/SongEntity.kt`
- `core/data/src/main/kotlin/com/encore/core/data/db/EncoreDatabase.kt`
- `core/data/src/main/kotlin/com/encore/core/data/repository/SongRepository.kt`
- `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailViewModel.kt`

### Acceptance Criteria:
- [ ] Zoom level saved to database on change
- [ ] Zoom level restored when song reopened
- [ ] Default zoom is 1.0 (100%) for new songs
- [ ] Zoom persists across app restarts
- [ ] Database migration executes without data loss
- [ ] Debouncing prevents excessive DB writes

---

## ⏳ Task 3: Full CSS/HTML Support - PENDING

**Status:** Blocked by Task 2
**Priority:** P1 (Important for stage visibility)

### Goal:
Enable full CSS/HTML rendering in markdown so musicians can use colored section headers (e.g., `<span style="color:blue">Verse 1</span>`) for quick visual navigation during live performances.

### Current State:
- mikepenz markdown renderer **partially** supports HTML tags
- Basic `<span>` tags may render but **CSS styles might be stripped**
- Need to verify if `style="color:..."` attributes are preserved

### Requirements:

#### 1. HTML Rendering Investigation
- Test current mikepenz renderer with colored `<span>` tags
- Determine if CSS `style` attributes are supported
- If not supported: Evaluate alternatives (WebView, custom renderer, Compose HTML library)

#### 2. Implementation Options

**Option A: mikepenz with HTML plugin (preferred)**
```kotlin
// Check if mikepenz supports HTML plugin
implementation("com.mikepenz:multiplatform-markdown-renderer-html:0.14.0")
```

**Option B: Custom Markdown Parser**
- Parse `<span style="color:...">` tags manually
- Apply colors using AnnotatedString with SpanStyle
- Replace markdown sections with Compose Text elements

**Option C: WebView Fallback**
- Render markdown as HTML in WebView
- Trade-off: Heavier, but full CSS support
- Not ideal for performance, but guaranteed compatibility

#### 3. Test Cases
Create sample songs with:
- `<span style="color:blue">Verse 1</span>`
- `<span style="color:red">Chorus</span>`
- `<span style="color:green; font-weight:bold">Bridge</span>`

### Files to Modify:
- `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailScreen.kt`
- `feature/performance/build.gradle.kts` (if new dependency needed)

### Acceptance Criteria:
- [ ] `<span style="color:blue">Text</span>` renders with blue color
- [ ] `<span style="color:red">Text</span>` renders with red color
- [ ] Multiple colors supported (blue, red, green, yellow, etc.)
- [ ] `font-weight:bold` renders as bold text
- [ ] Colored sections visible in dark and light themes
- [ ] No performance degradation

---

## ⏳ Task 4: De-scope Auto-Scroll (Move to Backlog) - PENDING

**Status:** Blocked by Task 3
**Priority:** P2 (Nice-to-have, not MVP)

### Goal:
Remove auto-scroll UI from Performance Mode while keeping the underlying code infrastructure for future Post-v1 implementation.

### Rationale:
- **User Feedback:** Musicians prefer manual scrolling for precise control
- **Complexity:** Duration parsing and speed calibration require extensive testing
- **MVP Focus:** Zoom persistence and HTML rendering are higher priority
- **Future Work:** Re-introduce as advanced feature in Post-v1

### Requirements:

#### 1. Remove from UI
- Remove Play/Pause button from SongDetailScreen
- Remove auto-scroll toggle from top bar (already removed in Task 1)
- Remove auto-scroll LaunchedEffect animation

#### 2. Keep in ViewModel (Plumbing)
- **Keep** `isAutoScrolling` state (unused but ready)
- **Keep** `scrollSpeedPxPerSecond` calculation
- **Keep** `toggleAutoScroll()` function
- **Keep** `parseDuration()` function
- **Keep** `calculateScrollSpeed()` function

#### 3. Documentation
- Add note to MILESTONE_3_PLAN.md: "Auto-scroll moved to Post-v1 backlog"
- Create `docs/backlog/AUTO_SCROLL_FEATURE.md` with implementation notes

### Files to Modify:
- `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailScreen.kt`
  - Remove Play/Pause button (if not already removed in Task 1)
  - Remove auto-scroll LaunchedEffect
  - Comment: "Auto-scroll infrastructure retained for Post-v1"
- `feature/performance/src/main/kotlin/com/encore/feature/performance/SongDetailViewModel.kt`
  - Add comment: "Auto-scroll methods retained for future use"

### Files to Create:
- `docs/backlog/AUTO_SCROLL_FEATURE.md`

### Acceptance Criteria:
- [ ] No auto-scroll UI visible in Performance Mode
- [ ] ViewModel retains auto-scroll methods (commented as future use)
- [ ] Duration parsing code remains functional
- [ ] Scroll speed calculation logic intact
- [ ] Backlog documentation created

---

## 🏗️ Foundation Complete (Prototype Phase)

### ✅ Completed in Previous Session:

#### SongDetailViewModel (feature/performance/SongDetailViewModel.kt)
- ✅ Song data loading from repository
- ✅ Text size state (0.5x - 3.0x)
- ✅ Auto-scroll state (to be hidden in UI)
- ✅ Duration parsing from markdown
- ✅ Scroll speed calculation

#### SongDetailScreen (feature/performance/SongDetailScreen.kt)
- ✅ Markdown rendering with mikepenz library
- ✅ Pinch-to-zoom gesture detection
- ✅ TopAppBar with controls (to be removed in Task 1)
- ✅ Zoom +/- buttons (to be moved to floating overlay)
- ✅ Auto-scroll toggle (to be removed in Task 4)

#### Navigation Integration
- ✅ Library → Song Detail navigation
- ✅ Setlist → Song Detail navigation
- ✅ ViewModelFactory wiring
- ✅ Routes and composable setup

#### Dependencies
- ✅ mikepenz markdown renderer v0.14.0
- ✅ Material Icons Extended
- ✅ Navigation Compose
- ✅ Lifecycle ViewModel Compose

---

## 📊 Milestone Success Criteria

**Core Features:**
- [ ] Full-screen Performance Mode (no chrome)
- [ ] Floating zoom controls with auto-hide
- [ ] Per-song zoom level persistence
- [ ] Full CSS/HTML rendering for colored sections
- [ ] Pinch-to-zoom (already working)
- [ ] Clean, distraction-free stage view

**Data Integrity:**
- [ ] Zoom level persists across app restarts
- [ ] Database migration executes cleanly
- [ ] No data loss during migration

**User Experience:**
- [ ] Tap-to-reveal controls
- [ ] 3-second auto-hide timer
- [ ] Smooth pinch-to-zoom
- [ ] Instant song load (<500ms)
- [ ] Visible on 11-inch tablet in portrait
- [ ] Readable from 14x20 room distance

**De-Scoped (Post-v1):**
- Auto-scroll engine (code retained, UI removed)
- Advanced duration calibration
- Edit mode (separate milestone)

---

## 🚀 Next Steps

### Immediate (Task 1):
1. Remove Scaffold and TopAppBar from SongDetailScreen
2. Add full-screen Box layout
3. Create floating zoom controls (bottom-right)
4. Implement tap gesture for reveal/hide
5. Add 3-second auto-hide timer
6. Test on 11-inch tablet

### Short-Term (Task 2):
1. Add `zoomLevel` field to SongEntity
2. Create database migration
3. Update SongRepository with save/load methods
4. Integrate zoom persistence in ViewModel
5. Test persistence across app restarts

### Medium-Term (Task 3):
1. Test current HTML rendering capabilities
2. Evaluate mikepenz HTML plugin
3. Implement custom span parser if needed
4. Add colored section samples to test data

### Long-Term (Task 4):
1. Remove auto-scroll UI elements
2. Document auto-scroll in backlog
3. Retain ViewModel infrastructure

---

## 🎸 Build and Run

**Build Command:**
```bash
cd android
./gradlew assembleDebug
```

**Install Command:**
```bash
./gradlew :app:installDebug
```

**Git Commands (Executed):**
```bash
# Commit milestone-2-final-stable
git add -A
git commit -m "chore: Milestone 2 Final Stable + Performance Engine Foundation"

# Create feature branch
git checkout -b feature/performance-viewer
```

---

## 📝 Notes

### Why De-scope Auto-Scroll?
- Musicians prefer manual scrolling for precise control during live performance
- Duration parsing is complex and requires extensive per-song calibration
- Zoom persistence and HTML rendering are higher priority for MVP
- Code infrastructure retained for easy re-introduction in Post-v1

### Why Floating Controls?
- Maximizes screen real estate for song content
- Follows Android full-screen immersive mode patterns
- Reduces distraction during live performance
- Quick access when needed (tap to reveal)

### Why Per-Song Zoom Persistence?
- Different songs have different complexity (simple hymns vs. complex arrangements)
- Musicians want to set zoom once per song and forget it
- Critical for muscle memory during live performance
- Eliminates friction in performance workflow

---

## 🔗 Related Documentation

- [Product Overview](../01_Product_Overview.md)
- [Technical Specification](../03_Technical_Specification.md)
- [Data Model](../architecture/data-model.md)
- [MILESTONE_2_PLAN.md](./MILESTONE_2_PLAN.md)
