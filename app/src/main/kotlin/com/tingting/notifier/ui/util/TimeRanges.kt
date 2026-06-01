package com.tingting.notifier.ui.util

import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure helpers turning a reference "today" + [ZoneId] into the epoch-millis ranges
 * the [com.tingting.notifier.data.repository.TransactionRepository] queries expect.
 * Kept pure (no `System.currentTimeMillis`) so ViewModels can be tested with a
 * fixed clock.
 */
object TimeRanges {

    /** Inclusive [from, to] epoch-millis covering all of [day] in [zone]. */
    fun dayRange(day: LocalDate, zone: ZoneId): LongRange {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }

    /** Inclusive range covering the last [days] days ending at the end of [today]. */
    fun lastDaysRange(today: LocalDate, days: Int, zone: ZoneId): LongRange {
        val start = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }
}
