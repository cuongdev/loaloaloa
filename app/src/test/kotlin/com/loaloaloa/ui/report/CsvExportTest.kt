package com.loaloaloa.ui.report

import com.loaloaloa.data.model.TransactionModel
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    /** 2026-06-02 09:14 local in Asia/Ho_Chi_Minh (UTC+7). */
    private fun ts(date: String, time: String): Long =
        LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun tx(
        amount: Long,
        isIncome: Boolean = true,
        bank: String = "MB Bank",
        raw: String = "Giao dich",
        at: Long,
    ) = TransactionModel("com.mbmobile", bank, amount, isIncome, raw, at)

    @Test
    fun `starts with UTF-8 BOM then header`() {
        val csv = CsvExport.build(emptyList(), zone)
        assertTrue("must begin with BOM", csv.startsWith("﻿"))
        assertEquals("﻿Ngày,Giờ,Loại,Ngân hàng/Ví,Số tiền,Nội dung\r\n", csv)
    }

    @Test
    fun `rows are emitted oldest first with Thu Chi and raw amount`() {
        val later = tx(320_000, isIncome = false, raw = "Chuyen tien", at = ts("2026-06-02", "10:02"))
        val earlier = tx(150_000, isIncome = true, raw = "Nhan tien", at = ts("2026-06-02", "09:14"))

        val lines = CsvExport.build(listOf(later, earlier), zone).split("\r\n")

        assertEquals("02/06/2026,09:14,Thu,MB Bank,150000,Nhan tien", lines[1])
        assertEquals("02/06/2026,10:02,Chi,MB Bank,320000,Chuyen tien", lines[2])
    }

    @Test
    fun `quotes and escapes fields containing comma quote or newline`() {
        val row = tx(50_000, raw = "TT \"ABC\", noi dung\nxuong dong", at = ts("2026-06-02", "08:00"))

        val line = CsvExport.build(listOf(row), zone).split("\r\n")[1]

        // amount has no separators; raw is wrapped in quotes with doubled inner quotes, newline kept.
        assertTrue(line.endsWith("\"TT \"\"ABC\"\", noi dung\nxuong dong\""))
    }

    @Test
    fun `fileName is scoped by period`() {
        val today = LocalDate.parse("2026-06-02") // a Tuesday
        assertEquals("baocao-loaloaloa-20260602.csv", CsvExport.fileName(ReportPeriod.TODAY, today))
        assertEquals("baocao-loaloaloa-tuan-20260601.csv", CsvExport.fileName(ReportPeriod.WEEK, today))
        assertEquals("baocao-loaloaloa-202606.csv", CsvExport.fileName(ReportPeriod.MONTH, today))
    }
}
