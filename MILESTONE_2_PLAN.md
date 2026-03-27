# Milestone 2: Core Library + Setlist Management - Plan

**Branch:** `milestone-2/library-management`
**Status:** Planning
**Created:** 2026-03-26

---

## Milestone 2 Overview

**Goal:** Deliver useful offstage workflow on tablet.

**Key Deliverables:**
- Single-song and full-library import with duplicate detection
- Library search and browse screens
- Setlist creation, set management, and editing
- Simple markdown edit mode for songs

**Exit Gate:** User can import songs, build a full setlist, and update a master song safely.

---

## Dependencies (All Met ✅)

From `docs/05_Build_Roadmap.md`:

- ✅ Phase 1 schema and parser approved
  - Data model documented in `docs/architecture/data-model.md`
  - Parser validated with mikepenz library

- ✅ Stable local persistence and repository structure in place
  - Room database approach decided
  - Multi-module architecture created

---

## First 3 Technical Steps (Recommended)

### Step 1: Implement Room Database Schema

**Priority:** CRITICAL - Foundation for all features
**Module:** `core:data`
**Estimated Complexity:** Medium

#### What to Build

1. **Room Entities:**
   - `SongEntity` - Complete Song data class with Room annotations
   - `SetlistEntity` - Setlist metadata
   - `SetEntity` - Individual sets within setlists
   - `SetEntryEntity` - Song placements in sets

2. **Room Database:**
   - `EncoreDatabase` - Main database class with version management
   - Type converters for timestamps, UUIDs, enums
   - Migration strategy (empty for v1)

3. **Database Indexes:**
   - Song search indexes (title, artist, combined)
   - SetEntry position indexes
   - Sync status indexes

#### Why This First?

- **Blocks Everything:** Import, library, setlists all require database
- **Low Risk:** Well-defined schema from data model doc
- **Testable:** Can write unit tests immediately
- **No UI Dependency:** Pure data layer work

#### Key Files to Create

```
core/data/src/main/kotlin/com/encore/core/data/
├── db/
│   ├── EncoreDatabase.kt
│   ├── TypeConverters.kt
│   └── migrations/
├── entities/
│   ├── SongEntity.kt
│   ├── SetlistEntity.kt
│   ├── SetEntity.kt
│   └── SetEntryEntity.kt
└── enums/
    └── SyncStatus.kt
```

#### Implementation Notes

From `docs/architecture/data-model.md`:

**Song Entity Fields:**
- `id: String` (UUID as String for Room)
- `userId: String` (hardcode "local-user" for V1, multi-user in Milestone 4)
- `title: String` (indexed)
- `artist: String` (indexed)
- `currentKey: String?`
- `markdownBody: String` (full chart)
- `originalImportBody: String?`
- `version: Int = 1`
- `createdAt: Long` (timestamp)
- `updatedAt: Long` (timestamp)
- `syncStatus: SyncStatus = SYNCED`
- `localUpdatedAt: Long`
- `lastSyncedAt: Long?`

**Unique Index:** `(userId, title, artist)` for duplicate detection

#### Acceptance Criteria

- [ ] Room database compiles without errors
- [ ] All entities have proper annotations (@Entity, @PrimaryKey, @Index)
- [ ] Type converters handle UUID, Timestamp, Enum conversions
- [ ] Database version set to 1
- [ ] Unit tests verify entity creation and indexes
- [ ] `./gradlew test` passes

---

### Step 2: Create Song Repository and DAO

**Priority:** HIGH - Enables import and library features
**Module:** `core:data`
**Estimated Complexity:** Medium

#### What to Build

1. **DAO Interfaces:**
   - `SongDao` - CRUD operations for songs
   - Query methods: getAll, searchByTitle, searchByArtist, findDuplicate
   - Insert/update/delete operations with conflict handling

2. **Repository Pattern:**
   - `SongRepository` - Abstraction over SongDao
   - Exposes Flow<List<Song>> for reactive UI updates
   - Handles duplicate detection logic
   - Maps SongEntity ↔ Song domain model

3. **Domain Models:**
   - `Song` data class (clean, UI-friendly)
   - Separate from SongEntity (separation of concerns)

#### Why This Second?

- **Builds on Step 1:** Requires database schema
- **Enables Import:** Need repository to save imported songs
- **Repository Pattern:** Clean architecture, testable
- **Reactive:** Flow-based updates for Compose UI

#### Key Files to Create

```
core/data/src/main/kotlin/com/encore/core/data/
├── dao/
│   └── SongDao.kt
├── repository/
│   ├── SongRepository.kt
│   └── SongRepositoryImpl.kt
└── models/
    └── Song.kt  (domain model, not entity)
```

#### Implementation Notes

**SongDao Methods:**
```kotlin
@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE userId = :userId AND title = :title AND artist = :artist LIMIT 1")
    suspend fun findDuplicate(userId: String, title: String, artist: String): SongEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(song: SongEntity): Long

    @Update
    suspend fun update(song: SongEntity)

    @Delete
    suspend fun delete(song: SongEntity)

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SongEntity?
}
```

**Repository Methods:**
```kotlin
interface SongRepository {
    fun getAllSongs(): Flow<List<Song>>
    fun searchSongs(query: String): Flow<List<Song>>
    suspend fun getSongById(id: String): Song?
    suspend fun insertSong(song: Song): Result<String>  // Returns ID or error
    suspend fun updateSong(song: Song): Result<Unit>
    suspend fun deleteSong(id: String): Result<Unit>
    suspend fun checkDuplicate(title: String, artist: String): Song?  // For import flow
}
```

#### Acceptance Criteria

- [ ] SongDao compiles with all CRUD methods
- [ ] SongRepository implements clean interface
- [ ] Duplicate detection query works correctly
- [ ] Flow-based queries emit updates reactively
- [ ] Unit tests for repository methods (mock DAO)
- [ ] Integration test: insert song, query it back
- [ ] `./gradlew test` passes

---

### Step 3: Build Import Flow Foundation

**Priority:** HIGH - Core user workflow
**Module:** `feature:library`
**Estimated Complexity:** High

#### What to Build

1. **File Picker Integration:**
   - Android Storage Access Framework (SAF) integration
   - Single-file picker for individual song import
   - Multi-file picker for full-library import
   - Markdown file filter (.md extension)

2. **Import Service:**
   - Parse selected markdown files using existing SongParser
   - Extract metadata (title, artist, key)
   - Check for duplicates via SongRepository
   - Generate UUIDs for new songs
   - Handle import errors gracefully

3. **Import UI States:**
   - Idle (showing import button)
   - Picking files
   - Processing imports
   - Duplicate detected (needs user decision)
   - Success/Error feedback

4. **Duplicate Detection Modal (Basic):**
   - Show existing song vs new song
   - Three buttons: Replace, Keep Both, Cancel
   - "Keep Both" appends (1), (2), etc. to title

#### Why This Third?

- **Uses Step 1 & 2:** Requires database and repository
- **High User Value:** First tangible feature
- **Tests Data Layer:** Validates database and repository work
- **Foundation for Library:** Import before browse makes sense

#### Key Files to Create

```
feature/library/src/main/kotlin/com/encore/feature/library/
├── import/
│   ├── ImportViewModel.kt
│   ├── ImportScreen.kt  (or integrate into LibraryScreen)
│   ├── ImportService.kt
│   └── DuplicateDetectionModal.kt
└── models/
    ├── ImportState.kt
    └── DuplicateAction.kt  (enum: REPLACE, KEEP_BOTH, CANCEL)
```

#### Implementation Notes

**File Picker (SAF):**
```kotlin
val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
) { uri ->
    uri?.let { viewModel.importSong(it) }
}

// Trigger:
launcher.launch(arrayOf("text/markdown", "text/plain"))
```

**Import Flow Logic:**
```kotlin
suspend fun importSong(uri: Uri): ImportResult {
    // 1. Read file content
    val markdown = readMarkdownFromUri(uri)

    // 2. Parse metadata
    val parsed = SongParser.parse(markdown)

    // 3. Check duplicate
    val duplicate = songRepository.checkDuplicate(parsed.title, parsed.artist)

    // 4. If duplicate, return DuplicateDetected state
    if (duplicate != null) {
        return ImportResult.DuplicateDetected(existing = duplicate, new = parsed)
    }

    // 5. Create Song entity
    val song = Song(
        id = UUID.randomUUID().toString(),
        userId = "local-user",  // Hardcoded for Milestone 2
        title = parsed.title,
        artist = parsed.artist,
        currentKey = parsed.key,
        markdownBody = parsed.markdownBody,
        originalImportBody = parsed.fullMarkdown,
        version = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    // 6. Insert into database
    songRepository.insertSong(song)

    return ImportResult.Success(song.id)
}
```

**Duplicate Modal UI:**
- Side-by-side comparison: Existing vs New
- Highlight differences (title, artist, key, content preview)
- Clear action buttons

#### Acceptance Criteria

- [ ] File picker opens and filters .md files
- [ ] Single song import parses and saves to database
- [ ] Duplicate detection triggers modal correctly
- [ ] Replace action updates existing song
- [ ] Keep Both action creates new song with (1) suffix
- [ ] Cancel action aborts import
- [ ] Error handling for malformed markdown files
- [ ] Success feedback shown to user
- [ ] `./gradlew assembleDebug` builds successfully
- [ ] Manual testing: import Amazing Grace sample song

---

## Remaining Milestone 2 Work (After Steps 1-3)

### Step 4: Build Library Screen
- Display all songs in scrollable list
- Search bar with real-time filtering
- Tap song → Song Viewer screen
- Import button triggers import flow

### Step 5: Implement Setlist Data Layer
- SetlistDao, SetDao, SetEntryDao
- SetlistRepository with CRUD operations
- Handle set renumbering on delete

### Step 6: Build Setlist Overview Screen
- List all setlists
- Create new setlist
- Tap setlist → Setlist Detail screen

### Step 7: Build Setlist Detail Screen
- Show all sets as tabs
- Display songs in each set
- Add/remove sets
- Navigate to Set Editor

### Step 8: Build Set Editor Screen
- Add songs from library to set
- Reorder songs via drag-and-drop
- Remove songs from set

### Step 9: Build Markdown Edit Mode
- Edit song.markdownBody in text editor
- Save updates song, increments version
- Preview toggle (optional)

### Step 10: Testing and Polish
- Unit tests for all repositories
- Integration tests for import flow
- Manual testing checklist
- Bug fixes and refinements

---

## Technical Risks and Mitigations

### Risk 1: Room Database Performance with Large Libraries
**Impact:** Slow queries with 500+ songs
**Mitigation:**
- Implement proper indexes from the start
- Use Flow for reactive queries (only fetch what's visible)
- Profile queries early with large test dataset
- Consider pagination if needed

### Risk 2: File Picker Permissions on Different Android Versions
**Impact:** Import fails on some devices
**Mitigation:**
- Use Storage Access Framework (SAF) - works on Android 10+
- Test on multiple Android versions (emulator)
- Provide clear error messages if permissions fail

### Risk 3: Duplicate Detection Edge Cases
**Impact:** False positives/negatives in duplicate matching
**Mitigation:**
- Use exact string matching (case-insensitive)
- Trim whitespace before comparison
- Document edge cases (e.g., "Amazing Grace" vs "Amazing Grace ")
- Add manual override option in future

### Risk 4: Set Renumbering Logic Complexity
**Impact:** Bugs when deleting sets mid-setlist
**Mitigation:**
- Write comprehensive unit tests for renumbering
- Use database transactions for atomic updates
- Test manually with edge cases (delete Set 1, delete last set, etc.)

---

## Success Criteria for Milestone 2

From `docs/05_Build_Roadmap.md`:

**Exit Gate:** Tablet-only management workflow is functional before performance mode work starts.

**Specific Criteria:**
1. ✅ User can import a single song successfully
2. ✅ User can import a full library (10+ songs)
3. ✅ Duplicate detection shows modal with Replace/Keep Both/Cancel
4. ✅ Library screen displays all songs with search
5. ✅ User can create a setlist with multiple sets
6. ✅ User can add/remove songs to/from sets
7. ✅ User can edit a song's markdown content
8. ✅ Changes persist across app restarts (Room working)
9. ✅ `./gradlew assembleDebug` builds successfully
10. ✅ Manual testing passes import, library, setlist workflows

---

## Definition of Done (Milestone 2)

Before marking Milestone 2 complete:

- [ ] All deliverables in `docs/06_Delivery_Checklist.md` complete
- [ ] Runnable APK generated and tested
- [ ] Source committed to `milestone-2/library-management` branch
- [ ] Import flow (single + full library) working
- [ ] Duplicate modal functional (Replace/Keep Both/Cancel)
- [ ] Library screen with search operational
- [ ] Setlist CRUD and set editing working
- [ ] Markdown edit mode functional
- [ ] Test notes document import behaviors
- [ ] No critical bugs blocking core workflows
- [ ] Ready to merge into main and tag v0.2

---

## Timeline Considerations

**Note:** As per project guidelines, no time estimates provided. Focus on completing features sequentially and thoroughly.

**Recommended Order:**
1. Database schema (enables everything)
2. Song repository (enables import and library)
3. Import flow (first user-facing feature)
4. Library screen (display imported songs)
5. Setlist data layer (enables setlist features)
6. Setlist screens (overview, detail, set editor)
7. Markdown edit mode (song editing)
8. Testing and polish

---

## Next Actions

1. **Approve this plan** or request modifications
2. **Begin Step 1:** Implement Room Database Schema
3. **Create** `core/data/src/main/kotlin/com/encore/core/data/db/EncoreDatabase.kt`
4. **Define** all Room entities based on data model
5. **Write** unit tests for database schema
6. **Verify** `./gradlew test` passes before proceeding to Step 2

---

**Status:** Awaiting approval to begin Step 1
**Branch:** `milestone-2/library-management`
**Created:** 2026-03-26
