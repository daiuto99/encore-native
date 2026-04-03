package com.encore.core.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Song - a single master version of a markdown chart.
 * Changes apply globally across all setlists.
 *
 * Based on: docs/architecture/data-model.md
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["user_id", "title"]),
        Index(value = ["user_id", "artist"]),
        Index(value = ["user_id", "title", "artist"], unique = true), // Duplicate detection
        Index(value = ["sync_status"]) // Sync queries
    ]
)
data class SongEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID as String for Room compatibility

    @ColumnInfo(name = "user_id")
    val userId: String, // "local-user" hardcoded for Milestone 2, multi-user in Milestone 4

    @ColumnInfo(name = "title")
    val title: String, // Searchable, indexed

    @ColumnInfo(name = "artist")
    val artist: String, // Searchable, indexed

    @ColumnInfo(name = "display_key")
    val displayKey: String?, // Active key shown in UI (e.g., "G", "Dm", "C#m")

    @ColumnInfo(name = "original_key")
    val originalKey: String? = null, // Reference key from source/import — null until set

    @ColumnInfo(name = "markdown_body")
    val markdownBody: String, // Full editable chart content

    @ColumnInfo(name = "original_import_body")
    val originalImportBody: String?, // Preserve initial import for reference

    @ColumnInfo(name = "is_lead_guitar")
    val isLeadGuitar: Boolean = false, // True when this song uses lead guitar part

    @ColumnInfo(name = "is_verified")
    val isVerified: Boolean = false, // True when last audit found no errors

    @ColumnInfo(name = "validation_errors")
    val validationErrors: String? = null, // Null = clean or unscanned; non-null = issues found

    @ColumnInfo(name = "last_verified_at")
    val lastVerifiedAt: Long = 0L, // Unix timestamp (ms) of last audit run

    @ColumnInfo(name = "last_zoom_level")
    val lastZoomLevel: Float = 1.0f, // Performance mode zoom level (1.0 = 100%, range 0.5-3.0)

    @ColumnInfo(name = "owner_id")
    val ownerId: String? = null, // Google account ID — null until user signs in (Milestone 4)

    @ColumnInfo(name = "version")
    val version: Int = 1, // Increments on edit, used for conflict detection

    @ColumnInfo(name = "created_at")
    val createdAt: Long, // Import time (Unix timestamp milliseconds)

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long, // Last edit time (Unix timestamp milliseconds)

    // Client-side fields for offline operation
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED,

    @ColumnInfo(name = "local_updated_at")
    val localUpdatedAt: Long, // Last local modification timestamp

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null, // Last successful sync timestamp

    @ColumnInfo(name = "is_harmony_mode")
    val isHarmonyMode: Boolean = false, // Harmony mode viewer toggle

    @ColumnInfo(name = "highlight_style")
    val highlightStyle: Int = 0, // 0 = None, 1 = Chords Bold, 2 = Lyrics Faded

    // ── Sync hash fields (Milestone 4 sync engine) ────────────────────────────
    @ColumnInfo(name = "last_synced_hash")
    val lastSyncedHash: String? = null, // MD5 of markdownBody at last successful sync; null = never synced

    @ColumnInfo(name = "is_dirty")
    val isDirty: Boolean = false, // true when local edits haven't been pushed to server

    @ColumnInfo(name = "is_locked_by_other")
    val isLockedByOther: Boolean = false, // true when another client holds the edit lock

    @ColumnInfo(name = "capo_enabled")
    val capoEnabled: Boolean = false,

    @ColumnInfo(name = "capo_fret")
    val capoFret: Int = 2       // 1-12; only relevant when capoEnabled = true
)
