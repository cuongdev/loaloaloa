package com.tingting.notifier.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingting.notifier.data.model.UserSettings
import com.tingting.notifier.data.model.WebhookConfig
import com.tingting.notifier.data.repository.UserSettingsRepository
import com.tingting.notifier.webhook.WebhookTester
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of a one-shot webhook "Gửi thử" attempt, surfaced to the UI. */
sealed interface WebhookTestState {
    data object Idle : WebhookTestState
    data object Testing : WebhookTestState
    data class Success(val code: Int) : WebhookTestState
    data class Failure(val message: String) : WebhookTestState
}

@HiltViewModel
class WebhookViewModel @Inject constructor(
    private val repo: UserSettingsRepository,
    private val tester: WebhookTester,
) : ViewModel() {

    val uiState: StateFlow<UserSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _testState = MutableStateFlow<WebhookTestState>(WebhookTestState.Idle)
    val testState: StateFlow<WebhookTestState> = _testState.asStateFlow()

    /** Persist the whole webhook config (the values currently in the form). */
    fun setWebhookConfig(config: WebhookConfig) {
        viewModelScope.launch { repo.setWebhookConfig(config) }
    }

    /**
     * Send a SAMPLE payload to the SUPPLIED [config] (not necessarily persisted).
     * Publishes Testing → Success/Failure on [testState].
     */
    fun test(config: WebhookConfig) {
        _testState.value = WebhookTestState.Testing
        viewModelScope.launch {
            val result = tester.test(config)
            _testState.value = result.fold(
                onSuccess = { WebhookTestState.Success(it) },
                onFailure = { WebhookTestState.Failure(it.message ?: "Không xác định") },
            )
        }
    }

    /** Reset the banner after the UI has shown the outcome. */
    fun clearTest() {
        _testState.value = WebhookTestState.Idle
    }
}
