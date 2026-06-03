package com.loaloaloa.domain

import androidx.datastore.core.DataStore
import com.loaloaloa.data.model.ApiConfig
import com.loaloaloa.data.model.AppMode
import com.loaloaloa.data.model.AudioOutput
import com.loaloaloa.data.model.QuietHours
import com.loaloaloa.data.model.RelayRegisterState
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.data.model.SpeakOption
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.data.model.WebhookConfig
import com.loaloaloa.data.model.WebhookTrigger
import com.loaloaloa.data.repository.UserSettingsRepository
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

    override suspend fun updateSpeakDailyTotal(enabled: Boolean) {
        dataStore.updateData { it.copy(speakDailyTotal = enabled) }
    }

    override suspend fun updateSpeakContent(enabled: Boolean) {
        dataStore.updateData { it.copy(speakContent = enabled) }
    }

    override suspend fun updateMinAnnounceAmount(amount: Long) {
        dataStore.updateData { it.copy(minAnnounceAmount = amount.coerceAtLeast(0)) }
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

    override suspend fun setWebhooks(webhooks: List<WebhookConfig>) {
        // Clear the legacy single config so a now-empty list doesn't resurrect it (see effectiveWebhooks).
        dataStore.updateData { it.copy(webhooks = webhooks, webhook = WebhookConfig()) }
    }

    override suspend fun setShiftStartedAt(startedAtMillis: Long?) {
        dataStore.updateData { it.copy(shiftStartedAt = startedAtMillis) }
    }

    override suspend fun setStaffName(name: String) {
        dataStore.updateData { it.copy(staffName = name) }
    }

    override suspend fun setActiveStaff(names: List<String>) {
        dataStore.updateData { it.copy(activeStaff = names) }
    }

    override suspend fun setCustomApps(apps: Map<String, String>) {
        dataStore.updateData { it.copy(customApps = apps) }
    }

    override suspend fun updateRelayRole(role: RelayRole) {
        dataStore.updateData { it.copy(relayRole = role) }
    }

    override suspend fun setRelayRoom(room: RelayRoom) {
        dataStore.updateData { it.copy(relayRoom = room) }
    }

    override suspend fun setRelayRegisterState(state: RelayRegisterState) {
        dataStore.updateData { it.copy(relayRegisterState = state) }
    }

    override suspend fun updateAppMode(mode: AppMode) {
        dataStore.updateData { it.copy(appMode = mode, settingsVersion = maxOf(it.settingsVersion, 1)) }
    }
}
