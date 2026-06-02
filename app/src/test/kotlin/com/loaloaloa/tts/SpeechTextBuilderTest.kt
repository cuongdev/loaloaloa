package com.loaloaloa.tts

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.UserSettings
import org.junit.Test

class SpeechTextBuilderTest {

    private val builder = SpeechTextBuilder()

    private fun model(
        bankName: String = "Vietcombank",
        amount: Long = 500_000,
        isIncome: Boolean = true,
    ) = TransactionModel(
        appId = "com.VCB",
        bankName = bankName,
        amount = amount,
        isIncome = isIncome,
        rawText = "+500.000đ",
        timestamp = 1_000,
    )

    @Test fun `income with bank uses full template`() {
        val text = builder.build(model(), UserSettings())
        assertThat(text).isEqualTo("Loa loa loa! Bạn vừa nhận được Vietcombank 500.000 đồng")
    }

    @Test fun `income short message omits bank`() {
        val text = builder.build(model(), UserSettings(speakShortMessage = true))
        assertThat(text).isEqualTo("Loa loa loa! Bạn vừa nhận được 500.000 đồng")
    }

    @Test fun `income with blank bank omits bank`() {
        val text = builder.build(model(bankName = ""), UserSettings())
        assertThat(text).isEqualTo("Loa loa loa! Bạn vừa nhận được 500.000 đồng")
    }

    @Test fun `outgoing with bank uses transfer template`() {
        val text = builder.build(model(isIncome = false), UserSettings())
        assertThat(text).isEqualTo("Bạn vừa chuyển Vietcombank 500.000 đồng")
    }

    @Test fun `outgoing short message omits bank`() {
        val text = builder.build(model(isIncome = false), UserSettings(speakShortMessage = true))
        assertThat(text).isEqualTo("Bạn vừa chuyển 500.000 đồng")
    }

    @Test fun `daily total suffix appended for income when enabled`() {
        val text = builder.build(model(), UserSettings(speakDailyTotal = true), dailyIncomeTotal = 2_000_000)
        assertThat(text).isEqualTo("Loa loa loa! Bạn vừa nhận được Vietcombank 500.000 đồng. Tổng hôm nay 2.000.000 đồng")
    }

    @Test fun `daily total suffix skipped when feature off`() {
        val text = builder.build(model(), UserSettings(speakDailyTotal = false), dailyIncomeTotal = 2_000_000)
        assertThat(text).isEqualTo("Loa loa loa! Bạn vừa nhận được Vietcombank 500.000 đồng")
    }

    @Test fun `daily total suffix skipped for outgoing`() {
        val text = builder.build(model(isIncome = false), UserSettings(speakDailyTotal = true), dailyIncomeTotal = 2_000_000)
        assertThat(text).isEqualTo("Bạn vừa chuyển Vietcombank 500.000 đồng")
    }

    @Test fun `daily total suffix skipped when total is null`() {
        val text = builder.build(model(), UserSettings(speakDailyTotal = true), dailyIncomeTotal = null)
        assertThat(text).isEqualTo("Loa loa loa! Bạn vừa nhận được Vietcombank 500.000 đồng")
    }

    @Test fun `amount is grouped by thousands`() {
        assertThat(SpeechTextBuilder.formatAmount(500_000)).isEqualTo("500.000")
        assertThat(SpeechTextBuilder.formatAmount(1_500_000)).isEqualTo("1.500.000")
        assertThat(SpeechTextBuilder.formatAmount(0)).isEqualTo("0")
        assertThat(SpeechTextBuilder.formatAmount(500)).isEqualTo("500")
        assertThat(SpeechTextBuilder.formatAmount(1_000)).isEqualTo("1.000")
        assertThat(SpeechTextBuilder.formatAmount(123_456_789)).isEqualTo("123.456.789")
    }
}
