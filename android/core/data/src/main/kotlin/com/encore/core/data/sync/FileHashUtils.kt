package com.encore.core.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Utility for generating deterministic MD5 hashes of song markdown content.
 *
 * Hashing is used to detect whether markdownBody has changed since the last
 * successful sync, enabling the three-way conflict detection:
 *   - Local hash == lastSyncedHash && remote hash == lastSyncedHash → UpToDate
 *   - Local dirty, remote unchanged → LocalAhead
 *   - Remote changed, local clean → RemoteAhead
 *   - Both changed → Conflict
 *
 * Trailing whitespace is trimmed before hashing to prevent false conflicts
 * caused by editors that add/remove trailing newlines on save.
 */
object FileHashUtils {

    /**
     * Generate an MD5 hex string for the given markdown body.
     *
     * Always runs on [Dispatchers.IO] — never call from the main thread.
     *
     * @param body Raw markdownBody string from [SongEntity]
     * @return 32-character lowercase hex MD5 string
     */
    suspend fun hashMarkdownBody(body: String): String = withContext(Dispatchers.IO) {
        val normalised = body.trimEnd()
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(normalised.toByteArray(Charsets.UTF_8))
        bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Synchronous variant for use inside already-dispatched IO coroutines or tests.
     * Prefer [hashMarkdownBody] in production code.
     */
    fun hashMarkdownBodySync(body: String): String {
        val normalised = body.trimEnd()
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(normalised.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
