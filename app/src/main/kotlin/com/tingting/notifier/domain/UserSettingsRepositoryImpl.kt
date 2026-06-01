package com.tingting.notifier.domain

import androidx.datastore.core.DataStore
import com.tingting.notifier.data.model.ApiConfig
import com.tingting.notifier.data.model.AudioOutput
import com.tingting.notifier.data.model.QuietHours
import com.tingting.notifier.data.model.SpeakOption
import com.tingting.notifier.data.model.UserSettings
import com.tingting.notifier.data.model.WebhookConfig
import com.tingting.notifier.data.model.WebhookTrigger
import com.tingting.notifier.data.repository.UserSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class UserSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<UserSettings>,
) : UserSettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data

    override suspend fun updateEnableService(enabled: Boolean) {
        dataStore.updateData { it.copy(enableService = enabled) }
    }

    override suspend fun updateSpeakOption(option: SpeakOption) {
        dataStore.updateData { it.copy(speakOption = option) }
    }

    override suspend fun updateExcludedApps(apps: List<String>) {
        dataStore.updateData { it.copy(excludedApps = apps) }
    }

    override suspend fun updateSpeakShortMessage(enabled: Boolean) {
        dataStore.updateData { it.copy(speakShortMessage = enabled) }
    }

    override suspend fun updateAudioOutput(output: AudioOutput) {
        dataStore.updateData { it.copy(audioOutput = output) }
    }

    override suspend fun updateForceMaxVolume(enabled: Boolean) {
        dataStore.updateData { it.copy(forceMaxVolume = enabled) }
    }

    override suspend fun updateEnableAudioFocus(enabled: Boolean) {
        dataStore.updateData { it.copy(enableAudioFocus = enabled) }
    }

    override suspend fun updateSpeakInSilentMode(enabled: Boolean) {
        dataStore.updateData { it.copy(speakInSilentMode = enabled) }
    }

    override suspend fun updatePlayChime(enabled: Boolean) {
        dataStore.updateData { it.copy(playChime = enabled) }
    }

    override suspend fun updateRepeat(enabled: Boolean) {
        dataStore.updateData { it.copy(repeat = enabled) }
    }

    override suspend fun setQuietHours(quietHours: QuietHours) {
        dataStore.updateData { it.copy(quietHours = quietHours) }
    }

    override suspend fun setApiConfig(api: ApiConfig) {
        dataStore.updateData { it.copy(api = api) }
    }

    override suspend fun updateApiLastSeenTxnId(id: String) {
        dataStore.updateData { it.copy(api = it.api.copy(lastSeenTxnId = id)) }
    }

    override suspend fun updateWebhookEnabled(enabled: Boolean) {
        dataStore.updateData { it.copy(webhook = it.webhook.copy(enabled = enabled)) }
    }

    override suspend fun updateWebhookUrl(url: String) {
        dataStore.updateData { it.copy(webhook = it.webhook.copy(url = url)) }
    }

    override suspend fun updateWebhookSecret(secret: String) {
        dataStore.updateData { it.copy(webhook = it.webhook.copy(secret = secret)) }
    }

    override suspend fun updateWebhookTrigger(trigger: WebhookTrigger) {
        dataStore.updateData { it.copy(webhook = it.webhook.copy(trigger = trigger)) }
    }

    override suspend fun setWebhookConfig(webhook: WebhookConfig) {
        dataStore.updateData { it.copy(webhook = webhook) }
    }
}
