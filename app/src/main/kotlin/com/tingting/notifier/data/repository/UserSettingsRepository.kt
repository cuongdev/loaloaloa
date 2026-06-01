package com.tingting.notifier.data.repository

import com.tingting.notifier.data.model.ApiConfig
import com.tingting.notifier.data.model.AudioOutput
import com.tingting.notifier.data.model.QuietHours
import com.tingting.notifier.data.model.SpeakOption
import com.tingting.notifier.data.model.UserSettings
import com.tingting.notifier.data.model.WebhookConfig
import com.tingting.notifier.data.model.WebhookTrigger
import kotlinx.coroutines.flow.Flow

/** Typed user settings, observable as a [Flow] with a suspend update per field. */
interface UserSettingsRepository {

    val settings: Flow<UserSettings>

    suspend fun updateEnableService(enabled: Boolean)
    suspend fun updateSpeakOption(option: SpeakOption)
    suspend fun updateExcludedApps(apps: List<String>)
    suspend fun updateSpeakShortMessage(enabled: Boolean)
    suspend fun updateAudioOutput(output: AudioOutput)
    suspend fun updateForceMaxVolume(enabled: Boolean)
    suspend fun updateEnableAudioFocus(enabled: Boolean)
    suspend fun updateSpeakInSilentMode(enabled: Boolean)
    suspend fun updatePlayChime(enabled: Boolean)
    suspend fun updateRepeat(enabled: Boolean)
    suspend fun setQuietHours(quietHours: QuietHours)
    suspend fun setApiConfig(api: ApiConfig)

    /** Persist the polling source's last-seen provider transaction id (dedupe cursor). */
    suspend fun updateApiLastSeenTxnId(id: String)

    suspend fun updateWebhookEnabled(enabled: Boolean)
    suspend fun updateWebhookUrl(url: String)
    suspend fun updateWebhookSecret(secret: String)
    suspend fun updateWebhookTrigger(trigger: WebhookTrigger)
    suspend fun setWebhookConfig(webhook: WebhookConfig)
}
