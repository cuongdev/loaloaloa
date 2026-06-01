package com.tingting.notifier.domain

import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.TransactionRecord
import com.tingting.notifier.data.repository.TransactionRepository
import com.tingting.notifier.database.dao.TransactionDao
import com.tingting.notifier.database.entity.toEntity
import com.tingting.notifier.database.entity.toModel
import com.tingting.notifier.database.entity.toRecord
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {

    override suspend fun addTransaction(transaction: TransactionModel): Long =
        dao.insert(transaction.toEntity())

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

    override fun distinctAppIds(): Flow<List<String>> = dao.distinctAppIds()

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun deleteAll() = dao.deleteAll()
}
