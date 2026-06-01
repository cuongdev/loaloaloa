package com.tingting.notifier.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransactionParserTest {

    private val parser = TransactionParser()

    // --- R6: amount + đ symbol ---
    @Test fun `plus dong symbol is income`() {
        val r = parser.parse("+500.000đ")!!
        assertThat(r.amount).isEqualTo(500_000)
        assertThat(r.isIncome).isTrue()
    }

    // --- R5: sign + amount + VND ---
    @Test fun `minus VND is outgoing`() {
        val r = parser.parse("- 1,200,000 VND")!!
        assertThat(r.amount).isEqualTo(1_200_000)
        assertThat(r.isIncome).isFalse()
    }

    // --- R1: balance-change line "TK xxxx(VND) +amount" ---
    @Test fun `balance change line`() {
        val r = parser.parse("TK 0123456789(VND) +500,000 Tai khoan thu huong")!!
        assertThat(r.amount).isEqualTo(500_000)
        assertThat(r.isIncome).isTrue()
    }

    // --- R2: "PS: +amount" / "So tien GD: +amount" ---
    @Test fun `PS prefix`() {
        val r = parser.parse("PS: +50000 So du: 1.000.000")!!
        assertThat(r.amount).isEqualTo(50_000)
        assertThat(r.isIncome).isTrue()
    }

    // --- R3 alt B: "VND(+)amount" ---
    @Test fun `VND with parenthesised sign`() {
        val r = parser.parse("So tien VND(+)500000")!!
        assertThat(r.amount).isEqualTo(500_000)
        assertThat(r.isIncome).isTrue()
    }

    // --- R3 alt A: "+VND amount" ---
    @Test fun `sign then VND then amount`() {
        val r = parser.parse("Giao dich +VND 750000 thanh cong")!!
        assertThat(r.amount).isEqualTo(750_000)
        assertThat(r.isIncome).isTrue()
    }

    // --- R4: increase keyword ---
    @Test fun `tang keyword is income`() {
        val r = parser.parse("So du tang 500.000 VND vao luc 10:00")!!
        assertThat(r.amount).isEqualTo(500_000)
        assertThat(r.isIncome).isTrue()
    }

    @Test fun `giam keyword is outgoing`() {
        val r = parser.parse("So du giam 200.000 VND")!!
        assertThat(r.amount).isEqualTo(200_000)
        assertThat(r.isIncome).isFalse()
    }

    // --- R7: "Số tiền: amount" with no sign defaults to income ---
    @Test fun `amount label without sign defaults to income`() {
        val r = parser.parse("So tien: 500.000d")!!
        assertThat(r.amount).isEqualTo(500_000)
        assertThat(r.isIncome).isTrue()
    }

    // --- spaces inside number (R6 allows spaces) ---
    @Test fun `spaced thousands grouping`() {
        val r = parser.parse("+1 200 000đ")!!
        assertThat(r.amount).isEqualTo(1_200_000)
    }

    // --- exclusions: bill reminders are not received money ---
    @Test fun `bill reminder is ignored`() {
        assertThat(parser.parse("Nhac no: hoa don tien dien 500.000d")).isNull()
        assertThat(parser.parse("Đến kỳ thanh toán 1.000.000 VND")).isNull()
    }

    // --- noise returns null ---
    @Test fun `unrelated text returns null`() {
        assertThat(parser.parse("Ban co mot tin nhan moi")).isNull()
        assertThat(parser.parse("")).isNull()
    }
}
