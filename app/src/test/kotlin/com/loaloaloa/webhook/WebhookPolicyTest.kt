package com.loaloaloa.webhook

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.WebhookConfig
import com.loaloaloa.data.model.WebhookTrigger
import org.junit.Test

/**
 * Full truth table for the outbound webhook fire decision. Fire iff
 * `enabled && url.isNotBlank() && direction matches trigger && within shift window`.
 */
class WebhookPolicyTest {

    private val noon = 12 * 60

    private fun config(
        enabled: Boolean = true,
        url: String = "https://hook.example/abc",
        trigger: WebhookTrigger = WebhookTrigger.BOTH,
        shiftEnabled: Boolean = false,
        shiftStartMinutes: Int = 8 * 60,
        shiftEndMinutes: Int = 17 * 60,
    ) = WebhookConfig(
        enabled = enabled,
        url = url,
        trigger = trigger,
        shiftEnabled = shiftEnabled,
        shiftStartMinutes = shiftStartMinutes,
        shiftEndMinutes = shiftEndMinutes,
    )

    // ---- enabled gate ----

    @Test fun `disabled never fires`() {
        val c = config(enabled = false, trigger = WebhookTrigger.BOTH)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = noon)).isFalse()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = false, nowMinutesOfDay = noon)).isFalse()
    }

    // ---- url gate ----

    @Test fun `blank url never fires`() {
        val c = config(url = "", trigger = WebhookTrigger.BOTH)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = noon)).isFalse()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = false, nowMinutesOfDay = noon)).isFalse()
    }

    @Test fun `whitespace-only url never fires`() {
        val c = config(url = "   ", trigger = WebhookTrigger.BOTH)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = noon)).isFalse()
    }

    // ---- BOTH ----

    @Test fun `both fires for income and outgoing`() {
        val c = config(trigger = WebhookTrigger.BOTH)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = noon)).isTrue()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = false, nowMinutesOfDay = noon)).isTrue()
    }

    // ---- INCOME ----

    @Test fun `income trigger fires only on income`() {
        val c = config(trigger = WebhookTrigger.INCOME)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = noon)).isTrue()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = false, nowMinutesOfDay = noon)).isFalse()
    }

    // ---- OUTGOING ----

    @Test fun `outgoing trigger fires only on outgoing`() {
        val c = config(trigger = WebhookTrigger.OUTGOING)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = false, nowMinutesOfDay = noon)).isTrue()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = noon)).isFalse()
    }

    // ---- shift window ----

    @Test fun `shift disabled fires at any time`() {
        val c = config(shiftEnabled = false)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = 3 * 60)).isTrue()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = 23 * 60)).isTrue()
    }

    @Test fun `daytime shift fires inside and not outside the window`() {
        val c = config(shiftEnabled = true, shiftStartMinutes = 8 * 60, shiftEndMinutes = 17 * 60)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = 8 * 60)).isTrue() // inclusive start
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = 12 * 60)).isTrue()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = 17 * 60)).isFalse() // exclusive end
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = 7 * 60 + 59)).isFalse()
    }

    @Test fun `overnight shift wraps past midnight`() {
        val c = config(shiftEnabled = true, shiftStartMinutes = 22 * 60, shiftEndMinutes = 6 * 60)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = 23 * 60)).isTrue()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = 2 * 60)).isTrue()
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = 12 * 60)).isFalse()
    }

    @Test fun `zero-width shift is treated as all-day`() {
        val c = config(shiftEnabled = true, shiftStartMinutes = 9 * 60, shiftEndMinutes = 9 * 60)
        assertThat(WebhookPolicy.shouldFire(c, isIncome = true, nowMinutesOfDay = 3 * 60)).isTrue()
    }
}
