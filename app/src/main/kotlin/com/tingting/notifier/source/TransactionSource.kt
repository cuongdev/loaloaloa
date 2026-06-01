package com.tingting.notifier.source

import com.tingting.notifier.data.model.TransactionModel
import kotlinx.coroutines.flow.Flow

/**
 * A detection source that emits normalized [TransactionModel]s. Both the
 * notification listener and the (Plan 6) API poller implement this seam so the
 * downstream pipeline — dedupe, persistence, announcement — is source-agnostic.
 */
interface TransactionSource {
    /** Normalized money-movement events as they are detected. */
    val transactions: Flow<TransactionModel>

    /** Begin observing/polling the underlying source. Idempotent. */
    fun start()

    /** Stop observing/polling and release any resources. Idempotent. */
    fun stop()
}
