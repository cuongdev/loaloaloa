package com.tingting.notifier.ui.fake

import com.tingting.notifier.data.model.ApiConfig
import com.tingting.notifier.data.model.AudioOutput
import com.tingting.notifier.data.model.QuietHours
import com.tingting.notifier.data.model.SpeakOption
import com.tingting.notifier.data.model.UserSettings
import com.tingting.notifier.data.model.WebhookConfig
import com.tingting.notifier.data.model.WebhookTrigger
import com.tingting.notifier.data.repository.UserSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [UserSettingsRepository] for ViewModel tests. */
class FakeUserSettingsRepository(
    initial: UserSettings = UserSettings(),
) : UserSettingsRepository {

    private val state = MutableStateFlow(initial)
    override val settings: Flow<UserSettings> = state

    val current: UserSettings get() = state.value

    override suspend fun updateEnableService(enabled: Boolean) = state.update { it.copy(enableService = enabled) }
    override suspend fun updateSpeakOption(option: SpeakOption) = state.update { it.copy(speakOption = option) }
    override suspend fun updateExcludedApps(apps: List<String>) = state.update { it.copy(excludedApps = apps) }
    override suspend fun updateSpeakShortMessage(enabled: Boolean) = state.update { it.copy(speakShortMessage = enabled) }
    override suspend fun updateAudioOutput(output: AudioOutput) = state.update { it.copy(audioOutput = output) }
    override suspend fun updateForceMaxVolume(enabled: Boolean) = state.update { it.copy(forceMaxVolume = enabled) }
    override suspend fun updateEnableAudioFocus(enabled: Boolean) = state.update { it.copy(enableAudioFocus = enabled) }
    override suspend fun updateSpeakInSilentMode(enabled: Boolean) = state.update { it.copy(speakInSilentMode = enabled) }
    override suspend fun updatePlayChime(enabled: Boolean) = state.update { it.copy(playChime = enabled) }
    override suspend fun updateRepeat(enabled: Boolean) = state.update { it.copy(repeat = enabled) }
    override suspend fun setQuietHours(quietHours: QuietHours) = state.update { it.copy(quietHours = quietHours) }
    override suspend fun setApiConfig(api: ApiConfig) = state.update { it.copy(api = api) }
    override suspend fun updateApiLastSeenTxnId(id: String) =
        state.update { it.copy(api = it.api.copy(lastSeenTxnId = id)) }

    override suspend fun updateWebhookEnabled(enabled: Boolean) =
        state.update { it.copy(webhook = it.webhook.copy(enabled = enabled)) }
    override suspend fun updateWebhookUrl(url: String) =
        state.update { it.copy(webhook = it.webhook.copy(url = url)) }
    override suspend fun updateWebhookSecret(secret: String) =
        state.update { it.copy(webhook = it.webhook.copy(secret = secret)) }
    override suspend fun updateWebhookTrigger(trigger: WebhookTrigger) =
        state.update { it.copy(webhook = it.webhook.copy(trigger = trigger)) }
    override suspend fun setWebhookConfig(webhook: WebhookConfig) =
        state.update { it.copy(webhook = webhook) }
}
