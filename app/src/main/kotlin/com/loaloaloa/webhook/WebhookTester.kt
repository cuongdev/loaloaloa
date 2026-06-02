package com.loaloaloa.webhook

import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.WebhookConfig
import com.loaloaloa.data.model.WebhookType
import com.loaloaloa.di.IoDispatcher
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * One-shot "Gửi thử" (send test) for the webhook config screen: POSTs a fixed SAMPLE
 * payload to the entered URL off the main thread and returns the HTTP status code, or a
 * failure carrying a short message. A separate seam from [WebhookSender] so the
 * [com.loaloaloa.ui.settings.WebhookViewModel] can be unit-tested with a fake
 * (mirrors [com.loaloaloa.source.api.SePayConnectionTester]).
 *
 * @return on success the HTTP status code; on failure the wrapped exception (its message
 *   is shown to the user).
 */
fun interface WebhookTester {
    suspend fun test(config: WebhookConfig): Result<Int>
}

/** OkHttp-backed [WebhookTester]. Runs entirely on the IO dispatcher. */
class OkHttpWebhookTester @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WebhookTester {

    override suspend fun test(config: WebhookConfig): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            val iso = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val req = buildWebhookRequest(config, SAMPLE_MODEL, iso) ?: error(
                when (config.type) {
                    WebhookType.TELEGRAM -> "Chưa nhập Bot Token và Chat ID"
                    WebhookType.GOOGLE_FORM -> "Chưa dán link Google Form hợp lệ"
                    else -> "Chưa nhập URL webhook"
                },
            )
            val request = Request.Builder()
                .url(req.url)
                .post(req.body.toRequestBody(JSON_MEDIA_TYPE))
                .apply {
                    if (req.secret.isNotEmpty()) addHeader(WebhookWorker.HEADER_SECRET, req.secret)
                }
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.code
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** A fixed, recognizable example transaction so the receiver can verify the wiring. */
        val SAMPLE_MODEL = TransactionModel(
            appId = "com.loaloaloa.sample",
            bankName = "Vietcombank",
            amount = 50_000L,
            isIncome = true,
            rawText = "Giao dịch thử nghiệm Loa Loa Loa",
            timestamp = 0L,
        )
    }
}
