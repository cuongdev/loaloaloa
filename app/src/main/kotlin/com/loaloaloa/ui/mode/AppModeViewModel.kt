package com.loaloaloa.ui.mode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loaloaloa.data.model.AppMode
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.ui.navigation.migrateAppMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Root shell state: Loading until the one-time migration has run, then the resolved [AppMode]. */
sealed interface RootModeState {
    data object Loading : RootModeState
    data class Ready(val appMode: AppMode) : RootModeState
}

@HiltViewModel
class AppModeViewModel @Inject constructor(
    private val repo: UserSettingsRepository,
) : ViewModel() {

    private val migrated = MutableStateFlow(false)

    val state: StateFlow<RootModeState> =
        combine(repo.settings, migrated) { s, done ->
            if (!done) RootModeState.Loading else RootModeState.Ready(s.appMode)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootModeState.Loading)

    /**
     * Run the one-time mode migration if this install predates [AppMode] (settingsVersion < 1),
     * then mark migration done so the root host stops showing the splash. Idempotent.
     */
    fun ensureMigrated(notifAccessGranted: Boolean) {
        viewModelScope.launch {
            val s = repo.settings.first()
            if (s.settingsVersion < 1) {
                repo.updateAppMode(migrateAppMode(s.relayRole, notifAccessGranted))
            }
            migrated.value = true
        }
    }

    fun chooseShop() = viewModelScope.launch { repo.updateAppMode(AppMode.SHOP_OWNER) }
    fun chooseStaff() = viewModelScope.launch { repo.updateAppMode(AppMode.STAFF) }
    fun toPicker() = viewModelScope.launch { repo.updateAppMode(AppMode.UNSET) }
}
