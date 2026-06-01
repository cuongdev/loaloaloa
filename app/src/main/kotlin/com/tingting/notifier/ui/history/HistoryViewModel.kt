package com.tingting.notifier.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingting.notifier.data.model.TransactionRecord
import com.tingting.notifier.data.repository.TransactionRepository
import com.tingting.notifier.ui.util.DateLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Income/outgoing filter for the chips. */
enum class DirectionFilter { ALL, INCOME, OUTGOING }

/** A date-grouped section of history rows (header + rows). */
data class HistorySection(
    val header: String,
    val rows: List<HistoryRow>,
)

/** A single history row, ready to render. */
data class HistoryRow(
    val id: Long,
    val bankName: String,
    val timeLabel: String,
    val memo: String,
    val amount: Long,
    val isIncome: Boolean,
    val record: TransactionRecord,
)

data class HistoryUiState(
    val direction: DirectionFilter = DirectionFilter.ALL,
    val bankFilter: String? = null, // appId, null = all
    val availableBanks: List<BankOption> = emptyList(),
    val incomeTotal: Long = 0,
    val outgoingTotal: Long = 0,
    val sections: List<HistorySection> = emptyList(),
    val isEmpty: Boolean = true,
)

/** A selectable bank in the filter dropdown (appId + display name). */
data class BankOption(val appId: String, val displayName: String)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    private val direction = MutableStateFlow(DirectionFilter.ALL)
    private val bankFilter = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        transactions.observeRecords(),
        direction,
        bankFilter,
    ) { records, dir, bank ->
        val today = LocalDate.now(clock.withZone(zone))
        val filtered = records.filter { rec ->
            val t = rec.transaction
            val dirOk = when (dir) {
                DirectionFilter.ALL -> true
                DirectionFilter.INCOME -> t.isIncome
                DirectionFilter.OUTGOING -> !t.isIncome
            }
            val bankOk = bank == null || t.appId == bank
            dirOk && bankOk
        }

        val availableBanks = records
            .map { BankOption(it.transaction.appId, it.transaction.bankName) }
            .distinctBy { it.appId }
            .sortedBy { it.displayName }

        HistoryUiState(
            direction = dir,
            bankFilter = bank,
            availableBanks = availableBanks,
            incomeTotal = filtered.filter { it.transaction.isIncome }.sumOf { it.transaction.amount },
            outgoingTotal = filtered.filter { !it.transaction.isIncome }.sumOf { it.transaction.amount },
            sections = groupByDate(filtered, today),
            isEmpty = filtered.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private fun groupByDate(records: List<TransactionRecord>, today: LocalDate): List<HistorySection> =
        records
            .groupBy { DateLabels.dateOf(it.transaction.timestamp, zone) }
            .toSortedMap(compareByDescending { it }) // newest day first
            .map { (date, recs) ->
                HistorySection(
                    header = DateLabels.groupLabel(date, today),
                    rows = recs.sortedByDescending { it.transaction.timestamp }.map { it.toRow() },
                )
            }

    private fun TransactionRecord.toRow(): HistoryRow = HistoryRow(
        id = id,
        bankName = transaction.bankName,
        timeLabel = DateLabels.timeLabel(transaction.timestamp, zone),
        memo = transaction.rawText,
        amount = transaction.amount,
        isIncome = transaction.isIncome,
        record = this,
    )

    fun setDirection(filter: DirectionFilter) { direction.value = filter }

    fun setBankFilter(appId: String?) { bankFilter.value = appId }

    /** Delete one row; returns the deleted record so the UI can offer Snackbar undo. */
    fun delete(record: TransactionRecord) {
        viewModelScope.launch { transactions.delete(record.id) }
    }

    /** Re-insert a previously deleted record (Snackbar "Hoàn tác"). */
    fun undoDelete(record: TransactionRecord) {
        viewModelScope.launch { transactions.addTransaction(record.transaction) }
    }

    fun deleteAll() {
        viewModelScope.launch { transactions.deleteAll() }
    }
}
