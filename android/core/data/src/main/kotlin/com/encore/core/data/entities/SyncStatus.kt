package com.encore.core.data.entities

/**
 * Sync status for offline-first entities.
 *
 * Tracks whether an entity has been synced with the server or has pending changes.
 */
enum class SyncStatus {
    /**
     * Entity is fully synced with server.
     * No local changes pending upload.
     */
    SYNCED,

    /**
     * Entity has local changes that need to be uploaded to server.
     * Will be sent on next manual sync.
     */
    PENDING_UPLOAD,

    /**
     * Entity is marked for deletion.
     * Will be deleted from server on next sync.
     */
    PENDING_DELETE,

    /**
     * Both local and remote content diverged since the last sync.
     * ConflictResolutionDialog must be shown before this song can be opened.
     */
    CONFLICT
}
