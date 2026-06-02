package com.loaloaloa.data.model

/**
 * A stored [TransactionModel] paired with its database row [id]. Used by the UI
 * (History) where rows must be deleted/undone by id. The domain [TransactionModel]
 * stays id-less on purpose (its equality is used by detection/announcement tests);
 * this wrapper carries the persistence id and the user-editable [note] only where
 * the UI needs them. The [note] is shop-owner annotation, not part of the money event.
 */
data class TransactionRecord(
    val id: Long,
    val transaction: TransactionModel,
    val note: String = "",
)
