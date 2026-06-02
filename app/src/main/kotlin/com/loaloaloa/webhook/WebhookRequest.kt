package com.loaloaloa.webhook

import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.WebhookConfig
import com.loaloaloa.data.model.WebhookType
import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** POST content types. Google Form needs form-urlencoded; every other destination uses JSON. */
const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=utf-8"

/**
 * A ready-to-deliver HTTP request derived from a [WebhookConfig] + transaction: the final
 * [url], the POST [body], its [contentType] (JSON, or form-urlencoded for Google Form), and an
 * optional [secret] sent as `X-Webhook-Secret`. Keeping this a pure value lets [WebhookSender]
 * and [OkHttpWebhookTester] share one builder.
 */
data class WebhookRequest(
    val url: String,
    val body: String,
    val secret: String = "",
    val contentType: String = JSON_CONTENT_TYPE,
)

/**
 * Build the outbound request for [config] + [model], or null when the config is missing the
 * fields its type needs (so callers just skip it). [isoTimestamp] is used for the generic
 * JSON payload's `timestamp`.
 */
fun buildWebhookRequest(config: WebhookConfig, model: TransactionModel, isoTimestamp: String): WebhookRequest? =
    when (config.type) {
        WebhookType.TELEGRAM -> {
            val token = config.botToken.trim()
            val chat = config.chatId.trim()
            if (token.isEmpty() || chat.isEmpty()) {
                null
            } else {
                WebhookRequest(
                    url = "https://api.telegram.org/bot$token/sendMessage",
                    body = telegramBody(chat, telegramText(model)),
                )
            }
        }

        WebhookType.GENERIC, WebhookType.GOOGLE_SHEET -> {
            val url = config.url.trim()
            if (url.isEmpty()) {
                null
            } else {
                WebhookRequest(
                    url = url,
                    body = buildPayload(model, isoTimestamp).toJson(),
                    secret = config.secret.trim(),
                )
            }
        }

        WebhookType.GOOGLE_FORM -> {
            val url = config.url.trim()
            // Map each transaction value onto its configured entry.* id; drop unmapped ones.
            val fields = listOf(
                config.formAmountEntry.trim() to model.amount.toString(),
                config.formBankEntry.trim() to model.bankName,
                config.formTimeEntry.trim() to isoTimestamp,
                config.formNoteEntry.trim() to model.rawText,
            ).filter { (entry, _) -> entry.isNotEmpty() }
            if (url.isEmpty() || fields.isEmpty()) {
                null
            } else {
                WebhookRequest(
                    url = url,
                    body = fields.joinToString("&") { (entry, value) ->
                        "${formEncode(entry)}=${formEncode(value)}"
                    },
                    contentType = FORM_CONTENT_TYPE,
                )
            }
        }
    }

/** `application/x-www-form-urlencoded` encoding of a single Google Form key or value. */
private fun formEncode(s: String): String = URLEncoder.encode(s, "UTF-8")

// --- Telegram message ---------------------------------------------------------

private val telegramJson = Json { encodeDefaults = true }

@Serializable
private data class TelegramSendMessage(
    val chat_id: String,
    val text: String,
    val parse_mode: String = "HTML",
)

private fun telegramBody(chatId: String, text: String): String =
    telegramJson.encodeToString(TelegramSendMessage.serializer(), TelegramSendMessage(chatId, text))

private const val TELEGRAM_DIVIDER = "--------------"

/**
 * Shop-friendly Vietnamese Telegram card; dynamic parts are HTML-escaped for `parse_mode=HTML`.
 * Fixed template:
 *   💰 Giao dịch mới
 *   --------------
 *   🗓 Ngày: dd/MM/yyyy HH:mm
 *   💳 Tài khoản: <masked>          (only when an account can be recovered from the notification)
 *   💰 Số tiền: 20.000 ₫
 *   📝 Nội dung: <raw notification text>
 *   --------------
 */
private fun telegramText(model: TransactionModel): String = buildString {
    append("💰 Giao dịch mới").append('\n')
    append(TELEGRAM_DIVIDER).append('\n')
    append("🗓 Ngày: ").append(formatDateTime(model.timestamp)).append('\n')
    extractAccount(model.rawText)?.let { acct ->
        append("💳 Tài khoản: ").append(htmlEscape(acct)).append('\n')
    }
    append("💰 Số tiền: ").append(formatVnd(model.amount)).append('\n')
    if (model.rawText.isNotBlank()) {
        append("📝 Nội dung: ").append(htmlEscape(model.rawText)).append('\n')
    }
    append(TELEGRAM_DIVIDER)
}

/** "500000" -> "500.000 ₫" (Vietnamese grouping + dong sign). */
private fun formatVnd(amount: Long): String = "%,d".format(amount).replace(',', '.') + " ₫"

/** Transaction time in the device's local zone, e.g. "01/06/2026 22:03". */
private fun formatDateTime(ts: Long): String =
    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("vi")).format(java.util.Date(ts))

private val accountLabelRegex =
    Regex("""(?:t[àaă]i\s*kho[ảa]n|tk|stk|s[ốo]\s*tk|account|acct)\s*[:\-]?\s*([Xx*]{2,}\d{2,}|\d{6,})""", RegexOption.IGNORE_CASE)
private val maskedAccountRegex = Regex("""[Xx*]{3,}\d{2,}""")

/**
 * Best-effort account-number recovery from the raw notification text — the [TransactionModel]
 * has no dedicated account field. Prefers a labelled "tài khoản/TK/STK …" capture, then a bare
 * masked token (e.g. XXXXXX9001). Returns null when nothing account-like is present, so the
 * "💳 Tài khoản" line is simply omitted rather than showing a wrong value.
 */
private fun extractAccount(raw: String): String? =
    accountLabelRegex.find(raw)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: maskedAccountRegex.find(raw)?.value

private fun htmlEscape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
