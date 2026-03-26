package com.encore.core.data.repository

import com.encore.core.data.dao.SongDao
import com.encore.core.data.entities.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Song data operations.
 *
 * Provides abstraction over SongDao and handles business logic for song management.
 * Implements offline-first pattern with Flow-based reactive queries.
 */
interface SongRepository {

    /**
     * Get all songs ordered by title.
     *
     * @return Flow of all songs for reactive UI updates
     */
    fun getSongs(): Flow<List<SongEntity>>

    /**
     * Search songs by title or artist.
     * If query is empty or blank, returns all songs.
     *
     * @param query Search term (partial match, case-insensitive)
     * @return Flow of matching songs
     */
    fun searchSongs(query: String): Flow<List<SongEntity>>

    /**
     * Get a single song by ID.
     *
     * @param id Song UUID
     * @return Song or null if not found
     */
    suspend fun getSongById(id: String): SongEntity?

    /**
     * Insert or update a song (upsert operation).
     * Used for import flow and editing songs.
     *
     * - If song.id doesn't exist: inserts new song
     * - If song.id exists: updates existing song
     *
     * @param song Song to insert or update
     * @return Result with song ID or error
     */
    suspend fun upsertSong(song: SongEntity): Result<String>

    /**
     * Delete a song.
     * Cascade will remove all SetEntries referencing this song.
     *
     * @param song Song to delete
     * @return Result indicating success or error
     */
    suspend fun deleteSong(song: SongEntity): Result<Unit>

    /**
     * Check if a duplicate song exists (by title and artist).
     * Used during import to detect existing songs.
     *
     * @param title Song title
     * @param artist Artist name
     * @param userId User ID (default "local-user" for Milestone 2)
     * @return Existing song or null
     */
    suspend fun findDuplicate(
        title: String,
        artist: String,
        userId: String = "local-user"
    ): SongEntity?

    /**
     * Get total count of songs in library.
     *
     * @return Number of songs
     */
    suspend fun getSongCount(): Int
}

/**
 * Implementation of SongRepository using Room DAO.
 */
class SongRepositoryImpl(
    private val songDao: SongDao
) : SongRepository {

    override fun getSongs(): Flow<List<SongEntity>> {
        return songDao.getAllSongs()
    }

    override fun searchSongs(query: String): Flow<List<SongEntity>> {
        // If query is empty or blank, return all songs
        return if (query.isBlank()) {
            songDao.getAllSongs()
        } else {
            songDao.searchSongs(query.trim())
        }
    }

    override suspend fun getSongById(id: String): SongEntity? {
        return songDao.getById(id)
    }

    override suspend fun upsertSong(song: SongEntity): Result<String> {
        return try {
            val existingSong = songDao.getById(song.id)

            if (existingSong != null) {
                // Update existing song
                songDao.update(song)
            } else {
                // Insert new song
                songDao.insert(song)
            }

            Result.success(song.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSong(song: SongEntity): Result<Unit> {
        return try {
            songDao.delete(song)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun findDuplicate(
        title: String,
        artist: String,
        userId: String
    ): SongEntity? {
        return songDao.findDuplicate(userId, title, artist)
    }

    override suspend fun getSongCount(): Int {
        return songDao.getCount()
    }
}
