package com.loaloaloa.domain

import com.loaloaloa.data.model.BestDay
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.TransactionRecord
import com.loaloaloa.data.repository.TransactionRepository
import com.loaloaloa.database.dao.TransactionDao
import com.loaloaloa.database.entity.toEntity
import com.loaloaloa.database.entity.toModel
import com.loaloaloa.database.entity.toRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {

    override suspend fun addTransaction(transaction: TransactionModel, note: String): Long =
        dao.insert(transaction.toEntity(note))

    override fun getAll(): Flow<List<TransactionModel>> =
        dao.getAll().map { rows -> rows.map { it.toModel() } }

    override fun observeRecords(): Flow<List<TransactionRecord>> =
        dao.getAll().map { rows -> rows.map { it.toRecord() } }

    override fun filterRecords(isIncome: Boolean?, appId: String?, from: Long, to: Long): Flow<List<TransactionRecord>> =
        dao.filter(isIncome, appId, from, to).map { rows -> rows.map { it.toRecord() } }

    override fun searchRecords(query: String, isIncome: Boolean?, from: Long, to: Long): Flow<List<TransactionRecord>> {
        val q = query.trim()
        val amountQ = q.filter { it.isDigit() }
        return dao.search(q, amountQ, isIncome, from, to).map { rows -> rows.map { it.toRecord() } }
    }

    override fun filter(isIncome: Boolean?, appId: String?, from: Long, to: Long): Flow<List<TransactionModel>> =
        dao.filter(isIncome, appId, from, to).map { rows -> rows.map { it.toModel() } }

    override fun totalAmount(isIncome: Boolean, from: Long, to: Long): Flow<Long> =
        dao.totalAmount(isIncome, from, to)

    override fun observeIncomeBetween(from: Long, to: Long): Flow<List<TransactionRecord>> =
        dao.incomeBetween(from, to).map { rows -> rows.map { it.toRecord() } }

    override fun maxIncomeAmount(): Flow<Long> = dao.maxIncomeAmount()

    override fun bestIncomeDay(): Flow<BestDay?> = dao.bestIncomeDay()

    override fun distinctAppIds(): Flow<List<String>> = dao.distinctAppIds()

    override suspend fun updateNote(id: Long, note: String) = dao.updateNote(id, note)

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun deleteAll() = dao.deleteAll()
}
