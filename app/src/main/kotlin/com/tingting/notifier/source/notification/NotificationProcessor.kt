package com.tingting.notifier.source.notification

import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.parser.BankRegistry
import com.tingting.notifier.parser.TransactionParser

/**
 * Pure transform from a posted notification's raw fields to a normalized
 * [TransactionModel], or null when the notification is not from a supported bank
 * or carries no recognizable amount. Holds no Android types so it is unit-tested
 * on the JVM. The [parser] is supplied by `SourceModule` (the single Hilt binding)
 * and constructed directly in tests.
 */
class NotificationProcessor(
    private val parser: TransactionParser,
) {
    /**
     * @return a [TransactionModel] when [packageName] is a supported bank and the
     *   combined notification text parses to an amount; otherwise null.
     */
    fun process(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        timestampMillis: Long,
    ): TransactionModel? {
        if (!BankRegistry.isBank(packageName)) return null

        val combined = listOfNotNull(title, text, bigText)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
        if (combined.isBlank()) return null

        val parsed = parser.parse(combined) ?: return null

        return TransactionModel(
            appId = packageName,
            bankName = BankRegistry.nameFor(packageName) ?: packageName,
            amount = parsed.amount,
            isIncome = parsed.isIncome,
            rawText = combined,
            timestamp = timestampMillis,
        )
    }
}
