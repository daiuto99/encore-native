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

    @ColumnInfo(name = "current_key")
    val currentKey: String?, // e.g., "G", "Dm", "C#m"

    @ColumnInfo(name = "markdown_body")
    val markdownBody: String, // Full editable chart content

    @ColumnInfo(name = "original_import_body")
    val originalImportBody: String?, // Preserve initial import for reference

    @ColumnInfo(name = "lead_marker")
    val leadMarker: String? = null, // Custom markers for lead vocals (future)

    @ColumnInfo(name = "harmony_markup")
    val harmonyMarkup: String? = null, // Future: harmony annotations

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
    val highlightStyle: Int = 0 // 0 = None, 1 = Chords Bold, 2 = Lyrics Faded
)
