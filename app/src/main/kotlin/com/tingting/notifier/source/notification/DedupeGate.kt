package com.tingting.notifier.source.notification

import javax.inject.Inject

/**
 * Time-windowed de-duplication for detected events. Banks frequently post the same
 * balance-change notification twice in quick succession; this collapses repeats of
 * the same [key] seen within [windowMillis].
 *
 * Pure with respect to time: the caller supplies `nowMillis`, so this is fully
 * unit-testable without the system clock. Not thread-safe; the single-threaded
 * notification callback is the only caller.
 */
class DedupeGate @Inject constructor(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    private val lastSeen = HashMap<String, Long>()

    /**
     * @return true if [key] was last recorded strictly within [windowMillis] of
     *   [nowMillis] (a duplicate to drop). Otherwise records [nowMillis] for [key]
     *   and returns false.
     */
    fun isDuplicate(key: String, nowMillis: Long): Boolean {
        val previous = lastSeen[key]
        if (previous != null && nowMillis - previous < windowMillis) {
            return true
        }
        lastSeen[key] = nowMillis
        return false
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS: Long = 500
    }
}
