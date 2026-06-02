package com.loaloaloa.tts

import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.UserSettings
import javax.inject.Inject

/**
 * Builds the single Vietnamese sentence announced for a [TransactionModel]. Pure —
 * holds no Android types and no audio policy. The "repeat" setting is handled by
 * [TtsManager] (which speaks the sentence twice); this always returns one sentence.
 */
class SpeechTextBuilder @Inject constructor() {

    /**
     * @param dailyIncomeTotal the calendar-day income total to append as "Tổng hôm nay …" when
     *   [UserSettings.speakDailyTotal] is on and this is an income event; null/non-income skips it.
     * @return the sentence to speak. Includes the bank name unless
     *   [UserSettings.speakShortMessage] is set or [TransactionModel.bankName] is blank; appends the
     *   raw notification content ("Nội dung: …") when [UserSettings.speakContent] is on.
     */
    fun build(
        model: TransactionModel,
        settings: UserSettings,
        dailyIncomeTotal: Long? = null,
    ): String {
        val amount = formatAmount(model.amount)
        val includeBank = !settings.speakShortMessage && model.bankName.isNotBlank()
        val base = if (model.isIncome) {
            if (includeBank) {
                "Loa loa loa! Bạn vừa nhận được ${model.bankName} $amount đồng"
            } else {
                "Loa loa loa! Bạn vừa nhận được $amount đồng"
            }
        } else {
            if (includeBank) {
                "Bạn vừa chuyển ${model.bankName} $amount đồng"
            } else {
                "Bạn vừa chuyển $amount đồng"
            }
        }
        val sentence = StringBuilder(base)
        if (settings.speakContent) {
            val content = model.rawText.replace(WHITESPACE, " ").trim()
            if (content.isNotEmpty()) sentence.append(". Nội dung: ").append(content)
        }
        if (settings.speakDailyTotal && model.isIncome && dailyIncomeTotal != null) {
            sentence.append(". Tổng hôm nay ").append(formatAmount(dailyIncomeTotal)).append(" đồng")
        }
        return sentence.toString()
    }

    companion object {
        /** Collapse runs of whitespace/newlines so the spoken content stays on one breathless line. */
        private val WHITESPACE = Regex("\\s+")

        /** Group an absolute VND amount with '.' thousands separators (500000 -> "500.000"). */
        fun formatAmount(amount: Long): String {
            val digits = amount.toString()
            val sb = StringBuilder()
            val firstGroup = digits.length % 3
            for ((index, ch) in digits.withIndex()) {
                if (index != 0 && (index - firstGroup) % 3 == 0) sb.append('.')
                sb.append(ch)
            }
            return sb.toString()
        }
    }
}
