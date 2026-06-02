package com.loaloaloa.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loaloaloa.data.repository.TransactionRepository
import com.loaloaloa.ui.util.TimeRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    private val period = MutableStateFlow(ReportPeriod.TODAY)

    /** One-shot CSV export payloads; the screen writes the file and opens the share sheet. */
    private val exportChannel = Channel<CsvExportData>(Channel.BUFFERED)
    val exports: Flow<CsvExportData> = exportChannel.receiveAsFlow()

    /** Switch the period the hero, period chart, average ticket and per-bank split follow. */
    fun selectPeriod(value: ReportPeriod) {
        period.value = value
    }

    /** Build a CSV of every transaction in the selected period and hand it to the screen to share. */
    fun exportCsv() {
        viewModelScope.launch {
            val today = today()
            val selected = period.value
            val range = when (selected) {
                ReportPeriod.TODAY -> TimeRanges.dayRange(today, zone)
                ReportPeriod.WEEK -> TimeRanges.weekRange(today, zone)
                ReportPeriod.MONTH -> TimeRanges.monthRange(today, zone)
            }
            val rows = transactions.filter(null, null, range.first, range.last).first()
            exportChannel.send(
                CsvExportData(
                    fileName = CsvExport.fileName(selected, today),
                    content = CsvExport.build(rows, zone),
                    rowCount = rows.size,
                ),
            )
        }
    }

    // Six-month window computed once; everything but the all-time records derives from it.
    private val window = TimeRanges.reportWindowRange(today(), zone)

    val uiState: StateFlow<ReportUiState> = combine(
        period,
        transactions.observeIncomeBetween(window.first, window.last),
        transactions.maxIncomeAmount(),
        transactions.bestIncomeDay(),
    ) { selected, records, maxIncome, bestDay ->
        ReportMetrics.compute(
            windowIncome = records.map { it.transaction },
            period = selected,
            today = today(),
            zone = zone,
            maxIncomeAllTime = maxIncome,
            bestDay = bestDay,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zone))
}
