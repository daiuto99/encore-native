package com.encore.core.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Set - a numbered segment within a setlist (e.g., Set 1, Set 2).
 *
 * Sets use color tabs for visual distinction.
 * Deleting a set triggers automatic renumbering (e.g., deleting Set 2 makes Set 3 become Set 2).
 *
 * Based on: docs/architecture/data-model.md
 */
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = SetlistEntity::class,
            parentColumns = ["id"],
            childColumns = ["setlist_id"],
            onDelete = ForeignKey.CASCADE // Delete sets when setlist is deleted
        )
    ],
    indices = [
        Index(value = ["setlist_id", "number"], unique = true), // Unique constraint
        Index(value = ["setlist_id"])
    ]
)
data class SetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // UUID as String

    @ColumnInfo(name = "setlist_id")
    val setlistId: String, // Foreign key to SetlistEntity

    @ColumnInfo(name = "number")
    val number: Int, // 1, 2, 3, etc. Renumbers on delete

    @ColumnInfo(name = "color_token")
    val colorToken: String? = null, // e.g., "blue", "green", "red" for UI tabs

    @ColumnInfo(name = "created_at")
    val createdAt: Long // Creation time (Unix timestamp milliseconds)
)
