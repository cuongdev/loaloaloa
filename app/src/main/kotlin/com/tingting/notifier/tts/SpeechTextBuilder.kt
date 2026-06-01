package com.tingting.notifier.tts

import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.UserSettings
import javax.inject.Inject

/**
 * Builds the single Vietnamese sentence announced for a [TransactionModel]. Pure —
 * holds no Android types and no audio policy. The "repeat" setting is handled by
 * [TtsManager] (which speaks the sentence twice); this always returns one sentence.
 */
class SpeechTextBuilder @Inject constructor() {

    /**
     * @return the sentence to speak. Includes the bank name unless
     *   [UserSettings.speakShortMessage] is set or [TransactionModel.bankName] is blank.
     */
    fun build(model: TransactionModel, settings: UserSettings): String {
        val amount = formatAmount(model.amount)
        val includeBank = !settings.speakShortMessage && model.bankName.isNotBlank()
        return if (model.isIncome) {
            if (includeBank) {
                "Ting ting! Bạn vừa nhận được ${model.bankName} $amount đồng"
            } else {
                "Ting ting! Bạn vừa nhận được $amount đồng"
            }
        } else {
            if (includeBank) {
                "Bạn vừa chuyển ${model.bankName} $amount đồng"
            } else {
                "Bạn vừa chuyển $amount đồng"
            }
        }
    }

    companion object {
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
