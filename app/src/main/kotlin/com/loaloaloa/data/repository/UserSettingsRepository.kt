package com.loaloaloa.data.repository

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
    suspend fun updateSpeakDailyTotal(enabled: Boolean)

    /** Toggle reading the raw transaction content (notification text) aloud after the amount. */
    suspend fun updateSpeakContent(enabled: Boolean)

    /** Set the minimum amount (VND) to announce out loud; values below 0 are clamped to 0 (off). */
    suspend fun updateMinAnnounceAmount(amount: Long)
    suspend fun setQuietHours(quietHours: QuietHours)
    suspend fun setApiConfig(api: ApiConfig)

    /** Persist the polling source's last-seen provider transaction id (dedupe cursor). */
    suspend fun updateApiLastSeenTxnId(id: String)

    suspend fun updateWebhookEnabled(enabled: Boolean)
    suspend fun updateWebhookUrl(url: String)
    suspend fun updateWebhookSecret(secret: String)
    suspend fun updateWebhookTrigger(trigger: WebhookTrigger)
    suspend fun setWebhookConfig(webhook: WebhookConfig)

    /** Replace the full list of outbound destinations (and retire the legacy single config). */
    suspend fun setWebhooks(webhooks: List<WebhookConfig>)

    /** Open a work shift at [startedAtMillis], or pass null to close/clear the current shift. */
    suspend fun setShiftStartedAt(startedAtMillis: Long?)

    /** Set this device's default employee name (staff shell); pass "" to clear. */
    suspend fun setStaffName(name: String)

    /** Replace the list of employees currently on shift on this device (stamped onto transaction notes). */
    suspend fun setActiveStaff(names: List<String>)

    /** Replace the set of user-added notification sources (package name → display name). */
    suspend fun setCustomApps(apps: Map<String, String>)

    /** Set this device's relay role (none / hub / spoke). */
    suspend fun updateRelayRole(role: RelayRole)

    /** Set the paired relay room, or pass an empty [RelayRoom] to clear the pairing. */
    suspend fun setRelayRoom(room: RelayRoom)

    /** Persist the spoke's FCM registration outcome (drives the staff status card). */
    suspend fun setRelayRegisterState(state: RelayRegisterState)

    /** Set the UI-shell mode and mark the settings schema as migrated (settingsVersion = 1). */
    suspend fun updateAppMode(mode: AppMode)
}
