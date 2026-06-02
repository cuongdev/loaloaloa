package com.loaloaloa.ingest

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.QuietHours
import com.loaloaloa.data.model.SpeakOption
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.tts.QuietHoursGate
import org.junit.Test

/**
 * Full truth table for the shared announce decision. `QuietHoursGate` is pure so it
 * is constructed directly; `nowMinutesOfDay` is supplied so the test never reads a
 * clock. The window 09:00..17:00 (540..1020) is "quiet" between those minutes.
 */
class AnnouncePolicyTest {

    private val policy = AnnouncePolicy(QuietHoursGate())

    private fun model(isIncome: Boolean, amount: Long = 500_000) = TransactionModel(
        appId = "sepay",
        bankName = "Vietcombank",
        amount = amount,
        isIncome = isIncome,
        rawText = "thanh toan",
        timestamp = 0L,
    )

    private fun settings(
        enableService: Boolean = true,
        speakOption: SpeakOption = SpeakOption.BOTH,
        quietHours: QuietHours = QuietHours(),
        minAnnounceAmount: Long = 0,
    ) = UserSettings(
        enableService = enableService,
        speakOption = speakOption,
        quietHours = quietHours,
        minAnnounceAmount = minAnnounceAmount,
    )

    // ---- enableService gate ----

    @Test fun `service disabled never announces`() {
        val s = settings(enableService = false, speakOption = SpeakOption.BOTH)
        assertThat(policy.shouldAnnounce(model(isIncome = true), s, nowMinutesOfDay = 720)).isFalse()
        assertThat(policy.shouldAnnounce(model(isIncome = false), s, nowMinutesOfDay = 720)).isFalse()
    }

    // ---- INCOME_ONLY ----

    @Test fun `income only announces income`() {
        val s = settings(speakOption = SpeakOption.INCOME_ONLY)
        assertThat(policy.shouldAnnounce(model(isIncome = true), s, nowMinutesOfDay = 720)).isTrue()
    }

    @Test fun `income only suppresses outgoing`() {
        val s = settings(speakOption = SpeakOption.INCOME_ONLY)
        assertThat(policy.shouldAnnounce(model(isIncome = false), s, nowMinutesOfDay = 720)).isFalse()
    }

    // ---- OUTGOING_ONLY ----

    @Test fun `outgoing only announces outgoing`() {
        val s = settings(speakOption = SpeakOption.OUTGOING_ONLY)
        assertThat(policy.shouldAnnounce(model(isIncome = false), s, nowMinutesOfDay = 720)).isTrue()
    }

    @Test fun `outgoing only suppresses income`() {
        val s = settings(speakOption = SpeakOption.OUTGOING_ONLY)
        assertThat(policy.shouldAnnounce(model(isIncome = true), s, nowMinutesOfDay = 720)).isFalse()
    }

    // ---- BOTH ----

    @Test fun `both announces income and outgoing`() {
        val s = settings(speakOption = SpeakOption.BOTH)
        assertThat(policy.shouldAnnounce(model(isIncome = true), s, nowMinutesOfDay = 720)).isTrue()
        assertThat(policy.shouldAnnounce(model(isIncome = false), s, nowMinutesOfDay = 720)).isTrue()
    }

    // ---- quiet hours ----

    @Test fun `quiet hours suppress even when direction matches and enabled`() {
        val qh = QuietHours(enabled = true, startMinutes = 540, endMinutes = 1020)
        val s = settings(speakOption = SpeakOption.BOTH, quietHours = qh)
        // 10:00 (600) is inside 09:00..17:00
        assertThat(policy.shouldAnnounce(model(isIncome = true), s, nowMinutesOfDay = 600)).isFalse()
    }

    @Test fun `outside quiet hours announces when enabled and matching`() {
        val qh = QuietHours(enabled = true, startMinutes = 540, endMinutes = 1020)
        val s = settings(speakOption = SpeakOption.BOTH, quietHours = qh)
        // 20:00 (1200) is outside 09:00..17:00
        assertThat(policy.shouldAnnounce(model(isIncome = true), s, nowMinutesOfDay = 1200)).isTrue()
    }

    @Test fun `disabled quiet hours do not suppress`() {
        val qh = QuietHours(enabled = false, startMinutes = 540, endMinutes = 1020)
        val s = settings(speakOption = SpeakOption.BOTH, quietHours = qh)
        assertThat(policy.shouldAnnounce(model(isIncome = true), s, nowMinutesOfDay = 600)).isTrue()
    }

    // ---- minimum announce amount ----

    @Test fun `amount below threshold is suppressed`() {
        val s = settings(minAnnounceAmount = 100_000)
        assertThat(policy.shouldAnnounce(model(isIncome = true, amount = 50_000), s, nowMinutesOfDay = 720)).isFalse()
    }

    @Test fun `amount at or above threshold announces`() {
        val s = settings(minAnnounceAmount = 100_000)
        assertThat(policy.shouldAnnounce(model(isIncome = true, amount = 100_000), s, nowMinutesOfDay = 720)).isTrue()
        assertThat(policy.shouldAnnounce(model(isIncome = true, amount = 500_000), s, nowMinutesOfDay = 720)).isTrue()
    }

    @Test fun `zero threshold announces every amount`() {
        val s = settings(minAnnounceAmount = 0)
        assertThat(policy.shouldAnnounce(model(isIncome = true, amount = 1), s, nowMinutesOfDay = 720)).isTrue()
    }
}
