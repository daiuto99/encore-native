# Milestone 2: Library Management

**Goal:** Build a functional song library with import, search, and basic CRUD operations. Users can import markdown charts, view their library, and search for songs.

**Status:** In Progress (Tasks 1-5 Complete)
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
| 6 | Import Flow | 🔄 NEXT | `feature/library/import/` |
| 7 | Song Detail Screen | ⏳ PENDING | `feature/library/detail/` |
| 8 | Edit Mode | ⏳ PENDING | `feature/edit/` |
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

## 🔄 Task 6: Import Flow (NEXT)

**Status:** Ready to implement
**Priority:** High - Core feature for Milestone 2

### Objective:
Build the markdown import flow using Android Storage Access Framework (SAF). Users can select `.md` files from their device, parse YAML front matter, detect duplicates, and import songs into the library.

### Technical Approach:

#### 1. File Picker Integration
**Tool:** Android Storage Access Framework (SAF)
- Use `ACTION_OPEN_DOCUMENT` intent for file selection
- MIME type filter: `text/markdown`, `text/plain`, `*/*` (with manual .md check)
- Request persistent URI permissions for future access
- Handle multi-file selection (optional for V1)

**Reference:**
```kotlin
val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "*/*"  // Or "text/markdown" if supported
    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/markdown", "text/plain"))
}
startActivityForResult(intent, REQUEST_CODE_IMPORT)
```

#### 2. Markdown Parsing
**Leverage Existing:** `core:ui` module already has `SongParser.parse(markdown: String)`
- Parses YAML front matter: title, artist, key
- Extracts markdown body content
- Returns `ParsedSong` data class

**Import Validation:**
- Title and Artist are required (error if missing)
- Key is optional
- Markdown body must not be empty

#### 3. Duplicate Detection
**Strategy:** Check unique constraint (userId, title, artist)
- Use `SongRepository.findDuplicate(title, artist, userId)`
- If duplicate found: Show dialog with options:
  - "Keep Existing" - Cancel import
  - "Replace" - Update existing song (preserves ID, increments version)
  - "Import as Copy" - Add suffix like " (2)" to title

#### 4. Import Confirmation Screen
**UI Flow:**
```
File Picker → Parse → Duplicate Check → Preview → Confirm → Library
```

**Preview Screen (before final import):**
- Show parsed title, artist, key
- Preview first 5 lines of markdown body
- "Cancel" and "Import" buttons
- If duplicate detected, show warning banner

#### 5. Repository Integration
**Import Logic:**
```kotlin
suspend fun importSong(
    markdown: String,
    originalFilename: String?,
    userId: String = "local-user"
): Result<String> {
    // 1. Parse markdown
    val parsed = SongParser.parse(markdown)

    // 2. Check for duplicate
    val existing = findDuplicate(parsed.title, parsed.artist, userId)

    // 3. Return duplicate info or proceed with upsert
    if (existing != null) {
        return Result.failure(DuplicateSongException(existing))
    }

    // 4. Create new song entity
    val song = SongEntity(
        id = UUID.randomUUID().toString(),
        userId = userId,
        title = parsed.title,
        artist = parsed.artist,
        currentKey = parsed.key,
        markdownBody = parsed.markdownBody,
        originalImportBody = markdown,  // Preserve original
        version = 1,
        createdAt = now,
        updatedAt = now,
        syncStatus = SyncStatus.PENDING_UPLOAD,
        localUpdatedAt = now,
        lastSyncedAt = null
    )

    // 5. Insert
    return upsertSong(song)
}
```

#### 6. Error Handling
**Common Errors:**
- File not found or inaccessible
- Invalid markdown format (no YAML front matter)
- Missing required fields (title, artist)
- Permission denied (SAF)
- Database insert failure

**Error UI:**
- Toast for quick errors (permission denied)
- Dialog for actionable errors (duplicate detected)
- Snackbar for success messages

### Files to Create:
```
feature/library/src/main/kotlin/com/encore/feature/library/import/
├── ImportViewModel.kt           // Handle file picker result, parse, duplicate check
├── ImportPreviewScreen.kt       // Show preview before final import
├── DuplicateResolutionDialog.kt // "Keep Existing", "Replace", "Import as Copy"
└── ImportUtils.kt               // File reading, MIME type checks
```

### Files to Modify:
- `feature/library/LibraryScreen.kt` - Wire FAB onClick to launch import flow
- `feature/library/LibraryViewModel.kt` - Add import state management (optional)
- `app/navigation/Navigation.kt` - Add import preview route

### Acceptance Criteria:
- [ ] User can tap FAB to open file picker
- [ ] User can select .md file from device storage
- [ ] System parses YAML front matter and extracts title, artist, key
- [ ] System detects duplicates and shows resolution dialog
- [ ] User sees preview screen before final import
- [ ] System imports song and shows success message
- [ ] Imported song appears in library list immediately (reactive Flow)
- [ ] originalImportBody preserves exact file content
- [ ] syncStatus set to PENDING_UPLOAD after import

### Testing Notes:
- Test with valid markdown files (YAML + chords)
- Test with missing YAML front matter (should error)
- Test with missing title or artist (should error)
- Test duplicate detection with exact match
- Test with multiple imports in sequence
- Test with very large markdown files (performance)

---

## ⏳ Task 7: Song Detail Screen (PENDING)

**Status:** Not started
**Dependencies:** Task 6 (Import Flow)

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

## ⏳ Task 8: Edit Mode (PENDING)

**Status:** Not started
**Dependencies:** Task 7 (Song Detail Screen)

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

## ⏳ Task 9: Testing & Polish (PENDING)

**Status:** Not started
**Dependencies:** Tasks 6-8

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
- ✅ Library Screen displays all songs with search
- ⏳ Import flow using Storage Access Framework
- ⏳ Song Detail Screen with markdown rendering
- ⏳ Edit Mode with markdown editing

**Non-Functional:**
- ✅ Offline-first architecture (all data from Room)
- ✅ Reactive UI with Flow-based updates
- ⏳ Performance: Search < 100ms for 100 songs
- ⏳ No data loss during edit operations
- ⏳ Clear error messages for all failures

**Deliverable:**
Working Android app deployed to physical tablet. Users can import markdown charts, view library, search songs, and edit charts. All data persists locally in Room database.

---

## Next Steps

**Immediate Priority:**
1. Implement Task 6: Import Flow (SAF + duplicate detection)
2. Test import flow with various markdown files
3. Commit "Clean Slate" with code + updated docs

**After Task 6:**
1. Task 7: Song Detail Screen
2. Task 8: Edit Mode
3. Task 9: Testing & Polish
4. Final Milestone 2 demo video

---

**Last Updated:** 2026-03-26
**Status:** 5/9 tasks complete. Import Flow is next.
