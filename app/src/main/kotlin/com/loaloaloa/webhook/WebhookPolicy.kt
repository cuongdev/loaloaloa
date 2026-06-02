package com.loaloaloa.webhook

import com.loaloaloa.data.model.WebhookConfig
import com.loaloaloa.data.model.WebhookTrigger
import com.loaloaloa.data.model.WebhookType

/**
 * Pure decision for whether a detected transaction should be pushed to a given destination.
 * Independent of the announce (TTS) decision: fire iff the destination is enabled, the fields
 * its type needs are configured, the money direction matches the configured trigger, and
 * (when a shift window is set) the current minute-of-day is inside that window.
 *
 * Pure with respect to time: the caller supplies [nowMinutesOfDay] (0..1439), so this is
 * fully unit-testable without the system clock.
 */
object WebhookPolicy {

    fun shouldFire(config: WebhookConfig, isIncome: Boolean, nowMinutesOfDay: Int): Boolean {
        if (!config.enabled || !config.isConfigured()) return false
        val directionOk = when (config.trigger) {
            WebhookTrigger.BOTH -> true
            WebhookTrigger.INCOME -> isIncome
            WebhookTrigger.OUTGOING -> !isIncome
        }
        return directionOk && isWithinShift(config, nowMinutesOfDay)
    }

    /**
     * True when [nowMinutesOfDay] falls inside the destination's shift window. Always true when
     * the shift is disabled or zero-width (start == end), so a misconfigured window never
     * silently drops every transaction. The window is half-open `[start, end)` and may wrap
     * past midnight (e.g. a 22:00–06:00 night shift).
     */
    fun isWithinShift(config: WebhookConfig, nowMinutesOfDay: Int): Boolean {
        if (!config.shiftEnabled) return true
        val start = config.shiftStartMinutes
        val end = config.shiftEndMinutes
        if (start == end) return true
        return if (start < end) {
            nowMinutesOfDay in start until end
        } else {
            nowMinutesOfDay >= start || nowMinutesOfDay < end
        }
    }
}

/** True when [WebhookConfig] has the fields its [WebhookConfig.type] requires to deliver. */
fun WebhookConfig.isConfigured(): Boolean = when (type) {
    WebhookType.TELEGRAM -> botToken.isNotBlank() && chatId.isNotBlank()
    WebhookType.GENERIC, WebhookType.GOOGLE_SHEET -> url.isNotBlank()
    WebhookType.GOOGLE_FORM -> url.isNotBlank() &&
        (formAmountEntry.isNotBlank() || formBankEntry.isNotBlank() ||
            formTimeEntry.isNotBlank() || formNoteEntry.isNotBlank())
}
