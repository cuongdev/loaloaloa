package com.tingting.notifier.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingting.notifier.data.model.ApiConfig
import com.tingting.notifier.data.model.AudioOutput
import com.tingting.notifier.data.model.QuietHours
import com.tingting.notifier.data.model.SpeakOption
import com.tingting.notifier.data.model.UserSettings
import com.tingting.notifier.data.repository.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: UserSettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<UserSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setSpeakOption(option: SpeakOption) = launch { repo.updateSpeakOption(option) }
    fun setPlayChime(enabled: Boolean) = launch { repo.updatePlayChime(enabled) }
    fun setRepeat(enabled: Boolean) = launch { repo.updateRepeat(enabled) }
    fun setSpeakShortMessage(enabled: Boolean) = launch { repo.updateSpeakShortMessage(enabled) }
    fun setForceMaxVolume(enabled: Boolean) = launch { repo.updateForceMaxVolume(enabled) }
    fun setSpeakInSilentMode(enabled: Boolean) = launch { repo.updateSpeakInSilentMode(enabled) }
    fun setAudioOutput(output: AudioOutput) = launch { repo.updateAudioOutput(output) }
    fun setEnableAudioFocus(enabled: Boolean) = launch { repo.updateEnableAudioFocus(enabled) }
    fun setQuietHours(quietHours: QuietHours) = launch { repo.setQuietHours(quietHours) }
    fun setExcludedApps(apps: List<String>) = launch { repo.updateExcludedApps(apps) }
    fun setApiConfig(api: ApiConfig) = launch { repo.setApiConfig(api) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
