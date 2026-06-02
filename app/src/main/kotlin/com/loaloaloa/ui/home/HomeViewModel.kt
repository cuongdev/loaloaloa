package com.loaloaloa.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loaloaloa.data.model.TransactionRecord
import com.loaloaloa.data.repository.TransactionRepository
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.ui.util.TimeRanges
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Live permission state pushed in from the Activity (checks need a Context). */
data class PermissionState(
    val notificationAccessGranted: Boolean = false,
    val batteryOptimized: Boolean = true, // true = NOT yet exempt (warning shown)
)

data class HomeUiState(
    val serviceEnabled: Boolean = false,
    val todayIncome: Long = 0,
    val todayCount: Int = 0,
    val recent: List<TransactionRecord> = emptyList(),
    val notificationAccessGranted: Boolean = false,
    val batteryWarning: Boolean = false,
    val shiftActive: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val settings: UserSettingsRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) : ViewModel() {

    private val permissions = MutableStateFlow(PermissionState())

    val uiState: StateFlow<HomeUiState> = combine(
        settings.settings,
        transactions.observeRecords(),
        permissions,
    ) { userSettings, records, perms ->
        val today = LocalDate.now(clock.withZone(zone))
        val range = TimeRanges.dayRange(today, zone)
        val todays = records.filter { it.transaction.timestamp in range }
        HomeUiState(
            serviceEnabled = userSettings.enableService,
            todayIncome = todays.filter { it.transaction.isIncome }.sumOf { it.transaction.amount },
            todayCount = todays.size,
            recent = records.take(5),
            notificationAccessGranted = perms.notificationAccessGranted,
            batteryWarning = perms.batteryOptimized,
            shiftActive = userSettings.shiftStartedAt != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Activity calls this on launch/resume after checking the helpers. */
    fun updatePermissions(notificationAccessGranted: Boolean, isIgnoringBatteryOptimizations: Boolean) {
        permissions.update {
            it.copy(
                notificationAccessGranted = notificationAccessGranted,
                batteryOptimized = !isIgnoringBatteryOptimizations,
            )
        }
    }

    /** Toggle the master service gate ([com.loaloaloa.data.model.UserSettings.enableService]). */
    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.updateEnableService(enabled) }
    }

    /** Save (or clear) the free-text note on a recent transaction; mirrors the History screen. */
    fun updateNote(id: Long, note: String) {
        viewModelScope.launch { transactions.updateNote(id, note) }
    }
}
