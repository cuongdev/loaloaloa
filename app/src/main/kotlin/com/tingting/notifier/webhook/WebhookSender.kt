package com.tingting.notifier.webhook

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.WebhookConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the webhook payload for a detected transaction and enqueues a [WebhookWorker]
 * to deliver it. Each transaction is its own one-time work request (non-unique) so
 * bursts of transactions are all delivered. WorkManager handles the network constraint
 * and exponential backoff retry; this class is the impurity boundary that turns the
 * model's epoch-millis timestamp into an ISO-8601 string.
 */
@Singleton
class WebhookSender @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enqueue(model: TransactionModel, config: WebhookConfig) {
        val iso = isoTimestamp(model.timestamp)
        val payloadJson = buildPayload(model, iso).toJson()

        val data = Data.Builder()
            .putString(WebhookWorker.KEY_URL, config.url)
            .putString(WebhookWorker.KEY_SECRET, config.secret)
            .putString(WebhookWorker.KEY_PAYLOAD_JSON, payloadJson)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<WebhookWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun isoTimestamp(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private companion object {
        const val TAG = "tingting_webhook"
        const val BACKOFF_SECONDS = 10L
    }
}
