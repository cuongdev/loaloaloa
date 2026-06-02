package com.loaloaloa.ui.util

import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure helpers turning a reference "today" + [ZoneId] into the epoch-millis ranges
 * the [com.loaloaloa.data.repository.TransactionRepository] queries expect.
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

    /** Inclusive epoch-millis range covering [start]..[endInclusive] whole days in [zone]. */
    fun rangeOfDays(start: LocalDate, endInclusive: LocalDate, zone: ZoneId): LongRange {
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return from..to
    }

    /** Inclusive range covering the calendar week (Monday..Sunday) containing [today]. */
    fun weekRange(today: LocalDate, zone: ZoneId): LongRange {
        val monday = mondayOf(today)
        return rangeOfDays(monday, monday.plusDays(6), zone)
    }

    /** Inclusive range covering the whole calendar month containing [today]. */
    fun monthRange(today: LocalDate, zone: ZoneId): LongRange {
        val first = today.withDayOfMonth(1)
        return rangeOfDays(first, first.plusMonths(1).minusDays(1), zone)
    }

    /**
     * The window the report loads into memory: from the first day of the month five
     * months before [today], through the end of [today]. Bounds the six calendar
     * months the monthly trend needs (and every shorter period derives from it).
     */
    fun reportWindowRange(today: LocalDate, zone: ZoneId): LongRange {
        val start = today.withDayOfMonth(1).minusMonths(5)
        return rangeOfDays(start, today, zone)
    }

    /** Monday of the calendar week containing [date] (weeks start Monday). */
    fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
}
