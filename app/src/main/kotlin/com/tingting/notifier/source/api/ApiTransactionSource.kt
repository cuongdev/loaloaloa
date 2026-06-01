package com.tingting.notifier.source.api

import com.tingting.notifier.data.model.ApiConfig
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.repository.UserSettingsRepository
import com.tingting.notifier.di.IoDispatcher
import com.tingting.notifier.source.TransactionSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

/**
 * Polling [TransactionSource] for SePay-style providers (spec §5.3). [start] launches
 * a coroutine that periodically fetches the recent-transactions list, keeps only rows
 * newer than the persisted last-seen id, normalizes them via [SePayNormalizer], emits
 * each to [transactions], and persists the new cursor. Network errors are logged and
 * the loop continues. [stop] cancels the loop.
 *
 * Dedupe is by provider transaction id (persisted across restarts in
 * [ApiConfig.lastSeenTxnId]) — this is the API source's own dedupe, separate from the
 * notification window dedupe. Both sources then share the [com.tingting.notifier.ingest.TransactionIngestor].
 *
 * This is framework/network glue (Retrofit build + loop); the tested unit is
 * [SePayNormalizer]. Idempotent start/stop.
 */
@Singleton
class ApiTransactionSource @Inject constructor(
    private val userSettingsRepository: UserSettingsRepository,
    private val normalizer: SePayNormalizer,
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TransactionSource {

    private val _transactions = MutableSharedFlow<TransactionModel>(extraBufferCapacity = 64)
    override val transactions: SharedFlow<TransactionModel> = _transactions.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var pollJob: Job? = null

    override fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch { pollLoop() }
    }

    override fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            val config = userSettingsRepository.settings.first().api
            if (!config.enabled || config.baseUrl.isBlank() || config.token.isBlank()) {
                // Source not configured; nothing to poll. Back off and re-check.
                delay(pollDelaySeconds(config).seconds)
                continue
            }
            runCatching { pollOnce(config) }
                .onFailure { Timber.w(it, "SePay poll cycle failed; continuing") }
            delay(pollDelaySeconds(config).seconds)
        }
    }

    private suspend fun pollOnce(config: ApiConfig) {
        val api = buildApi(config)
        val response = api.list(
            bearer = "Bearer ${config.token}",
            account = config.account.ifBlank { null },
            limit = DEFAULT_LIMIT,
        )

        val lastSeen = config.lastSeenTxnId
        var maxSeen = lastSeen
        // Oldest-first so emissions and the persisted cursor advance monotonically.
        val newRows = response.transactions
            .filter { isNewerThanCursor(it.id, lastSeen) }
            .sortedBy { it.id?.toLongOrNull() ?: 0L }

        for (dto in newRows) {
            val now = System.currentTimeMillis()
            val model = normalizer.normalize(dto, fallbackMillis = now) ?: continue
            _transactions.emit(model)
            val id = dto.id
            if (id != null && isNewerThanCursor(id, maxSeen)) maxSeen = id
        }

        if (maxSeen != lastSeen) {
            userSettingsRepository.updateApiLastSeenTxnId(maxSeen)
        }
    }

    /**
     * A row is new when we have no cursor yet, or its id is strictly greater than the
     * cursor. Ids are compared numerically when both parse as Long; otherwise a row is
     * considered new whenever its id differs from the cursor (string fallback).
     */
    private fun isNewerThanCursor(id: String?, cursor: String): Boolean {
        if (id.isNullOrBlank()) return false
        if (cursor.isBlank()) return true
        val idNum = id.toLongOrNull()
        val cursorNum = cursor.toLongOrNull()
        return if (idNum != null && cursorNum != null) idNum > cursorNum else id != cursor
    }

    private fun buildApi(config: ApiConfig): SePayApi =
        Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(config.baseUrl))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SePayApi::class.java)

    /** Retrofit requires the base URL to end with `/`. */
    private fun normalizeBaseUrl(raw: String): String =
        if (raw.endsWith("/")) raw else "$raw/"

    private fun pollDelaySeconds(config: ApiConfig): Int =
        config.pollSeconds.coerceAtLeast(MIN_POLL_SECONDS)

    /** Release the polling scope. For tests/teardown; production uses [stop]. */
    fun shutdown() {
        scope.cancel()
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
        const val MIN_POLL_SECONDS = 15
    }
}
