package com.encore.core.data.repository

import com.encore.core.data.dao.SongDao
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.sync.ContentSyncStatus
import com.encore.core.data.sync.EncoreApiService
import com.encore.core.data.sync.FileHashUtils
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
     * Search songs in a specific set by title or artist.
     * If query is empty or blank, returns all songs in the set.
     *
     * @param query Search term (partial match, case-insensitive)
     * @param setNumber Set number (1-4)
     * @return Flow of matching songs in the set
     */
    fun searchSongsInSet(query: String, setNumber: Int): Flow<List<SongEntity>>

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

    /**
     * Update the zoom level for a specific song.
     * Used in Performance Mode to persist per-song zoom preferences.
     *
     * @param songId Song UUID
     * @param zoomLevel Zoom level (0.5-3.0, where 1.0 = 100%)
     * @return Result indicating success or error
     */
    suspend fun updateZoomLevel(songId: String, zoomLevel: Float): Result<Unit>

    /**
     * Get all songs that have no key parsed yet.
     * Used for backfilling key on previously imported songs.
     *
     * @return List of songs with null displayKey
     */
    suspend fun getSongsWithoutKey(): List<SongEntity>

    /**
     * Get songs in a specific set ordered by position within the set.
     * Used when set filter is active with no search text.
     *
     * @param setNumber Set number (1-4)
     * @return Flow of songs ordered by set position
     */
    fun getSongsInSetOrdered(setNumber: Int): Flow<List<SongEntity>>

    /**
     * Get all songs as a one-shot list for audit scanning.
     */
    suspend fun getAllSongsOnce(): List<SongEntity>

    /**
     * Write the result of an audit scan for a single song.
     */
    suspend fun updateValidationResult(id: String, isVerified: Boolean, errors: String?, timestamp: Long)

    /**
     * Reactive stream of songs that have validation errors.
     * Only includes songs that have been scanned and failed.
     */
    fun getInvalidSongs(): Flow<List<SongEntity>>

    /**
     * Compare local markdownBody hash against the server-side hash to determine
     * whether a sync action is needed.
     *
     * Decision table:
     *  - lastSyncedHash == null                                   → [ContentSyncStatus.NeverSynced]
     *  - remoteHash == null (server echo for SYNCED fake)         → [ContentSyncStatus.UpToDate]
     *  - !isDirty && remoteHash != lastSyncedHash                 → [ContentSyncStatus.RemoteAhead]
     *  - isDirty && remoteHash == lastSyncedHash                  → [ContentSyncStatus.LocalAhead]
     *  - isDirty && remoteHash != lastSyncedHash                  → [ContentSyncStatus.Conflict]
     *  - otherwise                                                → [ContentSyncStatus.UpToDate]
     *
     * Always runs on [kotlinx.coroutines.Dispatchers.IO] internally via [FileHashUtils].
     *
     * @param songId     UUID of the song to check
     * @param apiService [EncoreApiService] implementation (real Ktor or [FakeSyncProvider])
     */
    suspend fun checkSyncStatus(songId: String, apiService: EncoreApiService): ContentSyncStatus

    /**
     * Mark a sync as successful by writing the current content hash into [lastSyncedHash]
     * and clearing [isDirty].
     *
     * @param songId UUID of the song that was just synced
     */
    suspend fun markSynced(songId: String)
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

    override fun searchSongsInSet(query: String, setNumber: Int): Flow<List<SongEntity>> {
        // Always use the filtered query for set-specific searches
        return songDao.searchSongsInSet(query.trim(), setNumber)
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

    override suspend fun updateZoomLevel(songId: String, zoomLevel: Float): Result<Unit> {
        return try {
            val song = songDao.getById(songId)
            if (song != null) {
                val updatedSong = song.copy(
                    lastZoomLevel = zoomLevel,
                    localUpdatedAt = System.currentTimeMillis()
                )
                songDao.update(updatedSong)
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Song not found: $songId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSongsWithoutKey(): List<SongEntity> = songDao.getSongsWithoutKey()

    override fun getSongsInSetOrdered(setNumber: Int): Flow<List<SongEntity>> =
        songDao.getSongsInSetOrdered(setNumber)

    override suspend fun getAllSongsOnce(): List<SongEntity> = songDao.getAllSongsOnce()

    override suspend fun updateValidationResult(
        id: String,
        isVerified: Boolean,
        errors: String?,
        timestamp: Long
    ) = songDao.updateValidation(id, isVerified, errors, timestamp)

    override fun getInvalidSongs(): Flow<List<SongEntity>> = songDao.getInvalidSongs()

    override suspend fun checkSyncStatus(songId: String, apiService: EncoreApiService): ContentSyncStatus {
        val song = songDao.getById(songId) ?: return ContentSyncStatus.NeverSynced
        if (song.lastSyncedHash == null) return ContentSyncStatus.NeverSynced

        val remote = apiService.getRemoteHash(songId)
        val remoteHash = remote.remoteHash
            ?: return ContentSyncStatus.UpToDate // FakeSyncProvider.SYNCED path

        return when {
            !song.isDirty && remoteHash != song.lastSyncedHash ->
                ContentSyncStatus.RemoteAhead(remoteHash)

            song.isDirty && remoteHash == song.lastSyncedHash ->
                ContentSyncStatus.LocalAhead

            song.isDirty && remoteHash != song.lastSyncedHash -> {
                val localHash = FileHashUtils.hashMarkdownBody(song.markdownBody)
                ContentSyncStatus.Conflict(localHash, remoteHash)
            }

            else -> ContentSyncStatus.UpToDate
        }
    }

    override suspend fun markSynced(songId: String) {
        val song = songDao.getById(songId) ?: return
        val hash = FileHashUtils.hashMarkdownBody(song.markdownBody)
        songDao.update(
            song.copy(
                lastSyncedHash = hash,
                isDirty = false,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }
}
