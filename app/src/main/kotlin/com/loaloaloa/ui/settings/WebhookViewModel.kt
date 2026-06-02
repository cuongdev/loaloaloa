package com.loaloaloa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.data.model.WebhookConfig
import com.loaloaloa.data.model.effectiveWebhooks
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.webhook.WebhookTester
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    /** Persist the whole legacy single-webhook config (kept for back-compat / tests). */
    fun setWebhookConfig(config: WebhookConfig) {
        viewModelScope.launch { repo.setWebhookConfig(config) }
    }

    /**
     * Add (blank id) or update (matching id) one destination in the list, then persist the
     * whole list. A blank id gets a fresh stable id so future edits target the same row.
     */
    fun saveWebhook(config: WebhookConfig) {
        viewModelScope.launch {
            val current = repo.settings.first().effectiveWebhooks
            val saved = if (config.id.isBlank()) config.copy(id = UUID.randomUUID().toString()) else config
            val next = if (current.any { it.id == saved.id }) {
                current.map { if (it.id == saved.id) saved else it }
            } else {
                current + saved
            }
            repo.setWebhooks(next)
        }
    }

    /** Remove the destination with [id]. */
    fun deleteWebhook(id: String) {
        viewModelScope.launch {
            repo.setWebhooks(repo.settings.first().effectiveWebhooks.filterNot { it.id == id })
        }
    }

    /** Flip the enabled switch on the destination with [id] without opening the editor. */
    fun toggleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val next = repo.settings.first().effectiveWebhooks
                .map { if (it.id == id) it.copy(enabled = enabled) else it }
            repo.setWebhooks(next)
        }
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
