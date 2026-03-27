package com.encore.core.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for SetEntry - a song placed in a specific position within a set.
 *
 * This is the junction/association table that links songs to sets with ordering.
 * A song can appear multiple times across a setlist (e.g., same song in Set 1 and Set 3).
 *
 * Based on: docs/architecture/data-model.md
 */
@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = SetEntity::class,
            parentColumns = ["id"],
            childColumns = ["set_id"],
            onDelete = ForeignKey.CASCADE // Delete entries when set is deleted
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE // Delete entries when song is deleted
        )
    ],
    indices = [
        Index(value = ["set_id", "position"], unique = true), // Unique position per set
        Index(value = ["song_id"]), // Find all uses of a song
        Index(value = ["set_id"]) // Fast setlist loading
    ]
)
data class SetEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID as String

    @ColumnInfo(name = "set_id")
    val setId: String, // Foreign key to SetEntity

    @ColumnInfo(name = "song_id")
    val songId: String, // Foreign key to SongEntity

    @ColumnInfo(name = "position")
    val position: Int, // 0-indexed position in set (0, 1, 2, ...)

    @ColumnInfo(name = "created_at")
    val createdAt: Long // Addition time (Unix timestamp milliseconds)
)
