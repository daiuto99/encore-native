package com.encore.core.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Setlist - a named collection of sets for a performance or rehearsal.
 *
 * A setlist contains multiple sets (Set 1, Set 2, etc.), each containing songs.
 *
 * Based on: docs/architecture/data-model.md
 */
@Entity(
    tableName = "setlists",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["sync_status"])
    ]
)
data class SetlistEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID as String

    @ColumnInfo(name = "user_id")
    val userId: String, // "local-user" hardcoded for Milestone 2

    @ColumnInfo(name = "name")
    val name: String, // Only required field, e.g., "Summer Tour 2026"

    @ColumnInfo(name = "version")
    val version: Int = 1, // For sync conflict detection

    @ColumnInfo(name = "created_at")
    val createdAt: Long, // Creation time (Unix timestamp milliseconds)

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long, // Last modification time

    // Client-side fields for offline operation
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED,

    @ColumnInfo(name = "local_updated_at")
    val localUpdatedAt: Long,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null
)
