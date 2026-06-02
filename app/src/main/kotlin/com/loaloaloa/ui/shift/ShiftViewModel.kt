package com.loaloaloa.ui.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loaloaloa.data.repository.TransactionRepository
import com.loaloaloa.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the in-app "Chốt ca" (shift handover) screen. A shift is just a start timestamp persisted
 * in settings ([UserSettings.shiftStartedAt]); while it is open the running totals are recomputed
 * from every transaction since that instant. Closing the shift emits a shareable summary and clears
 * the start timestamp. The clock is the only impurity — the aggregation/summary are pure & tested.
 */
@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val settings: UserSettingsRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    /** One-shot handover messages; the screen opens the share sheet with the text. */
    private val shareChannel = Channel<String>(Channel.BUFFERED)
    val shares: Flow<String> = shareChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ShiftUiState> = settings.settings
        .map { it.shiftStartedAt }
        .distinctUntilChanged()
        .flatMapLatest { startedAt ->
            if (startedAt == null) {
                flowOf(ShiftUiState(active = false))
            } else {
                transactions.filter(null, null, startedAt, Long.MAX_VALUE)
                    .map { rows -> ShiftSummary.aggregate(startedAt, rows) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShiftUiState())

    /** Begin (or re-anchor) a shift to count transactions from [atMillis] onward. */
    fun startShift(atMillis: Long) {
        viewModelScope.launch { settings.setShiftStartedAt(atMillis) }
    }

    /** Count from this instant — the usual "I'm clocking in now" case. */
    fun startShiftNow() = startShift(clock.millis())

    /**
     * Count from 00:00 today. Covers the common handover case where no shift was opened in the
     * morning but the person closing out still wants the running total "từ sáng đến giờ".
     */
    fun startShiftFromTodayStart() =
        startShift(LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant().toEpochMilli())

    /** Count from [hour]:[minute] today — e.g. the real time the shift began (06:00, 14:00…). */
    fun startShiftAtToday(hour: Int, minute: Int) =
        startShift(
            LocalDate.now(clock.withZone(zone))
                .atTime(LocalTime.of(hour, minute))
                .atZone(zone)
                .toInstant()
                .toEpochMilli(),
        )

    /** Build the handover summary, hand it to the screen to share, then end the shift. */
    fun closeShift() {
        viewModelScope.launch {
            val startedAt = settings.settings.first().shiftStartedAt ?: return@launch
            val rows = transactions.filter(null, null, startedAt, Long.MAX_VALUE).first()
            shareChannel.send(ShiftSummary.build(startedAt, clock.millis(), rows, zone))
            settings.setShiftStartedAt(null)
        }
    }

    /** End the shift without sharing anything. */
    fun cancelShift() {
        viewModelScope.launch { settings.setShiftStartedAt(null) }
    }
}
