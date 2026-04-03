package com.encore.core.data.sync

/**
 * Contract for the Encore cloud sync API.
 *
 * The real Ktor implementation will be wired here in a future sprint.
 * For now, [FakeSyncProvider] satisfies this interface with manually-toggleable
 * responses so conflict scenarios can be tested without a live server.
 */
interface EncoreApiService {

    /**
     * Fetch the server-side hash for a single song.
     *
     * @param songId UUID of the song
     * @return [RemoteHashResponse] — remoteHash is null if the song doesn't exist on the server
     */
    suspend fun getRemoteHash(songId: String): RemoteHashResponse
}

/**
 * Server response for a single song's current hash state.
 *
 * @param songId      Echo of the requested song UUID
 * @param remoteHash  MD5 of the server's current markdownBody; null = not on server yet
 * @param serverUpdatedAt Unix timestamp (ms) of last server-side modification; null if new
 */
data class RemoteHashResponse(
    val songId: String,
    val remoteHash: String?,
    val serverUpdatedAt: Long?
)
