package com.loaloaloa.ui.report

import com.loaloaloa.data.model.BestDay
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.ui.util.DateLabels
import com.loaloaloa.ui.util.TimeRanges
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure aggregation for the Report dashboard. Given the income rows in the six-month
 * report window plus the two all-time aggregates, it produces the whole
 * [ReportUiState]. No Android types, no clock — deterministic for a fixed
 * `today`/`zone`, so it is trivially testable.
 *
 * Everything here is **income-only**; rows are filtered defensively even though the
 * repository query already excludes outgoing.
 */
object ReportMetrics {

    private const val PATTERN_DAYS = 30
    private const val TREND_MONTHS = 6

    fun compute(
        windowIncome: List<TransactionModel>,
        period: ReportPeriod,
        today: LocalDate,
        zone: ZoneId,
        maxIncomeAllTime: Long,
        bestDay: BestDay?,
    ): ReportUiState {
        val income = windowIncome.filter { it.isIncome }

        // --- selected period -------------------------------------------------
        val periodRange = when (period) {
            ReportPeriod.TODAY -> TimeRanges.dayRange(today, zone)
            ReportPeriod.WEEK -> TimeRanges.weekRange(today, zone)
            ReportPeriod.MONTH -> TimeRanges.monthRange(today, zone)
        }
        val periodRecords = income.filter { it.timestamp in periodRange }
        val periodTotal = periodRecords.sumOf { it.amount }
        val periodCount = periodRecords.size
        val averageTicket = if (periodCount > 0) periodTotal / periodCount else 0L

        // --- growth (always three fixed, to-date comparisons) ----------------
        val growth = listOf(
            growthCard("vs hôm qua", income, today, zone, GrowthSpan.DAY),
            growthCard("vs tuần rồi", income, today, zone, GrowthSpan.WEEK),
            growthCard("vs tháng rồi", income, today, zone, GrowthSpan.MONTH),
        )

        // --- charts ----------------------------------------------------------
        val periodBars = periodBars(period, income, today, zone)
        val monthlyTrend = (TREND_MONTHS - 1 downTo 0).map { back ->
            val month = today.minusMonths(back.toLong())
            ChartBar("thg ${month.monthValue}", sumIn(income, TimeRanges.monthRange(month, zone)), back == 0)
        }

        // --- patterns over the last 30 days ----------------------------------
        val patternRange = TimeRanges.rangeOfDays(today.minusDays((PATTERN_DAYS - 1).toLong()), today, zone)
        val patternRecords = income.filter { it.timestamp in patternRange }

        val peakHours = (0..23).map { h ->
            HourBucket(h, patternRecords.filter { hourOf(it.timestamp, zone) == h }.sumOf { it.amount })
        }
        val peakHourHeadline = peakHours.filter { it.income > 0 }.maxByOrNull { it.income }?.let { top ->
            "Đông khách nhất: ${top.hour}–${(top.hour + 1) % 24}h"
        }

        val byWeekday = patternRecords
            .groupBy { dateOf(it.timestamp, zone).dayOfWeek.value }
            .mapValues { (_, recs) -> recs.sumOf { it.amount } }
        val busiestWeekday = (1..7).map { dow -> WeekdayBucket(weekdayShort(dow), byWeekday[dow] ?: 0L) }
        val busiestWeekdayHeadline = (1..7)
            .map { it to (byWeekday[it] ?: 0L) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.let { "Bận nhất: ${weekdayFull(it.first)}" }

        // --- per-bank split over the selected period -------------------------
        val perBank = perBank(periodRecords, periodTotal)

        // --- all-time records ------------------------------------------------
        val records = records(bestDay, maxIncomeAllTime, today)

        return ReportUiState(
            period = period,
            periodTotal = periodTotal,
            periodCount = periodCount,
            averageTicket = averageTicket,
            growth = growth,
            periodBars = periodBars,
            monthlyTrend = monthlyTrend,
            peakHours = peakHours,
            peakHourHeadline = peakHourHeadline,
            busiestWeekday = busiestWeekday,
            busiestWeekdayHeadline = busiestWeekdayHeadline,
            perBank = perBank,
            records = records,
            hasData = income.isNotEmpty() || maxIncomeAllTime > 0,
        )
    }

    // --- growth ------------------------------------------------------------------

    private enum class GrowthSpan { DAY, WEEK, MONTH }

    private fun growthCard(
        label: String,
        income: List<TransactionModel>,
        today: LocalDate,
        zone: ZoneId,
        span: GrowthSpan,
    ): GrowthCard {
        val (curRange, prevRange) = spans(span, today, zone)
        val cur = sumIn(income, curRange)
        val prev = sumIn(income, prevRange)
        val (pct, status) = growthOf(cur, prev)
        return GrowthCard(label, cur, pct, status)
    }

    /** Current "to-date" range paired with the same-length span of the previous period. */
    private fun spans(span: GrowthSpan, today: LocalDate, zone: ZoneId): Pair<LongRange, LongRange> = when (span) {
        GrowthSpan.DAY -> TimeRanges.dayRange(today, zone) to TimeRanges.dayRange(today.minusDays(1), zone)
        GrowthSpan.WEEK -> {
            val weekStart = TimeRanges.mondayOf(today)
            val daysIn = (today.dayOfWeek.value - 1).toLong() // 0..6
            val prevStart = weekStart.minusWeeks(1)
            TimeRanges.rangeOfDays(weekStart, today, zone) to
                TimeRanges.rangeOfDays(prevStart, prevStart.plusDays(daysIn), zone)
        }
        GrowthSpan.MONTH -> {
            val monthStart = today.withDayOfMonth(1)
            val lastMonthStart = monthStart.minusMonths(1)
            val prevEndDay = minOf(today.dayOfMonth, lastMonthStart.lengthOfMonth())
            TimeRanges.rangeOfDays(monthStart, today, zone) to
                TimeRanges.rangeOfDays(lastMonthStart, lastMonthStart.withDayOfMonth(prevEndDay), zone)
        }
    }

    private fun growthOf(cur: Long, prev: Long): Pair<Int?, GrowthStatus> = when {
        prev == 0L && cur == 0L -> null to GrowthStatus.NONE
        prev == 0L -> null to GrowthStatus.NEW
        cur == 0L -> -100 to GrowthStatus.DOWN
        else -> {
            val pct = Math.round((cur - prev) * 100.0 / prev).toInt()
            val status = when {
                pct > 0 -> GrowthStatus.UP
                pct < 0 -> GrowthStatus.DOWN
                else -> GrowthStatus.FLAT
            }
            pct to status
        }
    }

    // --- charts ------------------------------------------------------------------

    private fun periodBars(
        period: ReportPeriod,
        income: List<TransactionModel>,
        today: LocalDate,
        zone: ZoneId,
    ): List<ChartBar> = when (period) {
        ReportPeriod.TODAY -> (0 until 7).map { offset ->
            val day = today.minusDays((6 - offset).toLong())
            ChartBar(weekdayShort(day.dayOfWeek.value), sumIn(income, TimeRanges.dayRange(day, zone)), day == today)
        }
        ReportPeriod.WEEK -> {
            val monday = TimeRanges.mondayOf(today)
            (0 until 7).map { offset ->
                val day = monday.plusDays(offset.toLong())
                ChartBar(weekdayShort(day.dayOfWeek.value), sumIn(income, TimeRanges.dayRange(day, zone)), day == today)
            }
        }
        ReportPeriod.MONTH -> {
            val first = today.withDayOfMonth(1)
            (1..first.lengthOfMonth()).map { d ->
                val day = first.withDayOfMonth(d)
                val label = if (d == 1 || d % 5 == 0) d.toString() else ""
                ChartBar(label, sumIn(income, TimeRanges.dayRange(day, zone)), day == today)
            }
        }
    }

    // --- per-bank ----------------------------------------------------------------

    private fun perBank(periodRecords: List<TransactionModel>, periodTotal: Long): List<BankSlice> {
        if (periodTotal <= 0) return emptyList()
        val grouped = periodRecords.groupBy { it.bankName }
            .mapValues { (_, recs) -> recs.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
        val top = grouped.take(5)
        val rest = grouped.drop(5).sumOf { it.value }
        val slices = top.map { BankSlice(it.key, it.value, percent(it.value, periodTotal)) }.toMutableList()
        if (rest > 0) slices.add(BankSlice("Khác", rest, percent(rest, periodTotal)))
        return slices
    }

    // --- records -----------------------------------------------------------------

    private fun records(bestDay: BestDay?, maxIncomeAllTime: Long, today: LocalDate): RecordsInfo {
        if (bestDay == null) return RecordsInfo(null, 0, maxIncomeAllTime)
        val date = runCatching { LocalDate.parse(bestDay.day) }.getOrNull()
        val label = date?.let { DateLabels.groupLabel(it, today) } ?: bestDay.day
        return RecordsInfo(label, bestDay.total, maxIncomeAllTime)
    }

    // --- helpers -----------------------------------------------------------------

    private fun sumIn(income: List<TransactionModel>, range: LongRange): Long =
        income.filter { it.timestamp in range }.sumOf { it.amount }

    private fun hourOf(ts: Long, zone: ZoneId): Int = Instant.ofEpochMilli(ts).atZone(zone).hour

    private fun dateOf(ts: Long, zone: ZoneId): LocalDate = DateLabels.dateOf(ts, zone)

    private fun percent(part: Long, total: Long): Int =
        if (total <= 0) 0 else Math.round(part * 100.0 / total).toInt()

    private fun weekdayShort(dow: Int): String = when (dow) {
        1 -> "T2"; 2 -> "T3"; 3 -> "T4"; 4 -> "T5"; 5 -> "T6"; 6 -> "T7"; else -> "CN"
    }

    private fun weekdayFull(dow: Int): String = when (dow) {
        1 -> "Thứ 2"; 2 -> "Thứ 3"; 3 -> "Thứ 4"; 4 -> "Thứ 5"; 5 -> "Thứ 6"; 6 -> "Thứ 7"; else -> "Chủ nhật"
    }
}
