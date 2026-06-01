package com.tingting.notifier.source.notification

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationProcessorTest {

    private val processor = NotificationProcessor()

    @Test fun `non-bank package returns null`() {
        val result = processor.process(
            packageName = "com.facebook.katana",
            title = "Bank",
            text = "+500.000đ",
            bigText = null,
            timestampMillis = 1_000,
        )
        assertThat(result).isNull()
    }

    @Test fun `bank income notification maps to TransactionModel`() {
        val result = processor.process(
            packageName = "com.VCB",
            title = "Vietcombank",
            text = "+500.000đ",
            bigText = null,
            timestampMillis = 1_717_200_000_000,
        )!!
        assertThat(result.appId).isEqualTo("com.VCB")
        assertThat(result.bankName).isEqualTo("Vietcombank")
        assertThat(result.amount).isEqualTo(500_000)
        assertThat(result.isIncome).isTrue()
        assertThat(result.timestamp).isEqualTo(1_717_200_000_000)
        assertThat(result.rawText).contains("+500.000đ")
        assertThat(result.rawText).contains("Vietcombank")
    }

    @Test fun `outgoing notification has isIncome false`() {
        val result = processor.process(
            packageName = "com.mbmobile",
            title = "MB Bank",
            text = "- 1,200,000 VND",
            bigText = null,
            timestampMillis = 2_000,
        )!!
        assertThat(result.bankName).isEqualTo("MB Bank")
        assertThat(result.amount).isEqualTo(1_200_000)
        assertThat(result.isIncome).isFalse()
    }

    @Test fun `unparseable bank notification returns null`() {
        val result = processor.process(
            packageName = "com.VCB",
            title = "Vietcombank",
            text = "Khuyến mãi tháng 6, không có số tiền",
            bigText = null,
            timestampMillis = 3_000,
        )
        assertThat(result).isNull()
    }

    @Test fun `combines title text and bigText into rawText`() {
        val result = processor.process(
            packageName = "com.VCB",
            title = "Vietcombank",
            text = "Biến động số dư",
            bigText = "TK 0123456789(VND) +750,000 noi dung CK",
            timestampMillis = 4_000,
        )!!
        assertThat(result.amount).isEqualTo(750_000)
        assertThat(result.rawText).contains("Vietcombank")
        assertThat(result.rawText).contains("Biến động số dư")
        assertThat(result.rawText).contains("TK 0123456789(VND) +750,000 noi dung CK")
    }

    @Test fun `excluded bill reminder returns null`() {
        val result = processor.process(
            packageName = "com.VCB",
            title = "Vietcombank",
            text = "Đến kỳ thanh toán 1.000.000 VND",
            bigText = null,
            timestampMillis = 5_000,
        )
        assertThat(result).isNull()
    }
}
