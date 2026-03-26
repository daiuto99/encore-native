# Encore Navigation Map

**Milestone:** 1 - Foundation
**Status:** Draft
**Last Updated:** 2026-03-26

## Screen Hierarchy and Flow

```
┌───────────────────────────────────────────────────────────────┐
│                      App Entry Point                          │
└────────────────────────────┬──────────────────────────────────┘
                             │
                ┌────────────┴────────────┐
                │                         │
         [Authenticated?]           [Not Authenticated]
                │                         │
                ▼                         ▼
    ┌─────────────────────┐   ┌─────────────────────┐
    │   Home Screen       │   │   Auth Screen       │
    │   (Library)         │   │   (Google Sign-In)  │
    └─────────────────────┘   └─────────────────────┘
                │
                │
    ┌───────────┴───────────────────────────────────┐
    │                                               │
    ▼                                               ▼
┌─────────────────────┐                 ┌─────────────────────┐
│  Library Screen     │────Import───────│  Import Flow        │
│  - Search           │    (Songs)      │  - File picker      │
│  - Browse all songs │                 │  - Duplicate check  │
│  - Import button    │                 │  - Conflict modal   │
└─────────────────────┘                 └─────────────────────┘
    │         │
    │         │
    │         └─────────────────────────┐
    │                                   │
    ▼                                   ▼
┌─────────────────────┐     ┌─────────────────────────────────┐
│  Song Viewer        │     │  Setlists Overview              │
│  - Formatted chart  │     │  - List all setlists            │
│  - Edit button      │     │  - Create new setlist           │
│  - Performance btn  │     │  - Search setlists              │
└─────────────────────┘     └─────────────────────────────────┘
    │         │                          │
    │         │                          ▼
    │         │              ┌─────────────────────────────────┐
    │         │              │  Setlist Detail (Set Overview)  │
    │         │              │  - Tab for each set             │
    │         │              │  - Song list per set            │
    │         │              │  - Add/remove sets              │
    │         │              │  - Performance mode button      │
    │         │              └─────────────────────────────────┘
    │         │                          │         │
    │         │                          │         ▼
    │         │                          │  ┌─────────────────┐
    │         │                          │  │  Set Editor     │
    │         │                          │  │  - Add songs    │
    │         │                          │  │  - Reorder      │
    │         │                          │  │  - Remove songs │
    │         │                          │  └─────────────────┘
    │         │                          │
    │         ▼                          ▼
    │  ┌─────────────────────┐  ┌─────────────────────────────┐
    │  │  Edit Song Screen   │  │  Performance Mode           │
    │  │  - Markdown editor  │  │  - Dark theme               │
    │  │  - Save/Cancel      │  │  - Swipe navigation         │
    │  │  - Preview          │  │  - Search overlay           │
    │  └─────────────────────┘  │  - Return to set            │
    │                            └─────────────────────────────┘
    │                                        │
    │                                        ▼
    └────────────────────────────┬──────────────────────────────┘
                                 │
                                 ▼
                    ┌─────────────────────────────┐
                    │  Sync & Settings            │
                    │  - Sync now button          │
                    │  - Conflict resolution UI   │
                    │  - Account info             │
                    │  - Sign out                 │
                    └─────────────────────────────┘
```

## Screen Definitions

### 1. Auth Screen
**Purpose:** Google Sign-In and device activation.

**Entry:** App launch when not authenticated.

**Actions:**
- Tap "Sign in with Google"
- Handle OAuth flow
- Activate device session
- Navigate to Home/Library

**Exit:** Authenticated → Library Screen

---

### 2. Library Screen (Home)
**Purpose:** Browse and search the master song library.

**Entry:**
- Post-authentication
- Bottom nav "Library" tab
- Back from Song Viewer

**UI Elements:**
- Search bar (filters by title/artist)
- "Import Songs" button
- Scrollable list of songs (title, artist, key)
- Tap song → Song Viewer

**Actions:**
- Search songs
- Import songs (→ Import Flow)
- View song (→ Song Viewer)
- Navigate to Setlists (→ Setlists Overview)

---

### 3. Import Flow
**Purpose:** Import single or multiple markdown song files.

**Entry:** Tap "Import Songs" from Library Screen

**Steps:**
1. **File Picker:** Select .md file(s)
2. **Parse:** Extract title, artist, key from markdown
3. **Duplicate Check:** Compare (title, artist) against library
4. **Conflict Modal (if duplicate):**
   - Show existing song vs new song
   - Buttons: "Replace", "Keep Both", "Cancel"
   - "Keep Both" appends (1), (2), etc. to title
5. **Save:** Write to Room DB, mark sync_status = "pending_upload"
6. **Confirmation:** Show success message

**Exit:** Return to Library Screen

---

### 4. Song Viewer
**Purpose:** Display formatted markdown chart in normal reading mode.

**Entry:**
- Tap song from Library Screen
- Tap song from Setlist Detail

**UI Elements:**
- Rendered markdown (title, artist, key, lyrics, chords)
- "Edit" button (→ Edit Song Screen)
- "Perform" button (→ Performance Mode for this song)
- Back button (→ previous screen)

**Actions:**
- Read chart
- Edit song (→ Edit Song Screen)
- Enter performance mode (→ Performance Mode)

---

### 5. Edit Song Screen
**Purpose:** Edit markdown body of a song.

**Entry:** Tap "Edit" from Song Viewer

**UI Elements:**
- Markdown text editor (full screen)
- "Save" button
- "Cancel" button
- Optional "Preview" toggle

**Actions:**
- Edit markdown text
- Save: Increment version, update updated_at, mark sync_status = "pending_upload"
- Cancel: Discard changes

**Exit:** Return to Song Viewer (or Library)

---

### 6. Setlists Overview
**Purpose:** Browse and manage setlists.

**Entry:**
- Bottom nav "Setlists" tab
- Back from Setlist Detail

**UI Elements:**
- "Create Setlist" button
- List of setlists (name, date)
- Tap setlist → Setlist Detail

**Actions:**
- Create new setlist (→ Setlist Detail with empty Set 1)
- View setlist (→ Setlist Detail)
- Delete setlist (confirmation dialog)

---

### 7. Setlist Detail (Set Overview)
**Purpose:** View all sets in a setlist with song lists.

**Entry:** Tap setlist from Setlists Overview

**UI Elements:**
- Setlist name at top
- Tabs for each set (Set 1, Set 2, etc.) with color indicators
- For each set: scrollable list of songs in order
- "Add Set" button
- "Remove Set" button (confirmation if not empty)
- "Performance Mode" button (→ Performance Mode for full setlist)
- Tap set tab → Set Editor for that set

**Actions:**
- Switch between sets (tap tabs)
- Add set: Create new Set(number = max + 1)
- Remove set: Delete set, renumber subsequent sets
- Edit set (→ Set Editor)
- Enter performance mode (→ Performance Mode)

---

### 8. Set Editor
**Purpose:** Add, remove, and reorder songs within a set.

**Entry:** Tap set tab from Setlist Detail (or Edit button)

**UI Elements:**
- Set number and color indicator
- Ordered list of songs with drag handles
- "Add Song" button (→ Song Picker modal)
- "Remove" icon per song
- "Done" button

**Actions:**
- Add song from library (modal search/pick)
- Remove song from set
- Reorder songs (drag-and-drop)
- Save changes (update positions, mark sync_status)

**Exit:** Return to Setlist Detail

---

### 9. Performance Mode
**Purpose:** Stage-optimized view for live performance.

**Entry:**
- Tap "Perform" from Song Viewer (single song)
- Tap "Performance Mode" from Setlist Detail (full setlist)

**UI Elements:**
- Full-screen dark theme
- Current song rendered large and readable
- Swipe left/right for prev/next song
- Search icon (top-right) → Search Overlay
- "Return to Set" button (→ Setlist Detail at previous position)
- Optional nav arrows (left/right edges)

**State Management:**
- Track current setlist_id, set_number, song_id, scroll_offset
- Persist state to survive app background/foreground

**Actions:**
- Swipe to navigate songs
- Search for song (→ Search Overlay)
- Exit to setlist (→ Setlist Detail)

**Exit:**
- Tap "Return to Set" → Setlist Detail
- Back button → previous screen

---

### 10. Performance Mode Search Overlay
**Purpose:** Quick search within performance mode to jump to any song.

**Entry:** Tap search icon in Performance Mode

**UI Elements:**
- Semi-transparent overlay over performance view
- Search bar with auto-focus keyboard
- Filtered list of songs (from full library or current setlist)
- Tap song → Load song in Performance Mode

**Actions:**
- Type to filter
- Select song → Close overlay, show selected song
- Cancel → Close overlay, return to current song

---

### 11. Sync & Settings Screen
**Purpose:** Manual sync, conflict resolution, account management.

**Entry:** Bottom nav "Settings" tab

**UI Elements:**
- User profile info (name, email)
- "Sync Now" button
- Last sync timestamp
- Active device info
- Conflict list (if any pending)
- "Sign Out" button

**Actions:**
- Sync now:
  1. Collect pending local changes
  2. Send to backend
  3. Receive remote updates and conflicts
  4. If conflicts: Show Conflict Resolution UI
  5. If no conflicts: Apply updates, update sync token
- Resolve conflicts (→ Conflict Resolution Modal)
- Sign out: Clear session, return to Auth Screen

---

### 12. Conflict Resolution Modal
**Purpose:** User chooses version when sync detects conflicts.

**Entry:** Conflicts detected during sync

**UI Elements:**
- Conflict summary (entity type, entity name)
- Side-by-side comparison:
  - "Your Version" (local)
  - "Server Version" (remote)
- Buttons: "Keep Mine", "Use Server", "Cancel"

**Actions:**
- Choose local version: Send resolution to server, apply locally
- Choose remote version: Apply remote version locally
- Cancel: Leave conflict unresolved (can resolve later)

**Exit:** Return to Sync & Settings Screen

---

## Navigation Patterns

### Bottom Navigation (Always Visible)
- **Library** (Home icon)
- **Setlists** (List icon)
- **Settings** (Gear icon)

### Contextual Back Stack
- Each screen maintains proper back navigation
- Back button returns to previous logical screen
- Deep links preserve context (e.g., Song Viewer remembers source)

### Full-Screen Modes
- **Performance Mode:** Hides bottom nav, immersive full screen
- **Edit Song Screen:** Hides bottom nav, focuses on editing

---

## Milestone 1 Implementation Scope

For Foundation (Milestone 1), only the following screens need proof-of-concept:
1. **Auth Screen** (Google Sign-In stub or placeholder)
2. **Library Screen** (empty state or mock data)
3. **Song Viewer** (one sample markdown song rendered)

Remaining screens will be built in Milestones 2-4.

---

**Status:** Ready for implementation planning.
