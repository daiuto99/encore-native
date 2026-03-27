# Milestone 2: Library Management

**Goal:** Build a functional song library with import, search, and basic CRUD operations. Users can import markdown charts, view their library, and search for songs.

**Status:** Tasks 1-7 Complete (Ready for Task 8)
**Last Updated:** 2026-03-26

---

## Task Overview

| Task | Description | Status | Files |
|------|-------------|--------|-------|
| 1 | Room Entities | ✅ COMPLETED | `core/data/entities/` |
| 2 | DAOs | ✅ COMPLETED | `core/data/dao/` |
| 3 | Database & TypeConverters | ✅ COMPLETED | `core/data/db/` |
| 4 | Repositories | ✅ COMPLETED | `core/data/repository/` |
| 5 | Library Screen UI | ✅ COMPLETED | `feature/library/` |
| 6 | Import Flow | ✅ COMPLETED | `feature/library/LibraryViewModel.kt` |
| 7 | Setlist Management UI | ✅ COMPLETED | `feature/setlists/` |
| 8 | Song Detail Screen | 🔄 NEXT | `feature/library/detail/` |
| 9 | Testing & Polish | ⏳ PENDING | All modules |

---

## ✅ Task 1: Room Entities (COMPLETED)

**Date Completed:** 2026-03-26

### Files Created:
- `core/data/src/main/kotlin/com/encore/core/data/entities/SongEntity.kt`
- `core/data/src/main/kotlin/com/encore/core/data/entities/SetlistEntity.kt`
- `core/data/src/main/kotlin/com/encore/core/data/entities/SetEntity.kt`
- `core/data/src/main/kotlin/com/encore/core/data/entities/SetEntryEntity.kt`
- `core/data/src/main/kotlin/com/encore/core/data/entities/SyncStatus.kt`

### Implementation Details:
- **SongEntity:** UUID primary key, userId, title, artist, currentKey, markdownBody, originalImportBody, version tracking, timestamps, SyncStatus
- **Indexes:** Optimized for search (title, artist) and duplicate detection (unique constraint on userId + title + artist)
- **SetlistEntity:** Version tracking for conflict detection
- **SetEntity:** Foreign key to Setlist with CASCADE delete, number field for ordering
- **SetEntryEntity:** Junction table with foreign keys to Set and Song, position-based ordering (0-indexed)
- **SyncStatus:** Enum with SYNCED, PENDING_UPLOAD, PENDING_DELETE

### Key Decisions:
- UUIDs stored as String (not UUID type) for Room compatibility
- Timestamps stored as Long (Unix epoch milliseconds)
- Nullable `currentKey` and `originalImportBody`
- Unique constraint on (userId, title, artist) for duplicate detection during import

---

## ✅ Task 2: DAOs (COMPLETED)

**Date Completed:** 2026-03-26

### Files Created:
- `core/data/src/main/kotlin/com/encore/core/data/dao/SongDao.kt`
- `core/data/src/main/kotlin/com/encore/core/data/dao/SetlistDao.kt`
- `core/data/src/main/kotlin/com/encore/core/data/dao/SetDao.kt`
- `core/data/src/main/kotlin/com/encore/core/data/dao/SetEntryDao.kt`
- `core/data/src/main/kotlin/com/encore/core/data/relations/SetlistWithSets.kt`
- `core/data/src/main/kotlin/com/encore/core/data/relations/SetWithEntries.kt`
- `core/data/src/main/kotlin/com/encore/core/data/relations/SetEntryWithSong.kt`

### Implementation Details:
- **SongDao:** `searchSongs()` with LIKE query for partial matching (performance requirement for live shows), `findDuplicate()` for import
- **SetlistDao:** `@Transaction` query to fetch setlist with all nested sets and songs in order
- **SetDao:** Renumbering support with `getSetsToRenumber()`, `getMaxSetNumber()`
- **SetEntryDao:** Position management with `getEntriesToReposition()`, `getMaxPosition()`

### Key Decisions:
- LIKE search pattern: `LIKE '%' || :query || '%'` for partial matching
- @Transaction annotation ensures atomic multi-table queries
- Flow<List<Entity>> for reactive UI updates
- suspend functions for coroutine-based async operations

---

## ✅ Task 3: Database & TypeConverters (COMPLETED)

**Date Completed:** 2026-03-26

### Files Created:
- `core/data/src/main/kotlin/com/encore/core/data/db/EncoreDatabase.kt`
- `core/data/src/main/kotlin/com/encore/core/data/db/TypeConverters.kt`

### Implementation Details:
- **EncoreDatabase:** Room database with singleton pattern, 4 entities registered, all DAOs exposed
- **Pre-population:** DatabaseCallback with `onCreate()` inserts "Amazing Grace" demo song on first launch
- **TypeConverters:** Only SyncStatus enum converter (fromSyncStatus/toSyncStatus)
  - **Note:** Long, Int, String are handled natively by Room - no converters needed

### Build Configuration:
- **KSP Plugin:** Version 1.9.22-1.0.17
- **Room Version:** 2.6.1
- **Schema Export:** `$projectDir/schemas` for migration tracking

### Key Decisions:
- Singleton pattern with @Volatile instance and synchronized block
- Pre-populated with full 4-verse Amazing Grace with chords
- Schema export enabled for future migrations
- Only custom enum types require TypeConverters

### Error Fixed:
- **Issue:** Build failed with "multiple methods defining the same conversion for Long?"
- **Fix:** Removed redundant `fromTimestamp()` and `dateToTimestamp()` - Room handles primitives natively

---

## ✅ Task 4: Repositories (COMPLETED)

**Date Completed:** 2026-03-26

### Files Created:
- `core/data/src/main/kotlin/com/encore/core/data/repository/SongRepository.kt`
- `core/data/src/main/kotlin/com/encore/core/data/repository/SetlistRepository.kt`

### Implementation Details:

#### SongRepository:
- `getSongs()`: Returns Flow<List<SongEntity>> for reactive UI
- `searchSongs(query)`: Handles empty strings by returning all songs
- `upsertSong(song)`: Checks if song exists, then updates or inserts
- `findDuplicate()`: For import flow duplicate detection
- `getSongCount()`: Library statistics

#### SetlistRepository:
- `createSetlist()`: Creates setlist + initial Set 1
- `getSetlistWithSets()`: Fetches setlist with all nested data
- `addSongToSet()`: Appends song to end of set
- `addSongToSetAtPosition()`: Inserts with position shifting logic
- `removeSongFromSet()`: Deletes with position compacting
- `deleteSetAndRenumber()`: Automatic set renumbering
- `reorderSongInSet()`: Drag-and-drop support

### Key Patterns:
- Result<T> pattern for error handling
- Repository pattern abstracts DAO layer
- Complex business logic (renumbering, position management) in repository
- suspend functions for async operations

---

## ✅ Task 5: Library Screen UI (COMPLETED)

**Date Completed:** 2026-03-26

### Files Created:

#### Feature Module:
- `feature/library/src/main/kotlin/com/encore/feature/library/LibraryViewModel.kt`
- `feature/library/src/main/kotlin/com/encore/feature/library/LibraryScreen.kt`

#### App Module (Navigation & DI):
- `app/src/main/kotlin/com/encore/tablet/di/AppContainer.kt`
- `app/src/main/kotlin/com/encore/tablet/di/ViewModelFactory.kt`
- `app/src/main/kotlin/com/encore/tablet/navigation/Navigation.kt`

### Files Modified:
- `feature/library/build.gradle.kts` - Added dependencies (lifecycle-viewmodel-compose, navigation, Room, Coroutines)
- `app/build.gradle.kts` - Added `implementation(project(":feature:library"))`
- `app/src/main/kotlin/com/encore/tablet/MainActivity.kt` - Replaced Milestone 1 parser spike with navigation

### Implementation Details:

#### LibraryViewModel:
- Uses `flatMapLatest` to switch search query Flow automatically
- `StateFlow<String>` for search query state
- `StateFlow<List<SongEntity>>` for songs list
- `updateSearchQuery()` and `clearSearch()` functions

#### LibraryScreen:
- Material 3 TopAppBar with "Library" title
- Search bar with clear button (live filtering)
- LazyColumn with song list items (Title, Artist, Key badge)
- FloatingActionButton (+) for import (placeholder)
- Empty state messages for no songs / no search results
- Preview composables for 11-inch tablet portrait

#### Dependency Injection:
- **AppContainer:** Holds database and repository singletons
- **ViewModelFactory:** Creates ViewModels with dependencies
- Manual DI for Milestone 2 (will migrate to Hilt in Milestone 4)

#### Navigation:
- **Routes object:** Defines all app routes (`LIBRARY`, future routes commented)
- **EncoreNavHost:** Main navigation graph with Library as start destination
- Navigation callbacks for song click and import (placeholders for now)

### Key Features:
- Live search with LIKE query updates as you type
- Empty string handling shows all songs
- Reactive UI with Flow-based updates
- Material 3 design system
- 11-inch tablet optimized (Portrait)

### Error Fixed:
- **Issue:** Smart cast to 'String' impossible for `song.currentKey` (public API property from different module)
- **Fix:** Changed `if (song.currentKey != null)` to `song.currentKey?.let { key -> ... }`

### Verification:
- ✅ Built and deployed to physical tablet with `./gradlew :app:installDebug`
- ✅ App shows Library Screen on launch
- ✅ "Amazing Grace" demo song displays with key badge "G"
- ✅ Search bar functional with live filtering
- ✅ FAB visible (import placeholder)

---

## ✅ Task 6: Import Flow (COMPLETED)

**Date Completed:** 2026-03-26
**Priority:** High - Core feature for Milestone 2

### Objective:
Build the markdown import flow using Android Storage Access Framework (SAF). Users can select multiple `.md` files from their device, parse Obsidian-formatted chord sheets, detect duplicates, and import songs into the library.

### Implementation Details:

#### 1. File Picker Integration (LibraryScreen.kt:86-93)
**Tool:** Android Storage Access Framework (SAF) with `OpenMultipleDocuments()` contract
- Multi-select file picker using `rememberLauncherForActivityResult()`
- MIME type filter: `*/*` (accepts all files for flexibility)
- Directly invokes `viewModel.importSongs()` with URIs
- No persistent permissions needed (one-time access)

```kotlin
val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments()
) { uris ->
    if (uris.isNotEmpty()) {
        viewModel.importSongs(context, uris)
    }
}
```

#### 2. Filename Parsing (LibraryViewModel.kt:197-211)
**Pattern:** `Title - Artist.md`
- Regex: `"""(.+?)\s*-\s*(.+?)\.md$""".toRegex(RegexOption.IGNORE_CASE)`
- Fallback: Use filename as title, "Unknown Artist" as artist
- Removes `.md` extension before processing

#### 3. Obsidian Metadata Parsing (LibraryViewModel.kt:225-253)
**Key Extraction Regex Patterns:**
1. `(?i)\*?\*?Key:\*?\*?\s*([A-G][#b]?m?)` - Handles `**Key:** G`
2. `(?i)^\s*key\s*:\s*([A-G][#b]?m?)` - Handles `Key: G` (no bold)
3. `(?i)^\s*k\s*:\s*([A-G][#b]?m?)` - Handles `K: G` (short form)
4. `\[\s*(?i)key\s*:\s*([A-G][#b]?m?)\s*\]` - Handles `[Key: G]` (bracketed)

**Key Parsing:**
- Strips `**` markers, stores only key value (e.g., "G", "Dm", "C#m")
- Extracts first match from groupValues[1]
- Returns null if no key found (valid for songs without keys)

#### 4. Content Preservation
**Markdown Body Storage:**
- Full file content stored in `markdownBody` field
- `<span style="color:...">` HTML tags preserved for section markers
- `originalImportBody` stores exact import for reference
- No modification of chord notation (`[G]`, `[Dm]`, etc.)

#### 5. Duplicate Detection (LibraryViewModel.kt:122-128)
**Strategy:** Skip on Duplicate
- Check: `songRepository.findDuplicate(title, artist, "local-user")`
- If exists: Increment `skippedCount`, continue to next file
- **No overwrite** - user must manually delete to re-import
- **Rationale:** Prevents accidental data loss, predictable behavior

#### 6. Import Feedback (LibraryScreen.kt:95-113)
**Snackbar Messages:**
- Success: "X song(s) imported, Y duplicate(s) skipped"
- Progress: LinearProgressIndicator during import
- State: `isImporting` Flow shows visual feedback
- Result cleared after snackbar display

### Files Modified:
- ✅ `feature/library/LibraryViewModel.kt` - Added `importSongs()`, `parseFilename()`, `parseKey()`
- ✅ `feature/library/LibraryScreen.kt` - Added file picker launcher, snackbar, progress indicator
- ✅ `core/data/repository/SongRepository.kt` - Added `findDuplicate()` method

### Acceptance Criteria:
- ✅ User can tap FAB to open multi-select file picker
- ✅ User can select multiple .md files from device storage
- ✅ System parses Obsidian `**Key:**` metadata and extracts key value
- ✅ System detects duplicates and skips them (no overwrite)
- ✅ System imports songs immediately (no preview screen)
- ✅ Snackbar shows "X imported, Y skipped" message
- ✅ Imported songs appear in library list immediately (reactive Flow)
- ✅ `originalImportBody` preserves exact file content
- ✅ `syncStatus` set to PENDING_UPLOAD after import
- ✅ `<span>` HTML tags preserved in markdownBody

### Verification:
- ✅ Tested with 10-song Obsidian library import
- ✅ Key detection working for `**Key:** G` format
- ✅ Multi-select working (imported all 10 files at once)
- ✅ Duplicate detection prevents re-import
- ✅ Snackbar feedback accurate
- ✅ Deployed and tested on physical SM-X210 tablet

---

## ✅ Task 7: Setlist Management UI (COMPLETED)

**Date Completed:** 2026-03-26
**Priority:** High - Core feature for Milestone 2

### Objective:
Build the setlist management system allowing users to create setlists, organize songs into sets, and view setlist details with color-coded sets.

### Implementation Details:

#### 1. Setlist Overview Screen (SetlistScreen.kt)
**Features:**
- List of all setlists with name and creation date
- FAB (+) to create new setlist
- Delete setlist with swipe-to-delete
- Tap setlist to view detail screen
- Navigation to setlist detail on click

#### 2. Setlist Detail Screen (SetlistDetailScreen.kt:60-147)
**Features:**
- Display songs organized by sets (Set 1, Set 2, etc.)
- Color-coded set sections using Material 3 tonal palettes
- Per-set (+) button to add songs to specific set
- FAB (+) to add songs to first set
- Song Selection Dialog with search (SetlistDetailScreen.kt:351-422)
- Position-based ordering (1-indexed display, 0-indexed storage)

**Set Coloring (SetColor.kt):**
- 6-color rotation using Material 3 containers:
  - Set 1: primaryContainer
  - Set 2: secondaryContainer
  - Set 3: tertiaryContainer
  - Set 4: errorContainer (softened)
  - Set 5: surfaceVariant
  - Set 6: surfaceContainer
- Automatic color cycling based on `(setNumber - 1) % 6`
- Provides container and content colors for each set

#### 3. Song Selection Dialog (SetlistDetailScreen.kt:351-422)
**Features:**
- Searchable song library
- Live filtering as user types
- Search bar with clear button
- LazyColumn showing all songs
- Displays: title, artist, key badge
- Tap song to immediately add to current set
- Reactive UI with Flow-based song list

#### 4. Library Badging (LibraryScreen.kt:335-348)
**Features:**
- Small "Set 1", "Set 2" chips on song cards
- Shows which set(s) a song belongs to
- Low-profile design with badge colors matching set colors
- FlowRow layout for multi-badge wrapping
- LaunchedEffect fetches sets per song

**Set Membership Query (SetDao.kt:148-158):**
```kotlin
@Query("""
    SELECT DISTINCT sets.* FROM sets
    INNER JOIN set_entries ON sets.id = set_entries.set_id
    WHERE set_entries.song_id = :songId
    ORDER BY sets.number ASC
""")
suspend fun getSetsContainingSong(songId: String): List<SetEntity>
```

#### 5. ViewModel Enhancements (SetlistViewModel.kt)
**New Methods:**
- `addSongToSpecificSet(setId, songId)` - Add to any set (not just Set 1)
- `getSetsContainingSong(songId)` - For library badging
- `getSetlistWithSongs(setlistId)` - Returns nested Flow

**Nested Relations:**
- `SetlistWithSets` → `List<SetWithEntries>` → `List<SetEntryWithSong>`
- Room @Relation with `entity =` parameter for multi-level nesting
- Single query fetches complete setlist hierarchy

#### 6. Navigation Integration (Navigation.kt:100-107)
**Routes:**
- `Routes.SETLISTS` - Setlist overview screen
- `Routes.SETLIST_DETAIL` - Setlist detail with `{setlistId}` argument
- `Routes.LIBRARY` - Library screen with "Add to Setlist" flow

**Shared ViewModel:**
- SetlistViewModel shared across navigation for dialog state
- SongRepository passed to SetlistDetailScreen for search
- AppContainer threaded through component tree

### Files Created:
- ✅ `feature/setlists/SetlistViewModel.kt`
- ✅ `feature/setlists/SetlistScreen.kt`
- ✅ `feature/setlists/SetlistDetailScreen.kt`
- ✅ `core/ui/theme/SetColor.kt`

### Files Modified:
- ✅ `feature/library/LibraryViewModel.kt` - Added SetlistRepository dependency, `getSetsContainingSong()`
- ✅ `feature/library/LibraryScreen.kt` - Added set membership badges with FlowRow
- ✅ `core/data/dao/SetDao.kt` - Added `getSetsContainingSong()` query
- ✅ `core/data/repository/SetlistRepository.kt` - Exposed `getSetsContainingSong()` method
- ✅ `app/navigation/Navigation.kt` - Added setlist routes, passed repositories
- ✅ `app/di/ViewModelFactory.kt` - Inject SetlistRepository into LibraryViewModel
- ✅ `app/MainActivity.kt` - Pass AppContainer to MainScreen
- ✅ `core/data/relations/SetWithEntries.kt` - Updated to use `List<SetEntryWithSong>`
- ✅ `core/data/relations/SetlistWithSets.kt` - Updated to use `List<SetWithEntries>`

### UX Refinements:
1. **In-Set Song Adding:** Each set section has a (+) icon to add songs directly to that set (not just Set 1)
2. **Set Color Coding:** Material 3 tonal palettes provide visual distinction between sets
3. **Library Status Badges:** Songs show "Set 1", "Set 2" chips indicating where they're used
4. **Searchable Add Dialog:** Adding songs uses searchable library instead of list picker
5. **Nested Relations:** Single database query fetches complete setlist hierarchy for performance

### Acceptance Criteria:
- ✅ User can create new setlists
- ✅ User can delete setlists
- ✅ User can add songs to setlists from library
- ✅ User can add songs to specific sets from setlist detail
- ✅ Songs appear in position order within sets
- ✅ Sets are color-coded for visual distinction
- ✅ Library shows which sets songs belong to
- ✅ Song selection dialog is searchable
- ✅ Bottom navigation between Library and Setlists
- ✅ Setlist detail screen shows all songs organized by sets

### Verification:
- ✅ Built and deployed to physical SM-X210 tablet
- ✅ Created test setlist with multiple sets
- ✅ Added songs from library via multiple paths
- ✅ Set colors display correctly
- ✅ Library badges show set membership
- ✅ Search in song selection dialog works
- ✅ Navigation and state management verified

---

## ⏳ Task 8: Song Detail Screen (PENDING)

**Status:** Not started (Next Priority)
**Dependencies:** Tasks 6, 7

### Objective:
Display full song chart with markdown rendering. Shows title, artist, key, and full chord-over-lyric chart using the `MarkdownRenderer` from `core:ui`.

### Features:
- Full-screen markdown rendering
- Top app bar with song title
- Edit button (launches Edit Mode - Task 8)
- Delete button with confirmation dialog
- Scroll support for long charts

### Technical Notes:
- Reuse `MarkdownRenderer` from Milestone 1 (already tested)
- Navigation: `"song/{songId}"` route
- Load song by ID using `SongRepository.getSongById()`

---

## ⏳ Task 9: Edit Mode (PENDING)

**Status:** Not started
**Dependencies:** Task 8 (Song Detail Screen)

### Objective:
Allow users to edit markdown body in a text editor. Support YAML front matter editing (title, artist, key).

### Features:
- Multi-line text editor for markdown body
- Text fields for title, artist, key
- Preview mode toggle (edit ↔ preview)
- Save button (updates song, increments version)
- Cancel button (discard changes)

### Technical Notes:
- Use `TextField` with `maxLines = Int.MAX_VALUE` for markdown editor
- Show preview using `MarkdownRenderer`
- Validate title and artist (required)
- Update `syncStatus` to `PENDING_UPLOAD` on save

---

## ⏳ Task 10: Testing & Polish (PENDING)

**Status:** Not started
**Dependencies:** Tasks 6-9

### Objective:
End-to-end testing, edge case handling, performance optimization, and UI polish.

### Checklist:
- [ ] Import flow tested with various markdown files
- [ ] Duplicate detection works correctly
- [ ] Search performance with 100+ songs
- [ ] Edit mode handles long documents (5000+ lines)
- [ ] Delete confirmation prevents accidental deletion
- [ ] Navigation back button behavior correct
- [ ] UI responsive on 11-inch tablet (Portrait)
- [ ] Empty states for all screens
- [ ] Loading states for async operations
- [ ] Error messages user-friendly

---

## Milestone 2 Success Criteria

**Core Features:**
- ✅ Room database operational with 4 entities
- ✅ Library Screen displays all songs with search and set badges
- ✅ Import flow using Storage Access Framework with multi-select
- ✅ Setlist Management UI with color-coded sets
- ⏳ Song Detail Screen with markdown rendering
- ⏳ Edit Mode with markdown editing

**Non-Functional:**
- ✅ Offline-first architecture (all data from Room)
- ✅ Reactive UI with Flow-based updates
- ✅ Obsidian-compatible markdown format
- ✅ Performance: Multi-file import, nested relations
- ⏳ No data loss during edit operations
- ⏳ Clear error messages for all failures

**Deliverable:**
Working Android app deployed to physical tablet. Users can import multiple markdown charts from Obsidian, view library with set membership badges, create color-coded setlists, search songs, and organize performance sets. All data persists locally in Room database with reactive UI updates.

---

## Next Steps

**Immediate Priority:**
1. Task 8: Song Detail Screen (view full chord chart)
2. Task 9: Edit Mode (markdown editing)
3. Task 10: Testing & Polish
4. Final Milestone 2 demo video

**After Milestone 2:**
1. Milestone 3: Transposition Engine
2. Milestone 4: Google Sign-In & Authentication
3. Milestone 5: Sync Service with conflict resolution

---

**Last Updated:** 2026-03-26
**Status:** 7/10 tasks complete. Song Detail Screen is next.

**Recent Achievements:**
- ✅ Obsidian-compatible import with `**Key:**` parsing
- ✅ Multi-select SAF file picker
- ✅ Color-coded setlists with Material 3 tonal palettes
- ✅ Library badging showing set membership
- ✅ In-set song adding with searchable dialog
- ✅ Nested Room relations for complete setlist hierarchy
