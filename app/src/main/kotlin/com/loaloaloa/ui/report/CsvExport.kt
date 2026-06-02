package com.loaloaloa.ui.report

import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.ui.util.TimeRanges
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure CSV serialization of the transactions behind a report period. No Android types,
 * deterministic for a fixed [zone] — trivially unit-testable; the screen only handles the
 * file write + share intent.
 *
 * Output is RFC 4180 (CRLF rows, fields quoted only when they contain a delimiter, quote
 * or newline) and is prefixed with a UTF-8 BOM so Excel opens Vietnamese text correctly.
 */
object CsvExport {

    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val FILE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val FILE_MONTH = DateTimeFormatter.ofPattern("yyyyMM")

    private const val BOM = "﻿"
    private const val EOL = "\r\n"
    private val HEADER = listOf("Ngày", "Giờ", "Loại", "Ngân hàng/Ví", "Số tiền", "Nội dung")

    /** All [rows] for the period, oldest first, as a single CSV document (incl. BOM + header). */
    fun build(rows: List<TransactionModel>, zone: ZoneId): String {
        val sb = StringBuilder(BOM)
        sb.append(HEADER.joinToString(",", postfix = EOL) { escape(it) })
        rows.sortedBy { it.timestamp }.forEach { t ->
            val dt = Instant.ofEpochMilli(t.timestamp).atZone(zone)
            val cols = listOf(
                dt.toLocalDate().format(DATE),
                dt.toLocalTime().format(TIME),
                if (t.isIncome) "Thu" else "Chi",
                t.bankName,
                t.amount.toString(),
                t.rawText,
            )
            sb.append(cols.joinToString(",", postfix = EOL) { escape(it) })
        }
        return sb.toString()
    }

    /** Stable, period-scoped filename, e.g. `baocao-loaloaloa-20260602.csv`. */
    fun fileName(period: ReportPeriod, today: LocalDate): String {
        val tag = when (period) {
            ReportPeriod.TODAY -> today.format(FILE_DAY)
            ReportPeriod.WEEK -> "tuan-" + TimeRanges.mondayOf(today).format(FILE_DAY)
            ReportPeriod.MONTH -> today.format(FILE_MONTH)
        }
        return "baocao-loaloaloa-$tag.csv"
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
