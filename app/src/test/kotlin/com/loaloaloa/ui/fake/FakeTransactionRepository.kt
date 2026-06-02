package com.loaloaloa.ui.fake

import com.loaloaloa.data.model.BestDay
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.TransactionRecord
import com.loaloaloa.data.repository.TransactionRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [TransactionRepository] for ViewModel tests; newest-first ordering. */
class FakeTransactionRepository(
    initial: List<TransactionRecord> = emptyList(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : TransactionRepository {

    private val rows = MutableStateFlow(initial.sortedByDescending { it.transaction.timestamp })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    /** Seed helper that assigns ids automatically. */
    fun seed(vararg models: TransactionModel) {
        models.forEach { addRecordSync(it) }
    }

    private fun addRecordSync(model: TransactionModel, note: String = ""): Long {
        val id = nextId++
        rows.update { (it + TransactionRecord(id, model, note)).sortedByDescending { r -> r.transaction.timestamp } }
        return id
    }

    override suspend fun addTransaction(transaction: TransactionModel, note: String): Long =
        addRecordSync(transaction, note)

    override suspend fun updateNote(id: Long, note: String) {
        rows.update { list -> list.map { if (it.id == id) it.copy(note = note) else it } }
    }

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

    override fun searchRecords(query: String, isIncome: Boolean?, from: Long, to: Long): Flow<List<TransactionRecord>> {
        val q = query.trim()
        val amountQ = q.filter { it.isDigit() }
        return rows.map { list ->
            list.filter { rec ->
                val t = rec.transaction
                val keywordOk = q.isEmpty() ||
                    t.rawText.contains(q, ignoreCase = true) ||
                    t.bankName.contains(q, ignoreCase = true) ||
                    (amountQ.isNotEmpty() && t.amount.toString().contains(amountQ))
                keywordOk &&
                    (isIncome == null || t.isIncome == isIncome) &&
                    t.timestamp in from..to
            }
        }
    }

    override fun totalAmount(isIncome: Boolean, from: Long, to: Long): Flow<Long> =
        rows.map { list ->
            list.filter { it.transaction.isIncome == isIncome && it.transaction.timestamp in from..to }
                .sumOf { it.transaction.amount }
        }

    override fun observeIncomeBetween(from: Long, to: Long): Flow<List<TransactionRecord>> =
        rows.map { list ->
            list.filter { it.transaction.isIncome && it.transaction.timestamp in from..to }
        }

    override fun maxIncomeAmount(): Flow<Long> =
        rows.map { list ->
            list.filter { it.transaction.isIncome }.maxOfOrNull { it.transaction.amount } ?: 0L
        }

    override fun bestIncomeDay(): Flow<BestDay?> =
        rows.map { list ->
            list.filter { it.transaction.isIncome }
                .groupBy { Instant.ofEpochMilli(it.transaction.timestamp).atZone(zone).toLocalDate() }
                .mapValues { (_, recs) -> recs.sumOf { it.transaction.amount } }
                .maxByOrNull { it.value }
                ?.let { BestDay(it.key.toString(), it.value) }
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
