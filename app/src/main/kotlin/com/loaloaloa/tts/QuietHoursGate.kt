package com.loaloaloa.tts

import com.loaloaloa.data.model.QuietHours
import javax.inject.Inject

/**
 * Pure decision of whether the current minute-of-day falls inside the user's quiet
 * window. Quiet hours suppress the spoken announcement only — the caller still
 * persists the transaction. Times are minutes-of-day (0..1439); the window is
 * half-open `[start, end)` and may wrap past midnight when `start > end`.
 */
class QuietHoursGate @Inject constructor() {

    /**
     * @return true when announcements should be silenced at [nowMinutesOfDay].
     *   False when quiet hours are disabled or `start == end` (zero-width window).
     */
    fun isQuiet(nowMinutesOfDay: Int, quietHours: QuietHours): Boolean {
        if (!quietHours.enabled) return false
        val start = quietHours.startMinutes
        val end = quietHours.endMinutes
        if (start == end) return false
        return if (start < end) {
            nowMinutesOfDay in start until end
        } else {
            // Wraps past midnight, e.g. 22:00..07:00 = 1320..420.
            nowMinutesOfDay >= start || nowMinutesOfDay < end
        }
    }
}
