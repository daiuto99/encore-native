package com.encore.core.data.db

import androidx.room.TypeConverter
import com.encore.core.data.entities.SyncStatus

/**
 * Room type converters for non-primitive types.
 *
 * Handles conversion between Kotlin types and SQLite-compatible types.
 * Note: Long/Int/String are handled natively by Room, no converters needed.
 */
class EncoreTypeConverters {

    /**
     * Convert SyncStatus enum to String for database storage.
     */
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String {
        return status.name
    }

    /**
     * Convert String from database to SyncStatus enum.
     */
    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return try {
            SyncStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            SyncStatus.SYNCED // Default fallback
        }
    }
}
