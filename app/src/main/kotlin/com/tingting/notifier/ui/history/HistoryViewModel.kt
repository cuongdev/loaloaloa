package com.tingting.notifier.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingting.notifier.data.model.TransactionRecord
import com.tingting.notifier.data.repository.TransactionRepository
import com.tingting.notifier.ui.util.DateLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Income/outgoing filter for the chips. */
enum class DirectionFilter { ALL, INCOME, OUTGOING }

/** Maps the chip filter to the nullable isIncome facet of [TransactionRepository.searchRecords]. */
private fun DirectionFilter.toIsIncome(): Boolean? = when (this) {
    DirectionFilter.ALL -> null
    DirectionFilter.INCOME -> true
    DirectionFilter.OUTGOING -> false
}

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
    val searchQuery: String = "",
    val dateRange: HistoryViewModel.DateRange = HistoryViewModel.DateRange.ALL,
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

    /** Inclusive epoch-millis window; [ALL] means no date restriction. */
    data class DateRange(val from: Long, val to: Long) {
        val isAll: Boolean get() = this == ALL

        companion object {
            val ALL = DateRange(0L, Long.MAX_VALUE)
        }
    }

    private val direction = MutableStateFlow(DirectionFilter.ALL)
    private val bankFilter = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val dateRange = MutableStateFlow(DateRange.ALL)

    /** The current search inputs, threaded alongside the searched rows so the combine stays typed. */
    private data class SearchedState(
        val direction: DirectionFilter,
        val query: String,
        val range: DateRange,
        val records: List<TransactionRecord>,
    )

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searched =
        combine(
            searchQuery.debounce(300).distinctUntilChanged(),
            direction,
            dateRange,
        ) { q, dir, range -> Triple(q, dir, range) }
            .flatMapLatest { (q, dir, range) ->
                transactions.searchRecords(q, dir.toIsIncome(), range.from, range.to)
                    .map { SearchedState(dir, q, range, it) }
            }

    val uiState: StateFlow<HistoryUiState> = combine(
        searched,
        transactions.observeRecords(),
        bankFilter,
    ) { searchedState, allRecords, bank ->
        val today = LocalDate.now(clock.withZone(zone))

        // The DAO search query has no appId facet; apply the bank chip as an in-VM predicate.
        val filtered = searchedState.records.filter { bank == null || it.transaction.appId == bank }

        // Bank chip options come from the full (unfiltered) history so the list stays stable.
        val availableBanks = allRecords
            .map { BankOption(it.transaction.appId, it.transaction.bankName) }
            .distinctBy { it.appId }
            .sortedBy { it.displayName }

        HistoryUiState(
            direction = searchedState.direction,
            bankFilter = bank,
            searchQuery = searchedState.query,
            dateRange = searchedState.range,
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

    /** Update the keyword search (debounced ~300ms before it re-queries). */
    fun setSearchQuery(query: String) { searchQuery.value = query }

    /**
     * Restrict to an inclusive day range. [fromMillis]/[toMillis] are normalized to the
     * start/end of their local day (in the injected [zone]); null/null clears to [DateRange.ALL].
     */
    fun setDateRange(fromMillis: Long?, toMillis: Long?) {
        if (fromMillis == null && toMillis == null) {
            dateRange.value = DateRange.ALL
            return
        }
        val from = fromMillis ?: 0L
        val to = toMillis ?: from
        val startOfFrom = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfTo = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        dateRange.value = DateRange(startOfFrom, endOfTo)
    }

    /** Clear the date filter back to "all days". */
    fun clearDateRange() { dateRange.value = DateRange.ALL }

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
