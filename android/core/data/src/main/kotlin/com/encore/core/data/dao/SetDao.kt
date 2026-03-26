package com.encore.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.relations.SetWithEntries
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Set operations.
 *
 * Provides CRUD operations and queries for managing sets within setlists.
 * Handles set renumbering when sets are deleted (e.g., deleting Set 2 makes Set 3 become Set 2).
 */
@Dao
interface SetDao {

    /**
     * Get all sets for a setlist, ordered by number.
     *
     * @param setlistId Setlist UUID
     * @return Flow of sets ordered by number (Set 1, Set 2, etc.)
     */
    @Query("SELECT * FROM sets WHERE setlist_id = :setlistId ORDER BY number ASC")
    fun getSetsForSetlist(setlistId: String): Flow<List<SetEntity>>

    /**
     * Get a single set by ID.
     *
     * @param id Set UUID
     * @return Set or null if not found
     */
    @Query("SELECT * FROM sets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SetEntity?

    /**
     * Get a set with all its entries (songs) in a transaction.
     *
     * @param setId Set UUID
     * @return Set with all set entries
     */
    @Transaction
    @Query("SELECT * FROM sets WHERE id = :setId")
    suspend fun getSetWithEntries(setId: String): SetWithEntries?

    /**
     * Get a set with all its entries as a Flow for reactive UI.
     *
     * @param setId Set UUID
     * @return Flow of set with entries (updates when songs are added/removed)
     */
    @Transaction
    @Query("SELECT * FROM sets WHERE id = :setId")
    fun observeSetWithEntries(setId: String): Flow<SetWithEntries?>

    /**
     * Get all sets for a setlist with their entries.
     *
     * @param setlistId Setlist UUID
     * @return Flow of sets with entries, ordered by set number
     */
    @Transaction
    @Query("SELECT * FROM sets WHERE setlist_id = :setlistId ORDER BY number ASC")
    fun getSetsWithEntriesForSetlist(setlistId: String): Flow<List<SetWithEntries>>

    /**
     * Insert a new set.
     *
     * @param set Set to insert
     * @return Row ID of inserted set
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(set: SetEntity): Long

    /**
     * Update an existing set.
     * Used for changing set number or color token.
     *
     * @param set Set with updated fields
     */
    @Update
    suspend fun update(set: SetEntity)

    /**
     * Delete a set.
     * Cascade will also delete all SetEntries for this set.
     *
     * Note: After deletion, call renumberSets() to adjust remaining set numbers.
     *
     * @param set Set to delete
     */
    @Delete
    suspend fun delete(set: SetEntity)

    /**
     * Get the highest set number for a setlist.
     * Used when adding a new set (new number = max + 1).
     *
     * @param setlistId Setlist UUID
     * @return Maximum set number or 0 if no sets exist
     */
    @Query("SELECT COALESCE(MAX(number), 0) FROM sets WHERE setlist_id = :setlistId")
    suspend fun getMaxSetNumber(setlistId: String): Int

    /**
     * Get all sets with number greater than or equal to the specified number.
     * Used for renumbering after deletion.
     *
     * @param setlistId Setlist UUID
     * @param number Starting number
     * @return List of sets to renumber
     */
    @Query("""
        SELECT * FROM sets
        WHERE setlist_id = :setlistId
        AND number >= :number
        ORDER BY number ASC
    """)
    suspend fun getSetsToRenumber(setlistId: String, number: Int): List<SetEntity>

    /**
     * Get count of sets in a setlist.
     *
     * @param setlistId Setlist UUID
     * @return Number of sets in the setlist
     */
    @Query("SELECT COUNT(*) FROM sets WHERE setlist_id = :setlistId")
    suspend fun getSetCount(setlistId: String): Int
}
