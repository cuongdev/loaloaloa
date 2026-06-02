package com.loaloaloa.ui.shift

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.TransactionModel
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class ShiftSummaryTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun tx(amount: Long, isIncome: Boolean, at: Long) = TransactionModel(
        appId = "com.test",
        bankName = "MB Bank",
        amount = amount,
        isIncome = isIncome,
        rawText = "raw",
        timestamp = at,
    )

    @Test fun `aggregate splits income and outgoing with counts and net`() {
        val rows = listOf(
            tx(500_000, true, millis(2026, 6, 2, 9, 0)),
            tx(200_000, true, millis(2026, 6, 2, 10, 0)),
            tx(120_000, false, millis(2026, 6, 2, 11, 0)),
        )
        val s = ShiftSummary.aggregate(startedAt = millis(2026, 6, 2, 8, 0), rows = rows)
        assertThat(s.active).isTrue()
        assertThat(s.incomeTotal).isEqualTo(700_000)
        assertThat(s.incomeCount).isEqualTo(2)
        assertThat(s.outgoingTotal).isEqualTo(120_000)
        assertThat(s.outgoingCount).isEqualTo(1)
        assertThat(s.count).isEqualTo(3)
        assertThat(s.net).isEqualTo(580_000)
    }

    @Test fun `aggregate with null start is inactive`() {
        assertThat(ShiftSummary.aggregate(null, emptyList()).active).isFalse()
    }

    @Test fun `build renders same-day window and signed totals`() {
        val rows = listOf(
            tx(500_000, true, millis(2026, 6, 2, 9, 0)),
            tx(120_000, false, millis(2026, 6, 2, 11, 0)),
        )
        val text = ShiftSummary.build(
            startedAt = millis(2026, 6, 2, 8, 0),
            endedAt = millis(2026, 6, 2, 17, 30),
            rows = rows,
            zone = zone,
        )
        assertThat(text).contains("Ca: 02/06 08:00 → 17:30")
        assertThat(text).contains("Tiền vào: +500.000 đ (1 giao dịch)")
        assertThat(text).contains("Tiền ra: −120.000 đ (1 giao dịch)")
        assertThat(text).contains("Chênh lệch: +380.000 đ")
    }

    @Test fun `build renders overnight window with both dates and negative net`() {
        val rows = listOf(tx(1_000_000, false, millis(2026, 6, 2, 23, 0)))
        val text = ShiftSummary.build(
            startedAt = millis(2026, 6, 2, 22, 0),
            endedAt = millis(2026, 6, 3, 6, 0),
            rows = rows,
            zone = zone,
        )
        assertThat(text).contains("Ca: 02/06 22:00 → 03/06 06:00")
        assertThat(text).contains("Chênh lệch: −1.000.000 đ")
    }
}
