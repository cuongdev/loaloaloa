package com.tingting.notifier.ingest

import com.tingting.notifier.data.model.SpeakOption
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.UserSettings
import com.tingting.notifier.tts.QuietHoursGate
import javax.inject.Inject

/**
 * Pure decision of whether a detected transaction should be announced out loud.
 * Shared by every [com.tingting.notifier.source.TransactionSource] via the
 * [TransactionIngestor] so the gating rule lives in exactly one tested place.
 *
 * Announce iff ALL hold:
 *  - the master service switch ([UserSettings.enableService]) is on,
 *  - the [UserSettings.speakOption] matches the money direction, and
 *  - the current minute-of-day is NOT inside the user's quiet window.
 *
 * Pure with respect to time: the caller supplies [nowMinutesOfDay], so this is
 * fully unit-testable without the system clock.
 */
class AnnouncePolicy @Inject constructor(
    private val quietHoursGate: QuietHoursGate,
) {
    /** @return true when [model] should be spoken under [settings] at [nowMinutesOfDay]. */
    fun shouldAnnounce(
        model: TransactionModel,
        settings: UserSettings,
        nowMinutesOfDay: Int,
    ): Boolean {
        if (!settings.enableService) return false
        val directionOk = when (settings.speakOption) {
            SpeakOption.BOTH -> true
            SpeakOption.INCOME_ONLY -> model.isIncome
            SpeakOption.OUTGOING_ONLY -> !model.isIncome
        }
        if (!directionOk) return false
        return !quietHoursGate.isQuiet(nowMinutesOfDay, settings.quietHours)
    }
}
