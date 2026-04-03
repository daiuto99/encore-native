package com.encore.core.data.sync

/**
 * State for the Sync Progress HUD shown in the Performance Context Bar.
 *
 * When non-null, the live clock in the context bar is replaced with sync feedback.
 * Returns to clock display when this is null (sync idle or complete).
 */
sealed class SyncHudState {
    /** Sync is actively running — show "Syncing N/Total" + spinner. */
    data class InProgress(val current: Int, val total: Int) : SyncHudState()

    /** Sync finished — show a brief "✓ Synced" confirmation before clearing. */
    object Complete : SyncHudState()
}
