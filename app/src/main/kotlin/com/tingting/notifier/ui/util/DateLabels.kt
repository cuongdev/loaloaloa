package com.tingting.notifier.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure date/time labelling for history grouping (spec §8). All functions take the
 * [ZoneId] and a reference "today" so they are deterministic and unit-testable.
 */
object DateLabels {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale("vi"))

    /** Local date for an epoch-millis timestamp in [zone]. */
    fun dateOf(timestamp: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    /** "HH:mm" of a timestamp in [zone]. */
    fun timeLabel(timestamp: Long, zone: ZoneId): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone).format(timeFmt)

    /**
     * Group header for [date] relative to [today]:
     *   today → "Hôm nay", yesterday → "Hôm qua", else "d 'thg' M" (e.g. "30 thg 5").
     */
    fun groupLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Hôm nay"
        today.minusDays(1) -> "Hôm qua"
        else -> "${date.dayOfMonth} thg ${date.monthValue}"
    }

    /** Short weekday abbreviation for the report chart: T2..T7, CN. */
    fun weekdayShort(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "T2"
        2 -> "T3"
        3 -> "T4"
        4 -> "T5"
        5 -> "T6"
        6 -> "T7"
        else -> "CN"
    }
}
