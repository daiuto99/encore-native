package com.encore.core.data.sync

import android.content.IntentSender
import kotlinx.coroutines.flow.SharedFlow

/**
 * Unified contract for Encore's cloud sync backend.
 *
 * Extends [EncoreApiService] (hash fetch) and adds:
 *  - Session lock primitives ([requestLock] / [releaseLock])
 *  - Manifest fetch for bulk reconciliation ([fetchManifest])
 *  - Song body upload/download ([uploadSong] / [downloadSong])
 *
 * [FakeSyncProvider] satisfies this interface for offline/debug use.
 * [GcpSyncProvider] will satisfy it for production using GCS REST + OAuth2.
 */
interface SyncProvider : EncoreApiService {

    /**
     * Hot stream of OAuth2 consent Intents.
     *
     * Emits whenever the backend catches a [com.google.android.gms.auth.UserRecoverableAuthException]
     * (i.e. the user has not yet granted the required GCS scopes). The UI layer must collect
     * this flow and launch the provided [Intent] via an [android.app.Activity] so the Google
     * Play Services consent dialog can be shown. Once the user taps "Allow", subsequent
     * [com.google.android.gms.auth.GoogleAuthUtil.getToken] calls will succeed.
     */
    val authConsentEvents: SharedFlow<IntentSender>

    /**
     * Request an exclusive edit lock for [songId].
     *
     * @return [LockResult.Acquired] if granted; [LockResult.LockedBy] if another client owns the lock.
     */
    suspend fun requestLock(songId: String): LockResult

    /**
     * Release the edit lock for [songId].
     * No-op if this device does not currently hold the lock.
     */
    suspend fun releaseLock(songId: String)

    /**
     * Fetch the library manifest for [userId] from the sync backend.
     *
     * The manifest lists every song the server knows about, along with its
     * current hash and last-modified timestamp. Used to drive bulk reconciliation
     * without making one HTTP call per song.
     *
     * @return [GcpManifest] on success; null if the manifest doesn't exist yet or network is unavailable.
     */
    suspend fun fetchManifest(userId: String): GcpManifest?

    /**
     * Upload the full [markdownBody] for [songId] to the sync backend.
     *
     * @param userId      Owner of the song (used for bucket path scoping).
     * @param songId      UUID of the song being uploaded.
     * @param markdownBody Full markdown content to store.
     */
    suspend fun uploadSong(userId: String, songId: String, markdownBody: String)

    /**
     * Download the markdownBody for [songId] from the sync backend.
     *
     * @param userId  Owner of the song.
     * @param songId  UUID of the song to download.
     * @return Markdown content string on success; null if not found or unreachable.
     */
    suspend fun downloadSong(userId: String, songId: String): String?
}

/**
 * Server-side manifest listing all songs the backend knows about for a user.
 *
 * @param songs Map of songId → [GcpSongEntry] with hash and timestamp.
 */
data class GcpManifest(
    val songs: Map<String, GcpSongEntry>
)

/**
 * Single entry in the [GcpManifest].
 *
 * @param hash      MD5 hash of the song's current markdownBody on the server.
 * @param updatedAt Unix timestamp (ms) of the last server-side write.
 */
data class GcpSongEntry(
    val hash: String,
    val updatedAt: Long
)
