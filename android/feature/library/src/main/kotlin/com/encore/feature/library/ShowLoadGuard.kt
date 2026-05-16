package com.encore.feature.library

/**
 * Singleton flag that suppresses the numbered-set background sync
 * (checkAndApplyWebSetChanges) for 60 seconds after a show is explicitly
 * loaded on the tablet.
 *
 * Without this guard the sync races the show-load coroutine: it downloads
 * the old set_N.json files (still marked source:"web") and overwrites the
 * freshly loaded show data before stampAllSetsAsTablet can mark them as
 * source:"tablet".
 */
object ShowLoadGuard {
    @Volatile private var loadedAt: Long = 0L
    private const val SUPPRESS_MS = 60_000L

    /** Call this before starting a show load. */
    fun markLoaded() {
        loadedAt = System.currentTimeMillis()
    }

    /** Returns true if a show was loaded within the last 60 seconds. */
    fun isSuppressed(): Boolean =
        System.currentTimeMillis() - loadedAt < SUPPRESS_MS
}
