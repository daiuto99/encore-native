package com.encore.core.data.sync

/**
 * Result of a [SongRepository.checkSyncStatus] call.
 *
 * Named [ContentSyncStatus] to avoid collision with the existing [SyncStatus] enum
 * that tracks coarse entity state (SYNCED / PENDING_UPLOAD / PENDING_DELETE).
 * This sealed class carries the *hash-level* comparison result used to drive
 * auto-merge and the ConflictResolutionDialog.
 */
sealed class ContentSyncStatus {

    /** Both local and remote are at the same hash. No action needed. */
    object UpToDate : ContentSyncStatus()

    /**
     * Local body has been edited (isDirty=true) and the server hash hasn't changed
     * since the last sync. Safe to auto-push local → server.
     */
    object LocalAhead : ContentSyncStatus()

    /**
     * Server hash has changed but local body is clean (isDirty=false).
     * Safe to auto-pull remote → local.
     *
     * @param remoteHash The server's current MD5 hash
     */
    data class RemoteAhead(val remoteHash: String) : ContentSyncStatus()

    /**
     * Both sides diverged since the last sync.
     * Must surface the ConflictResolutionDialog — no auto-resolution.
     *
     * @param localHash  MD5 of the current local markdownBody
     * @param remoteHash MD5 of the server's current markdownBody
     */
    data class Conflict(val localHash: String, val remoteHash: String) : ContentSyncStatus()

    /** Song has never been synced — no lastSyncedHash baseline exists. */
    object NeverSynced : ContentSyncStatus()
}
