package com.encore.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.encore.core.data.entities.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Song operations.
 *
 * Provides CRUD operations and search functionality optimized for live performance use.
 * Search queries use LIKE for partial matching on title and artist.
 */
@Dao
interface SongDao {

    /**
     * Get all songs ordered alphabetically by title.
     * Returns a Flow for reactive UI updates.
     */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    /**
     * Search songs by partial title or artist match.
     * Uses LIKE with wildcards for instant search during live shows.
     *
     * Performance: Indexed on title and artist columns for fast queries.
     *
     * @param query Search term (case-insensitive)
     * @return Flow of matching songs ordered by title
     */
    @Query("""
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%'
        OR artist LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchSongs(query: String): Flow<List<SongEntity>>

    /**
     * Search songs in a specific set by partial title or artist match.
     * Joins with set_entries and sets tables to filter by set number.
     *
     * @param query Search term (case-insensitive)
     * @param setNumber Set number (1-4)
     * @return Flow of matching songs in the set, ordered by title
     */
    @Query("""
        SELECT DISTINCT songs.* FROM songs
        INNER JOIN set_entries ON songs.id = set_entries.song_id
        INNER JOIN sets ON set_entries.set_id = sets.id
        WHERE sets.number = :setNumber
        AND (songs.title LIKE '%' || :query || '%' OR songs.artist LIKE '%' || :query || '%')
        ORDER BY songs.title ASC
    """)
    fun searchSongsInSet(query: String, setNumber: Int): Flow<List<SongEntity>>

    /**
     * Get a single song by ID.
     *
     * @param id Song UUID
     * @return Song or null if not found
     */
    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SongEntity?

    /**
     * Find a duplicate song by title and artist.
     * Used during import to detect existing songs.
     *
     * @param userId User ID (hardcoded "local-user" for Milestone 2)
     * @param title Exact song title
     * @param artist Exact artist name
     * @return Existing song or null
     */
    @Query("""
        SELECT * FROM songs
        WHERE user_id = :userId
        AND title = :title
        AND artist = :artist
        LIMIT 1
    """)
    suspend fun findDuplicate(userId: String, title: String, artist: String): SongEntity?

    /**
     * Insert a new song.
     * Aborts on conflict (duplicate constraint violation).
     *
     * @param song Song to insert
     * @return Row ID of inserted song
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(song: SongEntity): Long

    /**
     * Update an existing song.
     * Used for editing markdown content and incrementing version.
     *
     * @param song Song with updated fields
     */
    @Update
    suspend fun update(song: SongEntity)

    /**
     * Delete a song.
     * Cascade will also delete all SetEntries referencing this song.
     *
     * @param song Song to delete
     */
    @Delete
    suspend fun delete(song: SongEntity)

    /**
     * Get all songs with pending sync status.
     * Used for sync operations in Milestone 4.
     *
     * @return List of songs that need to be synced
     */
    @Query("SELECT * FROM songs WHERE sync_status != 'SYNCED'")
    suspend fun getPendingSyncSongs(): List<SongEntity>

    /**
     * Get count of all songs for statistics.
     *
     * @return Total number of songs in library
     */
    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getCount(): Int

    /**
     * Get all songs that have no key parsed yet.
     * Used for backfilling key on previously imported songs.
     */
    @Query("SELECT * FROM songs WHERE current_key IS NULL")
    suspend fun getSongsWithoutKey(): List<SongEntity>

    /**
     * Get songs in a specific set ordered by their position within the set.
     * Used when a set filter is active and no search text is present.
     *
     * @param setNumber Set number (1-4)
     * @return Flow of songs ordered by position
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN set_entries ON songs.id = set_entries.song_id
        INNER JOIN sets ON set_entries.set_id = sets.id
        WHERE sets.number = :setNumber
        ORDER BY set_entries.position ASC
    """)
    fun getSongsInSetOrdered(setNumber: Int): Flow<List<SongEntity>>
}
