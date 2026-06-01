package com.tingting.notifier.ingest

import com.google.common.truth.Truth.assertThat
import com.tingting.notifier.data.model.QuietHours
import com.tingting.notifier.data.model.SpeakOption
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.UserSettings
import com.tingting.notifier.tts.QuietHoursGate
import org.junit.Test

/**
 * Full truth table for the shared announce decision. `QuietHoursGate` is pure so it
 * is constructed directly; `nowMinutesOfDay` is supplied so the test never reads a
 * clock. The window 09:00..17:00 (540..1020) is "quiet" between those minutes.
 */
class AnnouncePolicyTest {

    private val policy = AnnouncePolicy(QuietHoursGate())

    private fun model(isIncome: Boolean) = TransactionModel(
        appId = "sepay",
        bankName = "Vietcombank",
        amount = 500_000,
        isIncome = isIncome,
        rawText = "thanh toan",
        timestamp = 0L,
    )

    private fun settings(
        enableService: Boolean = true,
        speakOption: SpeakOption = SpeakOption.BOTH,
        quietHours: QuietHours = QuietHours(),
    ) = UserSettings(
        enableService = enableService,
        speakOption = speakOption,
        quietHours = quietHours,
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
}
