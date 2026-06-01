package com.tingting.notifier.data.model

/**
 * A normalized money-movement event from any [TransactionSource].
 *
 * @param appId   source package name (notification) or provider id (API)
 * @param bankName resolved human-readable bank/wallet name
 * @param amount  absolute VND amount (always >= 0)
 * @param isIncome true = money received (credit), false = money sent (debit)
 * @param rawText  original notification/text, kept for the detail view & debugging
 * @param timestamp epoch millis when observed
 */
data class TransactionModel(
    val appId: String,
    val bankName: String,
    val amount: Long,
    val isIncome: Boolean,
    val rawText: String,
    val timestamp: Long,
)
