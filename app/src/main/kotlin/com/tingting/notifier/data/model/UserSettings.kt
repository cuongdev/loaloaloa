package com.tingting.notifier.data.model

import kotlinx.serialization.Serializable

/** How announcements are picked relative to the money direction. */
enum class SpeakOption { INCOME_ONLY, OUTGOING_ONLY, BOTH }

/** Audio stream the chime/speech plays on. */
enum class AudioOutput { NOTIFICATION, ALARM, MEDIA }

/** A daily quiet window in which announcements are suppressed (minutes-of-day). */
@Serializable
data class QuietHours(
    val enabled: Boolean = false,
    val startMinutes: Int = 0,
    val endMinutes: Int = 0,
)

/** Configuration for the polling API source (e.g. SePay). */
@Serializable
data class ApiConfig(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val token: String = "",
    val account: String = "",
    val pollSeconds: Int = 30,
    /**
     * Provider transaction id of the most recent row ingested, used to dedupe the
     * polling source across restarts. Additive field; an empty default keeps
     * previously-persisted settings JSON loading cleanly.
     */
    val lastSeenTxnId: String = "",
)

/** Root user-settings tree persisted via [androidx.datastore.core.DataStore]. */
@Serializable
data class UserSettings(
    val enableService: Boolean = false,
    val speakOption: SpeakOption = SpeakOption.BOTH,
    val excludedApps: List<String> = emptyList(),
    val speakShortMessage: Boolean = false,
    val audioOutput: AudioOutput = AudioOutput.NOTIFICATION,
    val forceMaxVolume: Boolean = false,
    val enableAudioFocus: Boolean = true,
    val speakInSilentMode: Boolean = false,
    val playChime: Boolean = true,
    val repeat: Boolean = false,
    val quietHours: QuietHours = QuietHours(),
    val api: ApiConfig = ApiConfig(),
)
