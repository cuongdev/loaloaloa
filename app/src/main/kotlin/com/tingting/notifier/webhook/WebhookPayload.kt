package com.tingting.notifier.webhook

import com.tingting.notifier.data.model.TransactionModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The JSON body POSTed to a user's outbound webhook for each detected transaction.
 * Shape is deliberately flat and stable so receivers (Google Sheets / Telegram / a POS
 * / n8n) can map it without nesting.
 *
 * @param gateway   constant source tag.
 * @param bank      resolved bank/wallet name.
 * @param appId     source package name (notification) or provider id (API).
 * @param amount    absolute VND amount (always >= 0), serialized as a JSON number.
 * @param type      `"in"` for money received, `"out"` for money sent.
 * @param content   original notification/transaction text.
 * @param timestamp ISO-8601 instant the transaction was observed.
 */
@Serializable
data class WebhookPayload(
    val gateway: String = "TingTing",
    val bank: String,
    val appId: String,
    val amount: Long,
    val type: String,
    val content: String,
    val timestamp: String,
)

/** Pure mapping from a [TransactionModel] to its webhook [WebhookPayload]. */
fun buildPayload(model: TransactionModel, isoTimestamp: String): WebhookPayload =
    WebhookPayload(
        bank = model.bankName,
        appId = model.appId,
        amount = model.amount,
        type = if (model.isIncome) "in" else "out",
        content = model.rawText,
        timestamp = isoTimestamp,
    )

private val webhookJson = Json { encodeDefaults = true }

/** Serialize this payload to the JSON string sent as the POST body. */
fun WebhookPayload.toJson(): String = webhookJson.encodeToString(WebhookPayload.serializer(), this)
