package com.encore.core.data.sync

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

    /** Override the sync scenario for a specific song. Persists until [clearOverrides]. */
    fun setScenario(songId: String, scenario: SyncScenario) {
        overrides[songId] = scenario
    }

    /** Remove all per-song overrides; every song reverts to [SyncScenario.SYNCED]. */
    fun clearOverrides() = overrides.clear()

    /** Return the current scenario for [songId] (defaults to SYNCED). */
    fun scenarioFor(songId: String): SyncScenario = overrides[songId] ?: SyncScenario.SYNCED

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
