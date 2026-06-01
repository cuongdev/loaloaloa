package com.tingting.notifier.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingting.notifier.data.repository.TransactionRepository
import com.tingting.notifier.ui.util.DateLabels
import com.tingting.notifier.ui.util.TimeRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One bar in the 7-day chart. */
data class DayBar(
    val label: String,    // T2..T7, CN
    val income: Long,
    val isToday: Boolean,
)

data class ReportUiState(
    val todayIncome: Long = 0,
    val todayCount: Int = 0,
    val weekTotal: Long = 0,
    val averagePerDay: Long = 0,
    val largestTransaction: Long = 0,
    val bars: List<DayBar> = emptyList(),
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    transactions: TransactionRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    companion object { const val WINDOW_DAYS = 7 }

    val uiState: StateFlow<ReportUiState> = transactions.observeRecords()
        .map { records ->
            val today = LocalDate.now(clock.withZone(zone))
            val weekRange = TimeRanges.lastDaysRange(today, WINDOW_DAYS, zone)

            // income-by-day across the window
            val incomeByDate = records
                .filter { it.transaction.isIncome && it.transaction.timestamp in weekRange }
                .groupBy { DateLabels.dateOf(it.transaction.timestamp, zone) }
                .mapValues { (_, recs) -> recs.sumOf { it.transaction.amount } }

            val bars = (0 until WINDOW_DAYS).map { offset ->
                val day = today.minusDays((WINDOW_DAYS - 1 - offset).toLong())
                DayBar(
                    label = DateLabels.weekdayShort(day),
                    income = incomeByDate[day] ?: 0,
                    isToday = day == today,
                )
            }

            val weekTotal = bars.sumOf { it.income }
            val todayRange = TimeRanges.dayRange(today, zone)
            val todays = records.filter { it.transaction.timestamp in todayRange }

            ReportUiState(
                todayIncome = todays.filter { it.transaction.isIncome }.sumOf { it.transaction.amount },
                todayCount = todays.size,
                weekTotal = weekTotal,
                averagePerDay = weekTotal / WINDOW_DAYS,
                largestTransaction = records
                    .filter { it.transaction.isIncome && it.transaction.timestamp in weekRange }
                    .maxOfOrNull { it.transaction.amount } ?: 0,
                bars = bars,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())
}
