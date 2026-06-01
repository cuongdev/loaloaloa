package com.tingting.notifier.webhook

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tingting.notifier.di.IoDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/**
 * Reliable outbound delivery of a single webhook payload. The payload, target URL, and
 * optional shared secret are passed via [inputData] by [WebhookSender]; this worker just
 * POSTs the body and maps the outcome to a WorkManager [Result]:
 *
 * - HTTP 2xx → [Result.success]
 * - HTTP 5xx or any IO/transient error → [Result.retry] (until [MAX_ATTEMPTS], then give up)
 * - HTTP 4xx (and other non-retryable codes) → [Result.failure]
 *
 * Backoff + the network constraint are set on the request by [WebhookSender]. Framework
 * glue (network side effects); the payload shape and fire decision are unit-tested in
 * [WebhookPayload]/[WebhookPolicy].
 */
@HiltWorker
class WebhookWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val url = inputData.getString(KEY_URL)
        val payloadJson = inputData.getString(KEY_PAYLOAD_JSON)
        if (url.isNullOrBlank() || payloadJson.isNullOrBlank()) {
            Timber.w("Webhook work missing url/payload; failing permanently")
            return@withContext Result.failure()
        }
        val secret = inputData.getString(KEY_SECRET).orEmpty()

        val body = payloadJson.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .apply { if (secret.isNotEmpty()) addHeader(HEADER_SECRET, secret) }
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Result.success()
                    response.code in 500..599 -> retryOrGiveUp("HTTP ${response.code}")
                    else -> {
                        Timber.w("Webhook rejected (HTTP %d); giving up", response.code)
                        Result.failure()
                    }
                }
            }
        } catch (e: IOException) {
            retryOrGiveUp("IO: ${e.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Webhook delivery error; giving up")
            Result.failure()
        }
    }

    /** Retry transient failures until the attempt cap, then give up so work doesn't loop forever. */
    private fun retryOrGiveUp(reason: String): Result =
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
            Timber.w("Webhook delivery failed (%s) after %d attempts; giving up", reason, runAttemptCount + 1)
            Result.failure()
        } else {
            Timber.d("Webhook delivery transient failure (%s); retrying", reason)
            Result.retry()
        }

    companion object {
        const val KEY_URL = "url"
        const val KEY_SECRET = "secret"
        const val KEY_PAYLOAD_JSON = "payloadJson"

        const val HEADER_SECRET = "X-Webhook-Secret"

        /** Give up after this many attempts (≈ the spec's "~5"). */
        const val MAX_ATTEMPTS = 5

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
