package com.loaloaloa.source.notification

import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.parser.BankRegistry
import com.loaloaloa.parser.TransactionParser

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
     * @param customApps user-added sources (package name → display name) that should be parsed in
     *   addition to the built-in [BankRegistry] whitelist.
     * @return a [TransactionModel] when [packageName] is a supported bank or a user-added app, and
     *   the combined notification text parses to an amount; otherwise null.
     */
    fun process(
        packageName: String,
        title: String?,
        text: String?,
        bigText: String?,
        timestampMillis: Long,
        customApps: Map<String, String> = emptyMap(),
    ): TransactionModel? {
        // Resolve the display name; a package known to neither source isn't a transaction source.
        val displayName = BankRegistry.nameFor(packageName) ?: customApps[packageName] ?: return null

        // `text` and `bigText` are frequently the same string, or `bigText` is the expanded
        // form of a truncated `text`; collapse them so the body isn't repeated in rawText.
        val body = when {
            text.isNullOrBlank() -> bigText
            bigText.isNullOrBlank() -> text
            bigText.contains(text) -> bigText
            text.contains(bigText) -> text
            else -> "$text\n$bigText"
        }
        val combined = listOfNotNull(title, body)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
        if (combined.isBlank()) return null

        val parsed = parser.parse(combined) ?: return null

        return TransactionModel(
            appId = packageName,
            bankName = displayName,
            amount = parsed.amount,
            isIncome = parsed.isIncome,
            rawText = combined,
            timestamp = timestampMillis,
        )
    }
}
