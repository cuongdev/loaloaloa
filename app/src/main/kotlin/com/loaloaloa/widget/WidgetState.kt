package com.loaloaloa.widget

import com.loaloaloa.data.model.TransactionRecord
import com.loaloaloa.ui.util.DateLabels
import com.loaloaloa.ui.util.MoneyFormat
import com.loaloaloa.ui.util.TimeRanges
import java.time.LocalDate
import java.time.ZoneId

/**
 * The display strings the [LoaLoaLoaWidget] renders, derived purely from the inputs
 * the widget loads (history, today, service flag). Kept free of Glance/Android types
 * so the formatting + selection logic is unit-tested directly ([WidgetStateTest]),
 * leaving only framework glue in the Glance layer.
 *
 * Mirrors the design in /tmp/widget_full.png:
 *   - [todayTotal]   big "Hôm nay" hero, the sum of *today's income* (matches Home).
 *   - [latestLine]   "{bank} • {±amount}" for the newest record (null = empty history).
 *   - [latestTime]   "HH:mm" of that newest record.
 *   - [latestBadge]  1–2 char bank initials for the round badge ("MB", "?" when blank).
 *   - [serviceEnabled] drives the green "Đang hoạt động" status dot.
 */
data class WidgetState(
    val serviceEnabled: Boolean,
    val todayTotal: String,
    val latestLine: String?,
    val latestTime: String?,
    val latestBadge: String,
) {
    companion object {

        /** Empty-history default; shown before any transaction arrives. */
        fun empty(serviceEnabled: Boolean) = WidgetState(
            serviceEnabled = serviceEnabled,
            todayTotal = MoneyFormat.format(0, isIncome = true),
            latestLine = null,
            latestTime = null,
            latestBadge = "?",
        )

        /**
         * Build the widget state from [records] (newest-first, as [observeRecords] yields),
         * the reference [today]/[zone], and the master [serviceEnabled] flag.
         */
        fun from(
            records: List<TransactionRecord>,
            today: LocalDate,
            zone: ZoneId,
            serviceEnabled: Boolean,
        ): WidgetState {
            val range = TimeRanges.dayRange(today, zone)
            val todayIncome = records
                .map { it.transaction }
                .filter { it.isIncome && it.timestamp in range }
                .sumOf { it.amount }

            val latest = records.firstOrNull()?.transaction
                ?: return WidgetState(
                    serviceEnabled = serviceEnabled,
                    todayTotal = MoneyFormat.format(todayIncome, isIncome = true),
                    latestLine = null,
                    latestTime = null,
                    latestBadge = "?",
                )

            return WidgetState(
                serviceEnabled = serviceEnabled,
                todayTotal = MoneyFormat.format(todayIncome, isIncome = true),
                latestLine = "${latest.bankName} • ${MoneyFormat.format(latest.amount, latest.isIncome)}",
                latestTime = DateLabels.timeLabel(latest.timestamp, zone),
                latestBadge = badgeOf(latest.bankName),
            )
        }

        /** Up to two leading letters/digits of [bankName], uppercased; "?" when blank. */
        private fun badgeOf(bankName: String): String {
            val trimmed = bankName.trim()
            if (trimmed.isEmpty()) return "?"
            return trimmed.take(2).uppercase()
        }
    }
}
