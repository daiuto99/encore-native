package com.encore.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.encore.core.data.entities.SetlistEntity
import com.encore.core.data.relations.SetlistWithSets
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Setlist operations.
 *
 * Provides CRUD operations and @Transaction queries for fetching complete setlist data
 * with all nested sets and songs in correct order.
 */
@Dao
interface SetlistDao {

    /**
     * Get all setlists ordered by name.
     * Returns a Flow for reactive UI updates.
     */
    @Query("SELECT * FROM setlists ORDER BY name ASC")
    fun getAllSetlists(): Flow<List<SetlistEntity>>

    /**
     * Get a single setlist by ID.
     *
     * @param id Setlist UUID
     * @return Setlist or null if not found
     */
    @Query("SELECT * FROM setlists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SetlistEntity?

    /**
     * Get a setlist with all its sets in a single transaction.
     *
     * @Transaction ensures atomicity when fetching related data.
     * Sets are ordered by their number (Set 1, Set 2, etc.).
     *
     * @param setlistId Setlist UUID
     * @return Setlist with all nested sets
     */
    @Transaction
    @Query("SELECT * FROM setlists WHERE id = :setlistId")
    suspend fun getSetlistWithSets(setlistId: String): SetlistWithSets?

    /**
     * Get a setlist with all its sets as a Flow for reactive UI.
     *
     * @param setlistId Setlist UUID
     * @return Flow of setlist with sets (updates when sets change)
     */
    @Transaction
    @Query("SELECT * FROM setlists WHERE id = :setlistId")
    fun observeSetlistWithSets(setlistId: String): Flow<SetlistWithSets?>

    /**
     * Get all setlists with their sets in a single transaction.
     *
     * @return Flow of all setlists with nested sets
     */
    @Transaction
    @Query("SELECT * FROM setlists ORDER BY name ASC")
    fun getAllSetlistsWithSets(): Flow<List<SetlistWithSets>>

    /**
     * Insert a new setlist.
     *
     * @param setlist Setlist to insert
     * @return Row ID of inserted setlist
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(setlist: SetlistEntity): Long

    /**
     * Update an existing setlist.
     * Used for renaming or updating metadata.
     *
     * @param setlist Setlist with updated fields
     */
    @Update
    suspend fun update(setlist: SetlistEntity)

    /**
     * Delete a setlist.
     * Cascade will also delete all Sets and SetEntries associated with this setlist.
     *
     * @param setlist Setlist to delete
     */
    @Delete
    suspend fun delete(setlist: SetlistEntity)

    /**
     * Get count of all setlists.
     *
     * @return Total number of setlists
     */
    @Query("SELECT COUNT(*) FROM setlists")
    suspend fun getCount(): Int

    /**
     * Get all setlists as a one-shot (non-Flow) result.
     * Used when we need to inspect setlist state inside a suspend function.
     */
    @Query("SELECT * FROM setlists ORDER BY name ASC")
    suspend fun getSetlistsOnce(): List<SetlistEntity>

    /**
     * Search setlists by name.
     *
     * @param query Search term (case-insensitive, partial match)
     * @return Flow of matching setlists
     */
    @Query("""
        SELECT * FROM setlists
        WHERE name LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    fun searchSetlists(query: String): Flow<List<SetlistEntity>>
}
