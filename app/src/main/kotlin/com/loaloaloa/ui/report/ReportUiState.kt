package com.loaloaloa.ui.report

/** Period the dashboard hero, period chart, average ticket and per-bank split follow. */
enum class ReportPeriod { TODAY, WEEK, MONTH }

/** One bar in any of the report charts (period chart and 6-month trend). */
data class ChartBar(
    val label: String,    // weekday (T2..CN), day-of-month, or month ("thg 6"); "" = no label
    val value: Long,
    val highlight: Boolean,
)

/** Direction/state of a growth comparison, used to pick arrow + color. */
enum class GrowthStatus { UP, DOWN, FLAT, NEW, NONE }

/**
 * One "tăng trưởng" card: the current-span income plus how it compares to the same
 * span of the previous period. [deltaPercent] is null when [status] is NEW/NONE.
 */
data class GrowthCard(
    val label: String,        // "vs hôm qua" / "vs tuần rồi" / "vs tháng rồi"
    val currentAmount: Long,
    val deltaPercent: Int?,
    val status: GrowthStatus,
)

/** Income summed into one hour-of-day bucket (0..23) for the "giờ cao điểm" chart. */
data class HourBucket(val hour: Int, val income: Long)

/** Income summed into one weekday bucket (label T2..CN) for "ngày bận nhất". */
data class WeekdayBucket(val label: String, val income: Long)

/** One bank/wallet's share of the selected period's income. */
data class BankSlice(val bankName: String, val income: Long, val percent: Int)

/** All-time records (kỷ lục). [bestDayLabel] null when there is no income. */
data class RecordsInfo(
    val bestDayLabel: String?,
    val bestDayAmount: Long,
    val biggestTransaction: Long,
)

/** A CSV export ready to be written to a file and shared. [rowCount] excludes the header. */
data class CsvExportData(val fileName: String, val content: String, val rowCount: Int)

/** Everything the Report screen renders. All fields are precomputed by [ReportMetrics]. */
data class ReportUiState(
    val period: ReportPeriod = ReportPeriod.TODAY,
    val periodTotal: Long = 0,
    val periodCount: Int = 0,
    val averageTicket: Long = 0,
    val growth: List<GrowthCard> = emptyList(),
    val periodBars: List<ChartBar> = emptyList(),
    val monthlyTrend: List<ChartBar> = emptyList(),
    val peakHours: List<HourBucket> = emptyList(),
    val peakHourHeadline: String? = null,
    val busiestWeekday: List<WeekdayBucket> = emptyList(),
    val busiestWeekdayHeadline: String? = null,
    val perBank: List<BankSlice> = emptyList(),
    val records: RecordsInfo = RecordsInfo(null, 0, 0),
    val hasData: Boolean = false,
)
