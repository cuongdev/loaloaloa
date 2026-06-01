package com.tingting.notifier.webhook

import com.tingting.notifier.data.model.WebhookConfig
import com.tingting.notifier.di.IoDispatcher
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
 * [com.tingting.notifier.ui.settings.WebhookViewModel] can be unit-tested with a fake
 * (mirrors [com.tingting.notifier.source.api.SePayConnectionTester]).
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
            require(config.url.isNotBlank()) { "Chưa nhập URL webhook" }

            val iso = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val payloadJson = samplePayload(iso).toJson()
            val request = Request.Builder()
                .url(config.url.trim())
                .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                .apply {
                    if (config.secret.isNotEmpty()) addHeader(WebhookWorker.HEADER_SECRET, config.secret)
                }
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.code
            }
        }
    }

    /** A fixed, recognizable example transaction so the receiver can verify the wiring. */
    private fun samplePayload(iso: String) = WebhookPayload(
        bank = "Vietcombank",
        appId = "com.tingting.sample",
        amount = 50_000L,
        type = "in",
        content = "Giao dịch thử nghiệm TingTing",
        timestamp = iso,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
