package com.tingting.notifier.webhook

import com.tingting.notifier.data.model.WebhookConfig
import com.tingting.notifier.data.model.WebhookTrigger

/**
 * Pure decision for whether a detected transaction should be pushed to the outbound
 * webhook. Independent of the announce (TTS) decision: fire iff the webhook is enabled,
 * a URL is configured, and the money direction matches the configured trigger.
 */
object WebhookPolicy {

    fun shouldFire(config: WebhookConfig, isIncome: Boolean): Boolean {
        if (!config.enabled || config.url.isBlank()) return false
        return when (config.trigger) {
            WebhookTrigger.BOTH -> true
            WebhookTrigger.INCOME -> isIncome
            WebhookTrigger.OUTGOING -> !isIncome
        }
    }
}
