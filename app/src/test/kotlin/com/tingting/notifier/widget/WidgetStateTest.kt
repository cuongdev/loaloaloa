package com.tingting.notifier.widget

import com.google.common.truth.Truth.assertThat
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.TransactionRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class WidgetStateTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val today = LocalDate.of(2026, 6, 1)

    // Timestamps relative to the 2026-06-01 local day (+07).
    private val midToday = Instant.parse("2026-06-01T03:00:00Z").toEpochMilli() // 10:00 local
    private val earlyToday = Instant.parse("2026-06-01T01:00:00Z").toEpochMilli() // 08:00 local
    private val yesterday = Instant.parse("2026-05-31T03:00:00Z").toEpochMilli()

    private fun rec(id: Long, amount: Long, isIncome: Boolean, ts: Long, bank: String = "Vietcombank") =
        TransactionRecord(id, TransactionModel("com.bank", bank, amount, isIncome, "raw", ts))

    @Test fun `empty history shows zero total and no latest line`() {
        val state = WidgetState.from(emptyList(), today, zone, serviceEnabled = true)
        assertThat(state.serviceEnabled).isTrue()
        assertThat(state.todayTotal).isEqualTo("+0 đ")
        assertThat(state.latestLine).isNull()
        assertThat(state.latestTime).isNull()
        assertThat(state.latestBadge).isEqualTo("?")
    }

    @Test fun `empty factory mirrors empty history with the given service flag`() {
        val state = WidgetState.empty(serviceEnabled = false)
        assertThat(state.serviceEnabled).isFalse()
        assertThat(state.todayTotal).isEqualTo("+0 đ")
        assertThat(state.latestLine).isNull()
        assertThat(state.latestBadge).isEqualTo("?")
    }

    @Test fun `today total sums only today's income`() {
        val records = listOf(
            rec(4, 150_000, isIncome = true, ts = midToday),
            rec(3, 500_000, isIncome = true, ts = earlyToday),
            rec(2, 99_000, isIncome = false, ts = midToday),   // outgoing → excluded
            rec(1, 1_000_000, isIncome = true, ts = yesterday), // not today → excluded
        )
        val state = WidgetState.from(records, today, zone, serviceEnabled = true)
        assertThat(state.todayTotal).isEqualTo("+650.000 đ")
    }

    @Test fun `latest line uses the newest record with income sign`() {
        val records = listOf(
            rec(2, 500_000, isIncome = true, ts = midToday, bank = "MB Bank"),
            rec(1, 250_000, isIncome = true, ts = earlyToday, bank = "Vietcombank"),
        )
        val state = WidgetState.from(records, today, zone, serviceEnabled = true)
        assertThat(state.latestLine).isEqualTo("MB Bank • +500.000 đ")
        assertThat(state.latestTime).isEqualTo("10:00")
        assertThat(state.latestBadge).isEqualTo("MB")
    }

    @Test fun `latest line shows minus sign for outgoing newest record`() {
        val records = listOf(rec(1, 1_100_000, isIncome = false, ts = midToday, bank = "Techcombank"))
        val state = WidgetState.from(records, today, zone, serviceEnabled = false)
        assertThat(state.latestLine).isEqualTo("Techcombank • −1.100.000 đ")
        // Outgoing is not income, so today's income total stays zero.
        assertThat(state.todayTotal).isEqualTo("+0 đ")
    }

    @Test fun `service flag is carried through`() {
        val on = WidgetState.from(listOf(rec(1, 1, true, midToday)), today, zone, serviceEnabled = true)
        val off = WidgetState.from(listOf(rec(1, 1, true, midToday)), today, zone, serviceEnabled = false)
        assertThat(on.serviceEnabled).isTrue()
        assertThat(off.serviceEnabled).isFalse()
    }

    @Test fun `badge takes up to two uppercased leading chars`() {
        assertThat(WidgetState.from(listOf(rec(1, 1, true, midToday, "vietcombank")), today, zone, true).latestBadge)
            .isEqualTo("VI")
        assertThat(WidgetState.from(listOf(rec(1, 1, true, midToday, "M")), today, zone, true).latestBadge)
            .isEqualTo("M")
    }

    @Test fun `blank bank name yields question mark badge`() {
        val state = WidgetState.from(listOf(rec(1, 200_000, true, midToday, bank = "")), today, zone, true)
        assertThat(state.latestBadge).isEqualTo("?")
        assertThat(state.latestLine).isEqualTo(" • +200.000 đ")
    }
}
