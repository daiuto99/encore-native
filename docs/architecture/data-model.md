# Encore Data Model

**Milestone:** 2 - Library Management (Rooms Entities Implemented)
**Status:** Active - Room database operational
**Last Updated:** 2026-03-26

## Entity Relationship Overview

```
User (1) ──────┬─────────── (*) Song
               │
               ├─────────── (*) Setlist
               │
               └─────────── (*) Device

Setlist (1) ────────────── (*) Set

Set (1) ────────────────── (*) SetEntry (position-ordered, 0-indexed)

SetEntry (*) ───────────── (1) Song (many-to-many via junction table)

User (1) ───────────────── (*) ConflictRecord
```

**Key Relationships:**

1. **Song ↔ SetEntry ↔ Set:** Many-to-many relationship. A song can appear in multiple sets (even multiple times in the same set). SetEntry is the junction table with position ordering.

2. **Set Numbering:** Sets are numbered sequentially (1, 2, 3...). Deleting Set 2 automatically renumbers Set 3 → Set 2, Set 4 → Set 3, etc.

3. **Entry Position:** SetEntry uses 0-indexed position within each set. When a song is removed, positions are compacted (no gaps).

4. **Set Coloring:** Each Set uses a `colorToken` and Material 3 tonal palettes for visual distinction in the UI. Sets cycle through 6 colors based on their number.

5. **Cascade Deletes:** Deleting a Setlist cascades to Sets and SetEntries. Deleting a Song cascades to all SetEntries referencing it.

## Entity Definitions

### User
The authenticated musician account. One library per user in V1.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PRIMARY KEY | Server-generated |
| google_sub | String | UNIQUE, NOT NULL | Google OAuth subject ID |
| email | String | NOT NULL | From Google profile |
| display_name | String | NOT NULL | From Google profile |
| active_device_id | UUID | FOREIGN KEY -> Device.id | Only one active device in V1 |
| created_at | Timestamp | NOT NULL | Account creation time |
| updated_at | Timestamp | NOT NULL | Last profile update |

### Device
Represents an Android tablet that has been authenticated. Enforces single-device session policy.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PRIMARY KEY | Generated on first auth |
| user_id | UUID | FOREIGN KEY -> User.id | Owner |
| platform | String | NOT NULL | "android" in V1 |
| app_version | String | NOT NULL | e.g., "1.0.0-milestone1" |
| last_sync_at | Timestamp | NULLABLE | Last successful sync time |
| active | Boolean | NOT NULL | Only one device can be active |
| created_at | Timestamp | NOT NULL | First activation |

### Song
A single master version of a markdown chart. Changes apply globally.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PRIMARY KEY | Server-generated |
| user_id | UUID | FOREIGN KEY -> User.id | Owner |
| title | String | NOT NULL | Searchable, indexed |
| artist | String | NOT NULL | Searchable, indexed |
| current_key | String | NULLABLE | e.g., "G", "Dm", "C#m" |
| markdown_body | Text | NOT NULL | Full editable chart content |
| original_import_body | Text | NULLABLE | Preserve initial import for reference |
| lead_marker | String | NULLABLE | Custom markers for lead vocals |
| harmony_markup | String | NULLABLE | Future: harmony annotations |
| version | Integer | NOT NULL, DEFAULT 1 | Increments on edit, used for conflict detection |
| created_at | Timestamp | NOT NULL | Import time |
| updated_at | Timestamp | NOT NULL | Last edit time |

**Unique Constraint:** (user_id, title, artist) - detects duplicates during import

### Setlist
A named collection of sets for a performance or rehearsal.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PRIMARY KEY | Server-generated |
| user_id | UUID | FOREIGN KEY -> User.id | Owner |
| name | String | NOT NULL | Only required field |
| version | Integer | NOT NULL, DEFAULT 1 | For sync conflict detection |
| created_at | Timestamp | NOT NULL | Creation time |
| updated_at | Timestamp | NOT NULL | Last modification |

### Set
A numbered segment within a setlist (e.g., Set 1, Set 2). Sets use color tabs for visual distinction.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PRIMARY KEY | Server-generated |
| setlist_id | UUID | FOREIGN KEY -> Setlist.id | Parent setlist |
| number | Integer | NOT NULL | 1, 2, 3, etc. Renumbers on delete |
| color_token | String | NULLABLE | e.g., "blue", "green", "red" |
| created_at | Timestamp | NOT NULL | Creation time |

**Unique Constraint:** (setlist_id, number)

### SetEntry
A song placed in a specific position within a set. A song can appear multiple times across a setlist.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PRIMARY KEY | Server-generated |
| set_id | UUID | FOREIGN KEY -> Set.id | Parent set |
| song_id | UUID | FOREIGN KEY -> Song.id | Referenced song |
| position | Integer | NOT NULL | 0-indexed position in set |
| created_at | Timestamp | NOT NULL | Addition time |

**Unique Constraint:** (set_id, position)

### ConflictRecord
Tracks sync conflicts that require user resolution. Created when local and remote versions differ.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PRIMARY KEY | Server-generated |
| user_id | UUID | FOREIGN KEY -> User.id | Owner |
| entity_type | Enum | NOT NULL | "song", "setlist", "set", "set_entry" |
| entity_id | UUID | NOT NULL | ID of conflicting entity |
| local_version | Integer | NOT NULL | Version number from device |
| remote_version | Integer | NOT NULL | Version number from server |
| local_snapshot | JSONB | NULLABLE | Full local state for comparison |
| remote_snapshot | JSONB | NULLABLE | Full remote state for comparison |
| status | Enum | NOT NULL | "pending", "resolved_local", "resolved_remote" |
| resolved_at | Timestamp | NULLABLE | When user chose a version |
| created_at | Timestamp | NOT NULL | Conflict detection time |

## Room Database Schema (Android Client) - IMPLEMENTED ✓

**Status:** Milestone 2 - Fully operational
**Location:** `android/core/data/src/main/kotlin/com/encore/core/data/`

### Implemented Entities

#### SongEntity
**File:** `entities/SongEntity.kt`

```kotlin
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["user_id", "title"]),
        Index(value = ["user_id", "artist"]),
        Index(value = ["user_id", "title", "artist"], unique = true),
        Index(value = ["sync_status"])
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,                    // UUID string
    val userId: String,                            // "local-user" in Milestone 2
    val title: String,
    val artist: String,
    val currentKey: String?,                       // e.g., "G", "Dm", "C#m"
    val markdownBody: String,                      // Full chart content
    val originalImportBody: String?,               // Preserve initial import
    val version: Int = 1,                          // Conflict detection
    val createdAt: Long,                           // Unix timestamp millis
    val updatedAt: Long,                           // Unix timestamp millis
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val localUpdatedAt: Long,                      // Last local edit
    val lastSyncedAt: Long? = null                 // Last sync time
)
```

#### SetlistEntity
**File:** `entities/SetlistEntity.kt`

```kotlin
@Entity(
    tableName = "setlists",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["sync_status"])
    ]
)
data class SetlistEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val version: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val localUpdatedAt: Long,
    val lastSyncedAt: Long? = null
)
```

#### SetEntity
**File:** `entities/SetEntity.kt`

```kotlin
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = SetlistEntity::class,
            parentColumns = ["id"],
            childColumns = ["setlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["setlist_id", "number"], unique = true),
        Index(value = ["setlist_id"])
    ]
)
data class SetEntity(
    @PrimaryKey val id: String,
    val setlistId: String,
    val number: Int,                               // 1, 2, 3, etc.
    val colorToken: String?,                       // UI color hint
    val createdAt: Long
)
```

#### SetEntryEntity
**File:** `entities/SetEntryEntity.kt`

```kotlin
@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = SetEntity::class,
            parentColumns = ["id"],
            childColumns = ["set_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["set_id", "position"], unique = true),
        Index(value = ["song_id"]),
        Index(value = ["set_id"])
    ]
)
data class SetEntryEntity(
    @PrimaryKey val id: String,
    val setId: String,
    val songId: String,
    val position: Int,                             // 0-indexed
    val createdAt: Long
)
```

#### SyncStatus Enum
**File:** `entities/SyncStatus.kt`

```kotlin
enum class SyncStatus {
    SYNCED,           // In sync with server
    PENDING_UPLOAD,   // Local changes need upload
    PENDING_DELETE    // Marked for deletion
}
```

### Type Converters

**File:** `db/TypeConverters.kt`

```kotlin
class EncoreTypeConverters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return try {
            SyncStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            SyncStatus.SYNCED  // Safe default
        }
    }
}
```

**Note:** Room handles Long, Int, and String natively. Only custom enum types require converters.

### Database Class

**File:** `db/EncoreDatabase.kt`

```kotlin
@Database(
    entities = [
        SongEntity::class,
        SetlistEntity::class,
        SetEntity::class,
        SetEntryEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(EncoreTypeConverters::class)
abstract class EncoreDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun setlistDao(): SetlistDao
    abstract fun setDao(): SetDao
    abstract fun setEntryDao(): SetEntryDao

    // Pre-populated with "Amazing Grace" demo song
}
```

### Repositories

**Files:** `repository/SongRepository.kt`, `repository/SetlistRepository.kt`

- **SongRepository:** Search, upsert, duplicate detection
- **SetlistRepository:** Complex relationship management, set renumbering, position compacting

### Client-Side Fields (All Entities)

All syncable entities include:
- `syncStatus`: SyncStatus enum ("SYNCED", "PENDING_UPLOAD", "PENDING_DELETE")
- `localUpdatedAt`: Long timestamp of last local modification
- `lastSyncedAt`: Long? timestamp of last successful sync (nullable)

## Data Model Rules

1. **Song is Master:** A song has one version. Editing a song updates it everywhere.
2. **Duplicate Detection:** Title + Artist defines uniqueness per user.
3. **Set Renumbering:** Deleting Set 2 makes old Set 3 become new Set 2.
4. **Versioning:** All sync-able entities use integer version numbers that increment on change.
5. **Conflict Resolution:** Never auto-merge. Always ask the user.
6. **Offline Writes:** All writes succeed immediately to local DB, queue for sync.

## Migration Strategy

For future schema changes:
- Use Room migration paths for Android client
- Use Flyway or Liquibase for PostgreSQL migrations
- Always version API contracts

## Encore-Markdown Specification

**Version:** 1.0 (Milestone 2)
**Format:** Obsidian-compatible chord sheets

Encore uses a specific markdown format optimized for chord charts with inline chords, section markers, and metadata. This format is compatible with Obsidian's chord sheet conventions.

### Metadata Format

**All metadata uses bold markdown:**

```markdown
**Title:** Song Title
**Artist:** Artist Name
**Key:** G
**Tempo:** 120
**Time:** 4/4
```

**Parsing Rules:**
- Metadata fields are case-insensitive
- Format: `**Field:**` followed by value
- Required fields: Title, Artist
- Optional fields: Key, Tempo, Time, Capo, Notes
- Key format: `[A-G][#b]?[m]?` (e.g., "G", "Dm", "C#", "Bb")

**Regex Pattern:**
```kotlin
"""(?i)\*\*?Key:\*\*?\s*([A-G][#b]?m?)""".toRegex()
```

### Section Markers

**Sections use HTML spans with color coding:**

```html
<span style="color:blue">Intro</span>
<span style="color:blue">Verse 1</span>
<span style="color:blue">Chorus</span>
<span style="color:red">Bridge</span>
<span style="color:blue">Outro</span>
```

**Color Convention:**
- **Blue:** Intro, Verse, Chorus, Outro (standard sections)
- **Red:** Bridge, Interlude, Solo (special sections)

### Chord Notation

**Inline chords use brackets:**

```
Amazing [G]grace, how [C]sweet the [G]sound
That [G]saved a [D]wretch like [Em]me [D]
```

**Chord Format:**
- Brackets: `[ChordName]`
- Chord syntax: `[A-G][#b]?[m]?(sus|maj|min|dim|aug)?[0-9]?`
- Examples: `[G]`, `[Dm]`, `[C#m]`, `[Gsus4]`, `[Cmaj7]`, `[Bb]`

### Full Example

```markdown
**Title:** Amazing Grace
**Artist:** John Newton
**Key:** G
**Tempo:** 90
**Time:** 3/4

<span style="color:blue">Verse 1</span>
Amazing [G]grace, how [C]sweet the [G]sound
That [G]saved a [D]wretch like [Em]me [D]
I [G]once was [G7]lost, but [C]now am [G]found
Was [G]blind, but [D]now I [G]see

<span style="color:blue">Verse 2</span>
'Twas [G]grace that [C]taught my [G]heart to fear
And [G]grace my [D]fears re[Em]lieved [D]
How [G]precious [G7]did that [C]grace ap[G]pear
The [G]hour I [D]first be[G]lieved

<span style="color:red">Bridge</span>
Through [C]many dangers, [G]toils, and [D]snares
I [G]have already [D]come
```

### Import Behavior

**Encore's import flow (LibraryViewModel.kt:94-162):**

1. **File Selection:** Multi-select file picker using Storage Access Framework (SAF)
2. **Filename Parsing:** `Title - Artist.md` → extracts title and artist
   - Fallback: filename becomes title, artist = "Unknown Artist"
3. **Content Reading:** Read markdown file content via ContentResolver
4. **Key Extraction:** Parse `**Key:**` from content using regex patterns
   - Strips `**` markers, stores only the key value (e.g., "G", "Dm")
5. **Duplicate Detection:** Check for existing (title, artist, userId)
   - **Skip on Duplicate:** If exists, increment `skippedCount`, do not overwrite
   - **Rationale:** Prevents accidental data loss. Users must manually delete to re-import.
6. **Entity Creation:** Create SongEntity with:
   - `currentKey`: Extracted key value (no markdown formatting)
   - `markdownBody`: Full content with `<span>` HTML tags preserved
   - `originalImportBody`: Copy of import for reference
   - `syncStatus`: PENDING_UPLOAD
7. **Repository Upsert:** Insert new song, report success/failure

**Import Result:**
- Snackbar shows: "X songs imported, Y duplicates skipped"
- Duplicate songs retain their current version (no silent overwrites)

**Why Skip on Duplicate:**
- **Data Safety:** Prevents accidental overwrite of edited songs
- **User Control:** Forces intentional delete → re-import workflow
- **Sync Compatibility:** Avoids creating conflicting versions
- **Predictable Behavior:** Import operation is idempotent

### Rendering

**Markdown Renderer (`core:ui`):**
- Uses `MarkdownRenderer` composable from Milestone 1
- Renders inline chords above lyrics (chord-over-lyric alignment)
- Monospace font for chord positioning
- HTML span colors rendered for section headers
- Preserves whitespace for chord alignment

**Display in Library:**
- Title and Artist shown in list
- `currentKey` displayed as badge (e.g., "G", "Dm")
- Search by title or artist (partial match)

### Compatibility

**Obsidian Integration:**
- Format is 100% compatible with Obsidian chord sheet plugins
- Can edit charts in Obsidian, sync via filesystem
- HTML spans render in Obsidian's live preview
- Bold metadata is standard markdown

**Future Extensions:**
- YAML front matter support (alternative to bold metadata)
- Custom section colors via user preferences
- Transposition with automatic key detection
- Chord diagrams for guitar/piano

---

## Future Considerations (Post-V1)

- `capo_mode`: Boolean for capo transposition
- `target_key`: String for future transposition target
- `source_key`: String for original key before transposition
- `chord_tokens`: JSONB array for advanced search
- `tags`: Array of user-defined tags
- `setlist_templates`: Reusable setlist patterns

---

## Implementation Status

**Milestone 2 (100% COMPLETE):**
- ✓ Room entities: Song, Setlist, Set, SetEntry
- ✓ SyncStatus enum with TypeConverters
- ✓ DAOs with @Transaction queries (including nested relations)
- ✓ Repositories with business logic
- ✓ Database pre-populated with "Amazing Grace" demo song
- ✓ Library Screen UI with search and set membership badges
- ✓ Markdown Import Flow with SAF multi-select and Obsidian parser
- ✓ Setlist Management UI with color-coded sets
- ✓ In-set song adding with searchable dialog
- ✓ Set Color Helper with Material 3 tonal palettes

**Nested Relations (SetlistWithSets):**
- `SetlistWithSets` → `List<SetWithEntries>` → `List<SetEntryWithSong>`
- Enables single query fetch of complete setlist hierarchy
- Uses Room's @Relation with entity parameter for multi-level nesting

**UI Features:**
- **Library Badging:** Small "Set 1", "Set 2" chips showing where songs are used
- **Set Coloring:** 6-color rotation for visual set distinction
- **Song Selection Dialog:** Searchable library when adding to sets
- **Import Feedback:** Snackbar shows added/skipped counts

**Future Milestones:**
- Song Detail Screen (Milestone 2 - Task 8)
- User and Device entities (Milestone 4: Authentication)
- ConflictRecord entity (Milestone 5: Sync)
- Server-side PostgreSQL schema (Backend milestones)

**Status:** Milestone 2 - Tasks 1-7 complete. Data layer and UI operational, verified on physical SM-X210 tablet.
