package com.tingting.notifier.data.repository

import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.TransactionRecord
import kotlinx.coroutines.flow.Flow

/** Local history of money-movement events, exposed at the domain-model level. */
interface TransactionRepository {

    /** Persist a transaction; returns the new row id. */
    suspend fun addTransaction(transaction: TransactionModel): Long

    /** All transactions, newest first. */
    fun getAll(): Flow<List<TransactionModel>>

    /** All transactions with their row ids, newest first (UI list with delete/undo). */
    fun observeRecords(): Flow<List<TransactionRecord>>

    /** Filtered history with row ids; same facet semantics as [filter]. */
    fun filterRecords(isIncome: Boolean?, appId: String?, from: Long, to: Long): Flow<List<TransactionRecord>>

    /**
     * Keyword + date search with row ids. [query] matches content/account-number (rawText),
     * bank name, or amount (digits-only form of the query). Empty [query] matches all.
     * [isIncome] optional (null = ignore); [from]..[to] inclusive; newest first.
     */
    fun searchRecords(query: String, isIncome: Boolean?, from: Long, to: Long): Flow<List<TransactionRecord>>

    /** Filtered history. [isIncome] / [appId] optional (null = ignore); range inclusive; newest first. */
    fun filter(isIncome: Boolean?, appId: String?, from: Long, to: Long): Flow<List<TransactionModel>>

    /** Sum of amounts matching [isIncome] in the inclusive range; 0 if none. */
    fun totalAmount(isIncome: Boolean, from: Long, to: Long): Flow<Long>

    /** Distinct source app/provider ids present in the history (for filter chips). */
    fun distinctAppIds(): Flow<List<String>>

    /** Delete one transaction by row id. */
    suspend fun delete(id: Long)

    /** Delete the entire history. */
    suspend fun deleteAll()
}
