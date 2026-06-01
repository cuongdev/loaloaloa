package com.tingting.notifier.data.model

/**
 * A stored [TransactionModel] paired with its database row [id]. Used by the UI
 * (History) where rows must be deleted/undone by id. The domain [TransactionModel]
 * stays id-less on purpose (its equality is used by detection/announcement tests);
 * this wrapper carries the persistence id only where the UI needs it.
 */
data class TransactionRecord(
    val id: Long,
    val transaction: TransactionModel,
)
