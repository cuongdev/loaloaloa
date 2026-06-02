package com.loaloaloa.source.api

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class SePayNormalizerTest {

    private val normalizer = SePayNormalizer()
    private val fallback = 1_700_000_000_000L

    private fun dto(
        id: String? = "1",
        bankBrandName: String? = "Vietcombank",
        transactionDate: String? = "2024-01-15 10:30:00",
        amountIn: String? = "0",
        amountOut: String? = "0",
        transactionContent: String? = "thanh toan",
    ) = SePayTransactionDto(
        id = id,
        bankBrandName = bankBrandName,
        accountNumber = "0123",
        transactionDate = transactionDate,
        amountIn = amountIn,
        amountOut = amountOut,
        transactionContent = transactionContent,
        referenceNumber = "ref",
    )

    @Test fun `income row maps to income with amount_in`() {
        val model = normalizer.normalize(dto(amountIn = "500000", amountOut = "0"), fallback)
        assertThat(model).isNotNull()
        assertThat(model!!.isIncome).isTrue()
        assertThat(model.amount).isEqualTo(500_000L)
    }

    @Test fun `outgoing row maps to outgoing with amount_out`() {
        val model = normalizer.normalize(dto(amountIn = "0", amountOut = "200000"), fallback)
        assertThat(model).isNotNull()
        assertThat(model!!.isIncome).isFalse()
        assertThat(model.amount).isEqualTo(200_000L)
    }

    @Test fun `both zero yields null`() {
        assertThat(normalizer.normalize(dto(amountIn = "0", amountOut = "0"), fallback)).isNull()
    }

    @Test fun `both null amounts yield null`() {
        assertThat(normalizer.normalize(dto(amountIn = null, amountOut = null), fallback)).isNull()
    }

    @Test fun `dotted thousands string parses to plain long`() {
        val model = normalizer.normalize(dto(amountIn = "500.000", amountOut = "0"), fallback)
        assertThat(model!!.amount).isEqualTo(500_000L)
    }

    @Test fun `plain digit string parses to long`() {
        val model = normalizer.normalize(dto(amountIn = "500000", amountOut = "0"), fallback)
        assertThat(model!!.amount).isEqualTo(500_000L)
    }

    @Test fun `income takes precedence when both positive`() {
        val model = normalizer.normalize(dto(amountIn = "300000", amountOut = "100000"), fallback)
        assertThat(model!!.isIncome).isTrue()
        assertThat(model.amount).isEqualTo(300_000L)
    }

    @Test fun `valid date parses to local epoch millis`() {
        val model = normalizer.normalize(
            dto(transactionDate = "2024-01-15 10:30:00", amountIn = "1000", amountOut = "0"),
            fallback,
        )
        val expected = LocalDateTime.of(2024, 1, 15, 10, 30, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertThat(model!!.timestamp).isEqualTo(expected)
    }

    @Test fun `bad date falls back to provided millis`() {
        val model = normalizer.normalize(
            dto(transactionDate = "not a date", amountIn = "1000", amountOut = "0"),
            fallback,
        )
        assertThat(model!!.timestamp).isEqualTo(fallback)
    }

    @Test fun `null date falls back to provided millis`() {
        val model = normalizer.normalize(
            dto(transactionDate = null, amountIn = "1000", amountOut = "0"),
            fallback,
        )
        assertThat(model!!.timestamp).isEqualTo(fallback)
    }

    @Test fun `null bank name and content default to empty, appId is sepay`() {
        val model = normalizer.normalize(
            dto(bankBrandName = null, transactionContent = null, amountIn = "1000", amountOut = "0"),
            fallback,
        )
        assertThat(model!!.bankName).isEqualTo("")
        assertThat(model.rawText).isEqualTo("")
        assertThat(model.appId).isEqualTo("sepay")
    }
}
