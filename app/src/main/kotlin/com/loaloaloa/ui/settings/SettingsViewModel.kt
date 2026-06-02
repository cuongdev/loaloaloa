package com.loaloaloa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loaloaloa.data.model.ApiConfig
import com.loaloaloa.data.model.AudioOutput
import com.loaloaloa.data.model.QuietHours
import com.loaloaloa.data.model.SpeakOption
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.source.api.SePayConnectionTester
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of a one-shot SePay "Kiểm tra kết nối" attempt, surfaced to the UI. */
sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val count: Int) : ConnectionTestState
    data class Failure(val message: String) : ConnectionTestState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: UserSettingsRepository,
    private val connectionTester: SePayConnectionTester,
) : ViewModel() {

    val uiState: StateFlow<UserSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _connectionTest = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTest: StateFlow<ConnectionTestState> = _connectionTest.asStateFlow()

    fun setSpeakOption(option: SpeakOption) = launch { repo.updateSpeakOption(option) }
    fun setPlayChime(enabled: Boolean) = launch { repo.updatePlayChime(enabled) }
    fun setRepeat(enabled: Boolean) = launch { repo.updateRepeat(enabled) }
    fun setSpeakShortMessage(enabled: Boolean) = launch { repo.updateSpeakShortMessage(enabled) }
    fun setForceMaxVolume(enabled: Boolean) = launch { repo.updateForceMaxVolume(enabled) }
    fun setSpeakInSilentMode(enabled: Boolean) = launch { repo.updateSpeakInSilentMode(enabled) }
    fun setSpeakDailyTotal(enabled: Boolean) = launch { repo.updateSpeakDailyTotal(enabled) }
    fun setSpeakContent(enabled: Boolean) = launch { repo.updateSpeakContent(enabled) }
    fun setMinAnnounceAmount(amount: Long) = launch { repo.updateMinAnnounceAmount(amount) }
    fun setAudioOutput(output: AudioOutput) = launch { repo.updateAudioOutput(output) }
    fun setEnableAudioFocus(enabled: Boolean) = launch { repo.updateEnableAudioFocus(enabled) }
    fun setQuietHours(quietHours: QuietHours) = launch { repo.setQuietHours(quietHours) }
    fun setExcludedApps(apps: List<String>) = launch { repo.updateExcludedApps(apps) }
    fun setApiConfig(api: ApiConfig) = launch { repo.setApiConfig(api) }

    /**
     * Test connectivity for the SUPPLIED [config] (the values currently in the form,
     * not necessarily persisted). Publishes Testing → Success/Failure on [connectionTest].
     */
    fun testConnection(config: ApiConfig) {
        _connectionTest.value = ConnectionTestState.Testing
        viewModelScope.launch {
            val result = connectionTester.testConnection(config)
            _connectionTest.value = result.fold(
                onSuccess = { ConnectionTestState.Success(it) },
                onFailure = { ConnectionTestState.Failure(it.message ?: "Không xác định") },
            )
        }
    }

    /** Reset the banner after the UI has shown the outcome. */
    fun clearConnectionTest() {
        _connectionTest.value = ConnectionTestState.Idle
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
