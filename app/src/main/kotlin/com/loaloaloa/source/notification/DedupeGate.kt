package com.loaloaloa.source.notification

import androidx.annotation.VisibleForTesting

/**
 * Time-windowed de-duplication for detected events. Banks frequently post the same
 * balance-change notification twice in quick succession; this collapses repeats of
 * the same [key] seen within [windowMillis].
 *
 * Pure with respect to time: the caller supplies `nowMillis`, so this is fully
 * unit-testable without the system clock. Not thread-safe; the single-threaded
 * notification callback is the only caller.
 *
 * As a long-lived `@Singleton`, the gate would otherwise accumulate one entry per
 * distinct key forever. Each call first sweeps entries older than [windowMillis] —
 * they can never be duplicates again — so the map stays bounded by the number of
 * keys seen within any single window.
 */
class DedupeGate(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    private val lastSeen = HashMap<String, Long>()

    /**
     * @return true if [key] was last recorded strictly within [windowMillis] of
     *   [nowMillis] (a duplicate to drop). Otherwise records [nowMillis] for [key]
     *   and returns false. Stale entries (now - stored >= window) are evicted first.
     */
    fun isDuplicate(key: String, nowMillis: Long): Boolean {
        evictStale(nowMillis)
        val previous = lastSeen[key]
        if (previous != null && nowMillis - previous < windowMillis) {
            return true
        }
        lastSeen[key] = nowMillis
        return false
    }

    /** Drop every key whose last sighting is outside the window — it can never dedupe again. */
    private fun evictStale(nowMillis: Long) {
        val iterator = lastSeen.values.iterator()
        while (iterator.hasNext()) {
            if (nowMillis - iterator.next() >= windowMillis) iterator.remove()
        }
    }

    /** Number of keys currently retained. Exposed for tests to assert the map stays bounded. */
    @VisibleForTesting
    fun trackedKeyCount(): Int = lastSeen.size

    companion object {
        const val DEFAULT_WINDOW_MILLIS: Long = 500
    }
}
