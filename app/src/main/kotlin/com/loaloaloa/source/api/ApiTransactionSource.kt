package com.loaloaloa.source.api

import com.loaloaloa.data.model.ApiConfig
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.di.IoDispatcher
import com.loaloaloa.source.TransactionSource
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
import kotlinx.coroutines.withContext
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
 * notification window dedupe. Both sources then share the [com.loaloaloa.ingest.TransactionIngestor].
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
) : TransactionSource, SePayConnectionTester {

    private val _transactions = MutableSharedFlow<TransactionModel>(extraBufferCapacity = 64)
    override val transactions: SharedFlow<TransactionModel> = _transactions.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var pollJob: Job? = null

    // Cached Retrofit-backed client plus the (baseUrl, token) it was built from.
    // Rebuilt only when either changes, so a steady-state poll loop reuses one
    // Retrofit + GsonConverterFactory + proxy instead of allocating per cycle.
    private var cachedApi: SePayApi? = null
    private var cachedBaseUrl: String? = null
    private var cachedToken: String? = null

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

    /**
     * One-shot connection test for the Settings "Kiểm tra kết nối" button. Builds a
     * client from the SUPPLIED [config] (the values currently entered in the form, not
     * necessarily persisted), calls the list endpoint once on the IO dispatcher, and
     * returns the number of rows on success or the failure wrapped in [Result].
     * Does not touch the dedupe cursor or emit to [transactions].
     */
    override suspend fun testConnection(config: ApiConfig): Result<Int> = withContext(ioDispatcher) {
        runCatching {
            require(config.baseUrl.isNotBlank()) { "Chưa nhập địa chỉ máy chủ" }
            require(config.token.isNotBlank()) { "Chưa nhập API token" }
            val api = buildApi(config)
            val response = api.list(
                bearer = "Bearer ${config.token}",
                account = config.account.ifBlank { null },
                limit = DEFAULT_LIMIT,
            )
            response.error?.takeIf { it.isNotBlank() }?.let { error(it) }
            (response.transactions ?: emptyList()).size
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
        // Guard a null list: Gson honors an explicit `"transactions": null` payload,
        // which bypasses the DTO default and would NPE on iteration.
        // Oldest-first so emissions and the persisted cursor advance monotonically.
        val newRows = (response.transactions ?: emptyList())
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

    /**
     * Return the cached [SePayApi], rebuilding it only when the base URL or token
     * changed since the last build. The token isn't part of the Retrofit instance
     * (it's a per-call header), but a token change is a credential switch, so we
     * rebuild to drop any stale connection state.
     */
    private fun buildApi(config: ApiConfig): SePayApi {
        val cached = cachedApi
        if (cached != null && config.baseUrl == cachedBaseUrl && config.token == cachedToken) {
            return cached
        }
        val api = Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(config.baseUrl))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SePayApi::class.java)
        cachedApi = api
        cachedBaseUrl = config.baseUrl
        cachedToken = config.token
        return api
    }

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
