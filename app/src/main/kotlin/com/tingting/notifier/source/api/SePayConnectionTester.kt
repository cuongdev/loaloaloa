package com.tingting.notifier.source.api

import com.tingting.notifier.data.model.ApiConfig

/**
 * One-shot SePay connectivity check used by the Settings "Kiểm tra kết nối" button.
 * Implemented by [ApiTransactionSource]; a separate seam so the Settings ViewModel
 * can be unit-tested with a fake instead of the concrete polling source.
 *
 * @return on success the number of transaction rows the endpoint returned; on
 *   failure the wrapped exception (its message is shown to the user).
 */
fun interface SePayConnectionTester {
    suspend fun testConnection(config: ApiConfig): Result<Int>
}
