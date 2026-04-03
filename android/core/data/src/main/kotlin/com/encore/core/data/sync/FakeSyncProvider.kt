package com.encore.core.data.sync

/**
 * Result of a session-lock request against the fake server.
 *
 * [Acquired]       — lock granted; this device owns the edit session.
 * [LockedBy]       — another client already holds the lock; editing must be read-only.
 */
sealed class LockResult {
    object Acquired : LockResult()
    data class LockedBy(val owner: String) : LockResult()
}

/**
 * Fake implementation of [EncoreApiService] for development and manual testing.
 *
 * Allows developers to toggle per-song sync scenarios in code without a live
 * Ktor server, so all three conflict branches can be exercised:
 *
 *   [SyncScenario.SYNCED]        → remoteHash == null (server matches local, no conflict)
 *   [SyncScenario.REMOTE_AHEAD]  → server has a new hash, local is clean → auto-accept remote
 *   [SyncScenario.CONFLICT]      → server has a new hash AND local is dirty → show Decision Gate
 *
 * Usage (e.g. in a debug settings screen or a unit test):
 * ```
 *   FakeSyncProvider.setScenario(songId, FakeSyncProvider.SyncScenario.CONFLICT)
 *   FakeSyncProvider.setLocked(songId, "DesktopManager")
 * ```
 * Clear all overrides between test runs with [clearOverrides].
 */
object FakeSyncProvider : EncoreApiService {

    enum class SyncScenario {
        /** Remote hash matches last synced hash — no action needed. */
        SYNCED,

        /** Remote hash has changed, local body is clean — tablet should auto-accept remote. */
        REMOTE_AHEAD,

        /**
         * Remote hash has changed AND local body is dirty — both sides diverged.
         * ConflictResolutionDialog must be shown.
         */
        CONFLICT
    }

    private val overrides = mutableMapOf<String, SyncScenario>()

    /** Per-song lock overrides: songId → owner name. Absent = unlocked. */
    private val lockOverrides = mutableMapOf<String, String>()

    /** Active lock holders on this device (songs this tablet has acquired). */
    private val activeLocks = mutableSetOf<String>()

    /** Override the sync scenario for a specific song. Persists until [clearOverrides]. */
    fun setScenario(songId: String, scenario: SyncScenario) {
        overrides[songId] = scenario
    }

    /**
     * Simulate another client holding the lock for [songId].
     * Calling [requestLock] for this song will return [LockResult.LockedBy(owner)].
     */
    fun setLocked(songId: String, owner: String) {
        lockOverrides[songId] = owner
    }

    /** Remove all per-song overrides; every song reverts to [SyncScenario.SYNCED] / unlocked. */
    fun clearOverrides() {
        overrides.clear()
        lockOverrides.clear()
        activeLocks.clear()
    }

    /** Return the current scenario for [songId] (defaults to SYNCED). */
    fun scenarioFor(songId: String): SyncScenario = overrides[songId] ?: SyncScenario.SYNCED

    /**
     * Request an exclusive edit lock for [songId].
     *
     * - If a lock override is set via [setLocked], returns [LockResult.LockedBy].
     * - If this tablet already holds the lock (from a prior call), returns [LockResult.Acquired].
     * - Otherwise acquires the lock and returns [LockResult.Acquired].
     */
    suspend fun requestLock(songId: String): LockResult {
        val existingOwner = lockOverrides[songId]
        if (existingOwner != null) return LockResult.LockedBy(existingOwner)
        activeLocks.add(songId)
        return LockResult.Acquired
    }

    /**
     * Release the edit lock for [songId] (called when the editor is closed).
     * No-op if this device doesn't hold the lock.
     */
    suspend fun releaseLock(songId: String) {
        activeLocks.remove(songId)
    }

    override suspend fun getRemoteHash(songId: String): RemoteHashResponse {
        val now = System.currentTimeMillis()
        return when (overrides[songId] ?: SyncScenario.SYNCED) {
            SyncScenario.SYNCED -> RemoteHashResponse(
                songId = songId,
                remoteHash = null,           // null → "same as local" in checkSyncStatus
                serverUpdatedAt = null
            )
            SyncScenario.REMOTE_AHEAD -> RemoteHashResponse(
                songId = songId,
                remoteHash = "fake_remote_ahead_$songId",
                serverUpdatedAt = now
            )
            SyncScenario.CONFLICT -> RemoteHashResponse(
                songId = songId,
                remoteHash = "fake_conflict_$songId",
                serverUpdatedAt = now
            )
        }
    }
}
