package com.loaloaloa.data.repository

import com.loaloaloa.data.model.BestDay
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.TransactionRecord
import kotlinx.coroutines.flow.Flow

/** Local history of money-movement events, exposed at the domain-model level. */
interface TransactionRepository {

    /** Persist a transaction (optionally with an initial [note]); returns the new row id. */
    suspend fun addTransaction(transaction: TransactionModel, note: String = ""): Long

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

    /** Income records (with row ids) within the inclusive range, newest first. */
    fun observeIncomeBetween(from: Long, to: Long): Flow<List<TransactionRecord>>

    /** Largest single income amount ever; 0 when there is no income. */
    fun maxIncomeAmount(): Flow<Long>

    /** Highest single-day income ever (local day + total); null when no income. */
    fun bestIncomeDay(): Flow<BestDay?>

    /** Distinct source app/provider ids present in the history (for filter chips). */
    fun distinctAppIds(): Flow<List<String>>

    /** Set or clear the shop-owner note on a transaction row. */
    suspend fun updateNote(id: Long, note: String)

    /** Delete one transaction by row id. */
    suspend fun delete(id: Long)

    /** Delete the entire history. */
    suspend fun deleteAll()
}
