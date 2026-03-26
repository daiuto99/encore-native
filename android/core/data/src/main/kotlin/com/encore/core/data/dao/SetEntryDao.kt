package com.encore.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.encore.core.data.entities.SetEntryEntity
import com.encore.core.data.relations.SetEntryWithSong
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for SetEntry operations.
 *
 * SetEntry is the junction table that links songs to sets with position-based ordering.
 * Manages adding, removing, and reordering songs within sets.
 */
@Dao
interface SetEntryDao {

    /**
     * Get all entries for a set, ordered by position.
     *
     * @param setId Set UUID
     * @return Flow of entries ordered by position (0, 1, 2, ...)
     */
    @Query("SELECT * FROM set_entries WHERE set_id = :setId ORDER BY position ASC")
    fun getEntriesForSet(setId: String): Flow<List<SetEntryEntity>>

    /**
     * Get all entries for a set with their associated songs, ordered by position.
     *
     * @Transaction ensures songs are fetched atomically with entries.
     *
     * @param setId Set UUID
     * @return Flow of entries with songs in correct order
     */
    @Transaction
    @Query("SELECT * FROM set_entries WHERE set_id = :setId ORDER BY position ASC")
    fun getEntriesWithSongsForSet(setId: String): Flow<List<SetEntryWithSong>>

    /**
     * Get a single entry by ID.
     *
     * @param id SetEntry UUID
     * @return Entry or null if not found
     */
    @Query("SELECT * FROM set_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SetEntryEntity?

    /**
     * Get an entry with its song.
     *
     * @param id SetEntry UUID
     * @return Entry with song or null if not found
     */
    @Transaction
    @Query("SELECT * FROM set_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryWithSong(id: String): SetEntryWithSong?

    /**
     * Find all sets that contain a specific song.
     * Used to show where a song is used across setlists.
     *
     * @param songId Song UUID
     * @return List of entries referencing the song
     */
    @Query("SELECT * FROM set_entries WHERE song_id = :songId")
    suspend fun getEntriesForSong(songId: String): List<SetEntryEntity>

    /**
     * Insert a new entry (add song to set).
     * Aborts on conflict (duplicate position in set).
     *
     * @param entry Entry to insert
     * @return Row ID of inserted entry
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: SetEntryEntity): Long

    /**
     * Update an existing entry.
     * Used for reordering songs (changing position).
     *
     * @param entry Entry with updated fields
     */
    @Update
    suspend fun update(entry: SetEntryEntity)

    /**
     * Delete an entry (remove song from set).
     *
     * Note: After deletion, positions should be compacted (no gaps).
     *
     * @param entry Entry to delete
     */
    @Delete
    suspend fun delete(entry: SetEntryEntity)

    /**
     * Get the highest position number for a set.
     * Used when adding a new song (new position = max + 1).
     *
     * @param setId Set UUID
     * @return Maximum position or -1 if no entries exist
     */
    @Query("SELECT COALESCE(MAX(position), -1) FROM set_entries WHERE set_id = :setId")
    suspend fun getMaxPosition(setId: String): Int

    /**
     * Get all entries with position greater than or equal to the specified position.
     * Used for compacting positions after deletion or reordering.
     *
     * @param setId Set UUID
     * @param position Starting position
     * @return List of entries to reposition
     */
    @Query("""
        SELECT * FROM set_entries
        WHERE set_id = :setId
        AND position >= :position
        ORDER BY position ASC
    """)
    suspend fun getEntriesToReposition(setId: String, position: Int): List<SetEntryEntity>

    /**
     * Get count of songs in a set.
     *
     * @param setId Set UUID
     * @return Number of songs in the set
     */
    @Query("SELECT COUNT(*) FROM set_entries WHERE set_id = :setId")
    suspend fun getEntryCount(setId: String): Int

    /**
     * Delete all entries for a set.
     * Used when clearing a set or preparing to rebuild its contents.
     *
     * @param setId Set UUID
     */
    @Query("DELETE FROM set_entries WHERE set_id = :setId")
    suspend fun deleteAllEntriesForSet(setId: String)
}
