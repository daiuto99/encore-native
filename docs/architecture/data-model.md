# Encore Data Model

**Milestone:** 1 - Foundation
**Status:** Draft
**Last Updated:** 2026-03-26

## Entity Relationship Overview

```
User (1) ──────┬─────────── (*) Song
               │
               ├─────────── (*) Setlist
               │
               └─────────── (*) Device

Setlist (1) ────────────── (*) Set

Set (1) ────────────────── (*) SetEntry

SetEntry (*) ───────────── (1) Song

User (1) ───────────────── (*) ConflictRecord
```

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

## Room Database Schema (Android Client)

The local Room database mirrors the server schema with additional fields for offline operation:

### Additional Client-Side Fields

All entities include:
- `sync_status`: Enum ("synced", "pending_upload", "pending_delete")
- `local_updated_at`: Timestamp of last local modification
- `last_synced_at`: Timestamp of last successful sync

### Indexes

```kotlin
// Song indexes for fast search
@Index(value = ["user_id", "title"])
@Index(value = ["user_id", "artist"])
@Index(value = ["user_id", "title", "artist"], unique = true)

// SetEntry indexes for fast setlist loading
@Index(value = ["set_id", "position"], unique = true)
@Index(value = ["song_id"]) // Find all uses of a song

// Sync indexes
@Index(value = ["sync_status"])
```

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

## Future Considerations (Post-V1)

- `capo_mode`: Boolean for capo transposition
- `target_key`: String for future transposition target
- `source_key`: String for original key before transposition
- `chord_tokens`: JSONB array for advanced search
- `tags`: Array of user-defined tags
- `setlist_templates`: Reusable setlist patterns

---

**Status:** Ready for implementation after final review.
