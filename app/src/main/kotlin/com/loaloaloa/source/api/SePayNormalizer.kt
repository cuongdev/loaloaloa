package com.loaloaloa.source.api

import com.loaloaloa.data.model.TransactionModel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Pure transform from a SePay [SePayTransactionDto] to a normalized
 * [TransactionModel], or null when the row carries no money movement.
 *
 * Amounts arrive as strings (possibly dotted, e.g. "500.000"); they are reduced to
 * digits and parsed to Long. `amount_in > 0` → income with that amount; else
 * `amount_out > 0` → outgoing with that amount; else null. The `transaction_date`
 * ("yyyy-MM-dd HH:mm:ss", treated as local time) is parsed to epoch millis, falling
 * back to [fallbackMillis] (the caller's now) when missing or malformed.
 *
 * Holds no Android or network types, so it is unit-tested on the JVM.
 */
class SePayNormalizer @Inject constructor() {

    /** @return a [TransactionModel] for a money-moving row, or null. */
    fun normalize(dto: SePayTransactionDto, fallbackMillis: Long): TransactionModel? {
        val amountIn = parseAmount(dto.amountIn)
        val amountOut = parseAmount(dto.amountOut)

        val (amount, isIncome) = when {
            amountIn > 0 -> amountIn to true
            amountOut > 0 -> amountOut to false
            else -> return null
        }

        return TransactionModel(
            appId = APP_ID,
            bankName = dto.bankBrandName ?: "",
            amount = amount,
            isIncome = isIncome,
            rawText = dto.transactionContent ?: "",
            timestamp = parseTimestamp(dto.transactionDate, fallbackMillis),
        )
    }

    /** Strip every non-digit and parse; null/blank/non-numeric → 0. */
    private fun parseAmount(raw: String?): Long =
        raw?.filter { it.isDigit() }?.toLongOrNull() ?: 0L

    /** Parse a local "yyyy-MM-dd HH:mm:ss" to epoch millis; any failure → [fallbackMillis]. */
    private fun parseTimestamp(raw: String?, fallbackMillis: Long): Long {
        if (raw.isNullOrBlank()) return fallbackMillis
        return try {
            LocalDateTime.parse(raw, DATE_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            fallbackMillis
        }
    }

    private companion object {
        const val APP_ID = "sepay"
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
