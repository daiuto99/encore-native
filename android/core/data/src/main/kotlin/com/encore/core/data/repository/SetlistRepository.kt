package com.encore.core.data.repository

import com.encore.core.data.dao.SetDao
import com.encore.core.data.dao.SetEntryDao
import com.encore.core.data.dao.SetlistDao
import com.encore.core.data.dao.SongDao
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SetEntryEntity
import com.encore.core.data.entities.SetlistEntity
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.relations.SetEntryWithSong
import com.encore.core.data.relations.SetlistWithSets
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository for Setlist, Set, and SetEntry data operations.
 *
 * Handles complex relationships between setlists, sets, and songs.
 * Manages song ordering within sets and set renumbering logic.
 */
interface SetlistRepository {

    // ========== Setlist Operations ==========

    /**
     * Get all setlists ordered by name.
     *
     * @return Flow of all setlists
     */
    fun getSetlists(): Flow<List<SetlistEntity>>

    /**
     * Get a setlist with all its sets.
     *
     * @param setlistId Setlist UUID
     * @return Setlist with nested sets or null
     */
    suspend fun getSetlistWithSets(setlistId: String): SetlistWithSets?

    /**
     * Observe a setlist with all its sets for reactive UI.
     *
     * @param setlistId Setlist UUID
     * @return Flow of setlist with sets (updates when sets change)
     */
    fun observeSetlistWithSets(setlistId: String): Flow<SetlistWithSets?>

    /**
     * Create a new setlist with an initial Set 1.
     *
     * @param name Setlist name
     * @param userId User ID (default "local-user")
     * @return Result with setlist ID or error
     */
    suspend fun createSetlist(
        name: String,
        userId: String = "local-user"
    ): Result<String>

    /**
     * Update setlist metadata (e.g., rename).
     *
     * @param setlist Updated setlist entity
     * @return Result indicating success or error
     */
    suspend fun updateSetlist(setlist: SetlistEntity): Result<Unit>

    /**
     * Delete a setlist.
     * Cascade will delete all sets and set entries.
     *
     * @param setlist Setlist to delete
     * @return Result indicating success or error
     */
    suspend fun deleteSetlist(setlist: SetlistEntity): Result<Unit>

    // ========== Set Operations ==========

    /**
     * Get all sets for a setlist, ordered by number.
     *
     * @param setlistId Setlist UUID
     * @return Flow of sets ordered by number
     */
    fun getSetsForSetlist(setlistId: String): Flow<List<SetEntity>>

    /**
     * Add a new set to a setlist.
     * Automatically assigns next available set number.
     *
     * @param setlistId Setlist UUID
     * @param colorToken Optional color token for UI (e.g., "blue", "green")
     * @return Result with set ID or error
     */
    suspend fun addSetToSetlist(
        setlistId: String,
        colorToken: String? = null
    ): Result<String>

    /**
     * Delete a set and renumber subsequent sets.
     * Example: Deleting Set 2 makes Set 3 become Set 2, Set 4 become Set 3, etc.
     *
     * @param set Set to delete
     * @return Result indicating success or error
     */
    suspend fun deleteSetAndRenumber(set: SetEntity): Result<Unit>

    // ========== Set Entry Operations (Songs in Sets) ==========

    /**
     * Get all songs in a set, ordered by position.
     *
     * @param setId Set UUID
     * @return Flow of entries with songs in correct order
     */
    fun getSongsInSet(setId: String): Flow<List<SetEntryWithSong>>

    /**
     * Add a song to a set at the end (next available position).
     *
     * @param setId Set UUID
     * @param songId Song UUID
     * @return Result with entry ID or error
     */
    suspend fun addSongToSet(setId: String, songId: String): Result<String>

    /**
     * Add a song to a set at a specific position.
     * Shifts existing songs at that position and after down by 1.
     *
     * @param setId Set UUID
     * @param songId Song UUID
     * @param position Target position (0-indexed)
     * @return Result with entry ID or error
     */
    suspend fun addSongToSetAtPosition(
        setId: String,
        songId: String,
        position: Int
    ): Result<String>

    /**
     * Remove a song from a set and compact positions (remove gaps).
     *
     * @param entryId SetEntry UUID
     * @return Result indicating success or error
     */
    suspend fun removeSongFromSet(entryId: String): Result<Unit>

    /**
     * Reorder a song within a set (move from old position to new position).
     *
     * @param entryId SetEntry UUID
     * @param newPosition Target position (0-indexed)
     * @return Result indicating success or error
     */
    suspend fun reorderSongInSet(entryId: String, newPosition: Int): Result<Unit>
}

/**
 * Implementation of SetlistRepository using Room DAOs.
 */
class SetlistRepositoryImpl(
    private val setlistDao: SetlistDao,
    private val setDao: SetDao,
    private val setEntryDao: SetEntryDao,
    private val songDao: SongDao
) : SetlistRepository {

    // ========== Setlist Operations ==========

    override fun getSetlists(): Flow<List<SetlistEntity>> {
        return setlistDao.getAllSetlists()
    }

    override suspend fun getSetlistWithSets(setlistId: String): SetlistWithSets? {
        return setlistDao.getSetlistWithSets(setlistId)
    }

    override fun observeSetlistWithSets(setlistId: String): Flow<SetlistWithSets?> {
        return setlistDao.observeSetlistWithSets(setlistId)
    }

    override suspend fun createSetlist(name: String, userId: String): Result<String> {
        return try {
            val now = System.currentTimeMillis()
            val setlistId = UUID.randomUUID().toString()

            val setlist = SetlistEntity(
                id = setlistId,
                userId = userId,
                name = name,
                version = 1,
                createdAt = now,
                updatedAt = now,
                syncStatus = com.encore.core.data.entities.SyncStatus.SYNCED,
                localUpdatedAt = now,
                lastSyncedAt = now
            )

            setlistDao.insert(setlist)

            // Create initial Set 1
            val setId = UUID.randomUUID().toString()
            val set = SetEntity(
                id = setId,
                setlistId = setlistId,
                number = 1,
                colorToken = "blue", // Default color
                createdAt = now
            )

            setDao.insert(set)

            Result.success(setlistId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSetlist(setlist: SetlistEntity): Result<Unit> {
        return try {
            setlistDao.update(setlist)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSetlist(setlist: SetlistEntity): Result<Unit> {
        return try {
            setlistDao.delete(setlist)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== Set Operations ==========

    override fun getSetsForSetlist(setlistId: String): Flow<List<SetEntity>> {
        return setDao.getSetsForSetlist(setlistId)
    }

    override suspend fun addSetToSetlist(
        setlistId: String,
        colorToken: String?
    ): Result<String> {
        return try {
            val now = System.currentTimeMillis()
            val maxSetNumber = setDao.getMaxSetNumber(setlistId)
            val newSetNumber = maxSetNumber + 1

            val setId = UUID.randomUUID().toString()
            val set = SetEntity(
                id = setId,
                setlistId = setlistId,
                number = newSetNumber,
                colorToken = colorToken,
                createdAt = now
            )

            setDao.insert(set)
            Result.success(setId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSetAndRenumber(set: SetEntity): Result<Unit> {
        return try {
            // Delete the set (cascade will delete entries)
            setDao.delete(set)

            // Get all sets with number > deleted set's number
            val setsToRenumber = setDao.getSetsToRenumber(set.setlistId, set.number + 1)

            // Renumber each set (decrement by 1)
            setsToRenumber.forEach { setToRenumber ->
                val updatedSet = setToRenumber.copy(number = setToRenumber.number - 1)
                setDao.update(updatedSet)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== Set Entry Operations ==========

    override fun getSongsInSet(setId: String): Flow<List<SetEntryWithSong>> {
        return setEntryDao.getEntriesWithSongsForSet(setId)
    }

    override suspend fun addSongToSet(setId: String, songId: String): Result<String> {
        return try {
            // Verify song exists
            val song = songDao.getById(songId)
                ?: return Result.failure(Exception("Song not found: $songId"))

            val now = System.currentTimeMillis()
            val maxPosition = setEntryDao.getMaxPosition(setId)
            val newPosition = maxPosition + 1

            val entryId = UUID.randomUUID().toString()
            val entry = SetEntryEntity(
                id = entryId,
                setId = setId,
                songId = songId,
                position = newPosition,
                createdAt = now
            )

            setEntryDao.insert(entry)
            Result.success(entryId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addSongToSetAtPosition(
        setId: String,
        songId: String,
        position: Int
    ): Result<String> {
        return try {
            // Verify song exists
            val song = songDao.getById(songId)
                ?: return Result.failure(Exception("Song not found: $songId"))

            // Shift existing entries at position and after down by 1
            val entriesToShift = setEntryDao.getEntriesToReposition(setId, position)
            entriesToShift.forEach { entry ->
                val updatedEntry = entry.copy(position = entry.position + 1)
                setEntryDao.update(updatedEntry)
            }

            // Insert new entry at target position
            val now = System.currentTimeMillis()
            val entryId = UUID.randomUUID().toString()
            val entry = SetEntryEntity(
                id = entryId,
                setId = setId,
                songId = songId,
                position = position,
                createdAt = now
            )

            setEntryDao.insert(entry)
            Result.success(entryId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeSongFromSet(entryId: String): Result<Unit> {
        return try {
            val entry = setEntryDao.getById(entryId)
                ?: return Result.failure(Exception("Entry not found: $entryId"))

            // Delete the entry
            setEntryDao.delete(entry)

            // Compact positions (shift entries after deleted position up by 1)
            val entriesToCompact = setEntryDao.getEntriesToReposition(
                entry.setId,
                entry.position + 1
            )

            entriesToCompact.forEach { entryToCompact ->
                val updatedEntry = entryToCompact.copy(position = entryToCompact.position - 1)
                setEntryDao.update(updatedEntry)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reorderSongInSet(entryId: String, newPosition: Int): Result<Unit> {
        return try {
            val entry = setEntryDao.getById(entryId)
                ?: return Result.failure(Exception("Entry not found: $entryId"))

            val oldPosition = entry.position

            if (oldPosition == newPosition) {
                // No change needed
                return Result.success(Unit)
            }

            // Remove from old position (compact)
            val entriesToCompact = setEntryDao.getEntriesToReposition(
                entry.setId,
                oldPosition + 1
            )
            entriesToCompact.forEach { entryToCompact ->
                val updatedEntry = entryToCompact.copy(position = entryToCompact.position - 1)
                setEntryDao.update(updatedEntry)
            }

            // Shift entries at new position and after down by 1
            val entriesToShift = setEntryDao.getEntriesToReposition(entry.setId, newPosition)
            entriesToShift.forEach { entryToShift ->
                val updatedEntry = entryToShift.copy(position = entryToShift.position + 1)
                setEntryDao.update(updatedEntry)
            }

            // Update entry to new position
            val updatedEntry = entry.copy(position = newPosition)
            setEntryDao.update(updatedEntry)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
