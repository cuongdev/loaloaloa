package com.loaloaloa.relay

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.loaloaloa.di.IoDispatcher
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
 * Reliable delivery of one relay push to the Sender. The pre-encrypted body, target URL, and HMAC
 * signature are passed via [inputData] by [RelaySender]; this worker just POSTs the body with the
 * signature header and maps the outcome to a WorkManager [Result]:
 *
 * - HTTP 2xx → [Result.success]
 * - HTTP 5xx or any IO/transient error → [Result.retry] (until [MAX_ATTEMPTS], then give up)
 * - HTTP 4xx (rejected signature, unknown room) → [Result.failure]
 *
 * Framework glue mirroring [com.loaloaloa.webhook.WebhookWorker]; the encryption, payload
 * shape, and signing it carries are unit-tested in [RelayCrypto]/[RelayPayload].
 */
@HiltWorker
class RelayWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        val url = inputData.getString(KEY_URL)
        val body = inputData.getString(KEY_BODY)
        val signature = inputData.getString(KEY_SIGNATURE)
        if (url.isNullOrBlank() || body.isNullOrBlank() || signature.isNullOrBlank()) {
            Timber.w("Relay work missing url/body/signature; failing permanently")
            return@withContext Result.failure()
        }

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_CONTENT_TYPE.toMediaType()))
            .addHeader(HEADER_SIGNATURE, signature)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Result.success()
                    response.code in 500..599 -> retryOrGiveUp("HTTP ${response.code}")
                    else -> {
                        Timber.w("Relay rejected (HTTP %d); giving up", response.code)
                        Result.failure()
                    }
                }
            }
        } catch (e: IOException) {
            retryOrGiveUp("IO: ${e.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Relay delivery error; giving up")
            Result.failure()
        }
    }

    /** Retry transient failures until the attempt cap, then give up so work doesn't loop forever. */
    private fun retryOrGiveUp(reason: String): Result =
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
            Timber.w("Relay delivery failed (%s) after %d attempts; giving up", reason, runAttemptCount + 1)
            Result.failure()
        } else {
            Timber.d("Relay delivery transient failure (%s); retrying", reason)
            Result.retry()
        }

    companion object {
        const val KEY_URL = "url"
        const val KEY_BODY = "body"
        const val KEY_SIGNATURE = "signature"

        const val HEADER_SIGNATURE = "X-Relay-Signature"
        private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"

        /** Give up after this many attempts (mirrors the webhook worker). */
        const val MAX_ATTEMPTS = 5
    }
}
