package com.loaloaloa.ui.shift

import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.ui.util.MoneyFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Live totals for the open shift, recomputed from every transaction since it started. */
data class ShiftUiState(
    val active: Boolean = false,
    val startedAt: Long? = null,
    val incomeTotal: Long = 0,
    val incomeCount: Int = 0,
    val outgoingTotal: Long = 0,
    val outgoingCount: Int = 0,
) {
    val count: Int get() = incomeCount + outgoingCount
    val net: Long get() = incomeTotal - outgoingTotal
}

/**
 * Pure builder for the plain-text shift handover summary the staff shares (Telegram/Zalo/SMS).
 * No Android types so it is unit-tested directly. Reuses [MoneyFormat] for the signed amounts.
 */
object ShiftSummary {

    private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    /** Aggregate [rows] (every transaction in the shift) into [ShiftUiState]. */
    fun aggregate(startedAt: Long?, rows: List<TransactionModel>): ShiftUiState {
        if (startedAt == null) return ShiftUiState(active = false)
        val income = rows.filter { it.isIncome }
        val outgoing = rows.filter { !it.isIncome }
        return ShiftUiState(
            active = true,
            startedAt = startedAt,
            incomeTotal = income.sumOf { it.amount },
            incomeCount = income.size,
            outgoingTotal = outgoing.sumOf { it.amount },
            outgoingCount = outgoing.size,
        )
    }

    /** A shareable handover message covering [startedAt]..[endedAt]. */
    fun build(startedAt: Long, endedAt: Long, rows: List<TransactionModel>, zone: ZoneId): String {
        val s = aggregate(startedAt, rows)
        val start = Instant.ofEpochMilli(startedAt).atZone(zone)
        val end = Instant.ofEpochMilli(endedAt).atZone(zone)
        val window = if (start.toLocalDate() == end.toLocalDate()) {
            "${DATE_TIME.format(start)} → ${TIME.format(end)}"
        } else {
            "${DATE_TIME.format(start)} → ${DATE_TIME.format(end)}"
        }
        return buildString {
            appendLine("📊 CHỐT CA — Loa Loa Loa")
            appendLine("Ca: $window")
            appendLine("Tiền vào: ${MoneyFormat.format(s.incomeTotal, true)} (${s.incomeCount} giao dịch)")
            appendLine("Tiền ra: ${MoneyFormat.format(s.outgoingTotal, false)} (${s.outgoingCount} giao dịch)")
            append("Chênh lệch: ${MoneyFormat.format(s.net, s.net >= 0)}")
        }
    }
}
