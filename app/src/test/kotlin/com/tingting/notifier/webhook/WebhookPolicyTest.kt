package com.tingting.notifier.webhook

import com.google.common.truth.Truth.assertThat
import com.tingting.notifier.data.model.WebhookConfig
import com.tingting.notifier.data.model.WebhookTrigger
import org.junit.Test

/**
 * Full truth table for the outbound webhook fire decision. Fire iff
 * `enabled && url.isNotBlank() && direction matches trigger`.
 */
class WebhookPolicyTest {

    private fun config(
        enabled: Boolean = true,
        url: String = "https://hook.example/abc",
        trigger: WebhookTrigger = WebhookTrigger.BOTH,
    ) = WebhookConfig(enabled = enabled, url = url, trigger = trigger)

    // ---- enabled gate ----

    @Test fun `disabled never fires`() {
        val c = config(enabled = false, trigger = WebhookTrigger.BOTH)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true)).isFalse()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = false)).isFalse()
    }

    // ---- url gate ----

    @Test fun `blank url never fires`() {
        val c = config(url = "", trigger = WebhookTrigger.BOTH)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true)).isFalse()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = false)).isFalse()
    }

    @Test fun `whitespace-only url never fires`() {
        val c = config(url = "   ", trigger = WebhookTrigger.BOTH)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true)).isFalse()
    }

    // ---- BOTH ----

    @Test fun `both fires for income and outgoing`() {
        val c = config(trigger = WebhookTrigger.BOTH)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true)).isTrue()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = false)).isTrue()
    }

    // ---- INCOME ----

    @Test fun `income trigger fires only on income`() {
        val c = config(trigger = WebhookTrigger.INCOME)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true)).isTrue()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = false)).isFalse()
    }

    // ---- OUTGOING ----

    @Test fun `outgoing trigger fires only on outgoing`() {
        val c = config(trigger = WebhookTrigger.OUTGOING)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = false)).isTrue()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true)).isFalse()
    }
}
