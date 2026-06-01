package com.tingting.notifier.tts

import com.google.common.truth.Truth.assertThat
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.UserSettings
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
        assertThat(text).isEqualTo("Ting ting! Bạn vừa nhận được Vietcombank 500.000 đồng")
    }

    @Test fun `income short message omits bank`() {
        val text = builder.build(model(), UserSettings(speakShortMessage = true))
        assertThat(text).isEqualTo("Ting ting! Bạn vừa nhận được 500.000 đồng")
    }

    @Test fun `income with blank bank omits bank`() {
        val text = builder.build(model(bankName = ""), UserSettings())
        assertThat(text).isEqualTo("Ting ting! Bạn vừa nhận được 500.000 đồng")
    }

    @Test fun `outgoing with bank uses transfer template`() {
        val text = builder.build(model(isIncome = false), UserSettings())
        assertThat(text).isEqualTo("Bạn vừa chuyển Vietcombank 500.000 đồng")
    }

    @Test fun `outgoing short message omits bank`() {
        val text = builder.build(model(isIncome = false), UserSettings(speakShortMessage = true))
        assertThat(text).isEqualTo("Bạn vừa chuyển 500.000 đồng")
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
