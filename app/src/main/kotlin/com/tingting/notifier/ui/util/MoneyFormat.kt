package com.tingting.notifier.ui.util

import com.tingting.notifier.tts.SpeechTextBuilder

/**
 * Shared money rendering for the UI. Reuses [SpeechTextBuilder.formatAmount] for the
 * '.'-grouped digits and adds the sign + trailing `đ`:
 *   income  → "+500.000 đ"
 *   outgoing→ "−500.000 đ"   (U+2212 MINUS SIGN, matching the design, not '-')
 *
 * Pure (no Android types) so it is unit-tested directly.
 */
object MoneyFormat {

    private const val MINUS = '−' // − minus sign

    /** Signed amount with grouping and trailing đ, e.g. format(500000, true) = "+500.000 đ". */
    fun format(amount: Long, isIncome: Boolean): String {
        val sign = if (isIncome) "+" else MINUS.toString()
        return "$sign${grouped(amount)} đ"
    }

    /** Unsigned grouped amount with trailing đ, e.g. "12.800.000 đ" (used for neutral totals). */
    fun plain(amount: Long): String = "${grouped(amount)} đ"

    private fun grouped(amount: Long): String =
        SpeechTextBuilder.formatAmount(kotlin.math.abs(amount))
}
