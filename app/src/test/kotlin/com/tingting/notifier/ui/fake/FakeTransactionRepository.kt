package com.tingting.notifier.ui.fake

import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.TransactionRecord
import com.tingting.notifier.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [TransactionRepository] for ViewModel tests; newest-first ordering. */
class FakeTransactionRepository(
    initial: List<TransactionRecord> = emptyList(),
) : TransactionRepository {

    private val rows = MutableStateFlow(initial.sortedByDescending { it.transaction.timestamp })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    /** Seed helper that assigns ids automatically. */
    fun seed(vararg models: TransactionModel) {
        models.forEach { addRecordSync(it) }
    }

    private fun addRecordSync(model: TransactionModel): Long {
        val id = nextId++
        rows.update { (it + TransactionRecord(id, model)).sortedByDescending { r -> r.transaction.timestamp } }
        return id
    }

    override suspend fun addTransaction(transaction: TransactionModel): Long = addRecordSync(transaction)

    override fun getAll(): Flow<List<TransactionModel>> = rows.map { list -> list.map { it.transaction } }

    override fun observeRecords(): Flow<List<TransactionRecord>> = rows

    override fun filterRecords(isIncome: Boolean?, appId: String?, from: Long, to: Long): Flow<List<TransactionRecord>> =
        rows.map { list ->
            list.filter { rec ->
                val t = rec.transaction
                (isIncome == null || t.isIncome == isIncome) &&
                    (appId == null || t.appId == appId) &&
                    t.timestamp in from..to
            }
        }

    override fun filter(isIncome: Boolean?, appId: String?, from: Long, to: Long): Flow<List<TransactionModel>> =
        filterRecords(isIncome, appId, from, to).map { list -> list.map { it.transaction } }

    override fun totalAmount(isIncome: Boolean, from: Long, to: Long): Flow<Long> =
        rows.map { list ->
            list.filter { it.transaction.isIncome == isIncome && it.transaction.timestamp in from..to }
                .sumOf { it.transaction.amount }
        }

    override fun distinctAppIds(): Flow<List<String>> =
        rows.map { list -> list.map { it.transaction.appId }.distinct() }

    override suspend fun delete(id: Long) {
        rows.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun deleteAll() {
        rows.update { emptyList() }
    }
}
