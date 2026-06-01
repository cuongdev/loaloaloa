package com.tingting.notifier.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tingting.notifier.database.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    /**
     * Filtered history. [isIncome] and [appId] are optional — pass null to ignore
     * that facet. The [from]..[to] timestamp range is inclusive. Newest first.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:isIncome IS NULL OR isIncome = :isIncome)
          AND (:appId IS NULL OR appId = :appId)
          AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp DESC
        """
    )
    fun filter(isIncome: Boolean?, appId: String?, from: Long, to: Long): Flow<List<TransactionEntity>>

    /**
     * Keyword + date search. The trimmed keyword [q] matches against [TransactionEntity.rawText]
     * (covers content/memo AND account numbers, which live in the raw text) OR
     * [TransactionEntity.bankName]; the digits-only form [amountQ] matches the amount.
     * Empty [q] (with empty [amountQ]) matches everything. [isIncome] optional (null = ignore);
     * the [from]..[to] timestamp range is inclusive. Newest first.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (
            :q = ''
            OR rawText LIKE '%' || :q || '%'
            OR bankName LIKE '%' || :q || '%'
            OR (:amountQ != '' AND CAST(amount AS TEXT) LIKE '%' || :amountQ || '%')
        )
          AND (:isIncome IS NULL OR isIncome = :isIncome)
          AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp DESC
        """
    )
    fun search(q: String, amountQ: String, isIncome: Boolean?, from: Long, to: Long): Flow<List<TransactionEntity>>

    /** Sum of absolute amounts matching [isIncome] within the inclusive range; 0 if none. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE isIncome = :isIncome
          AND timestamp BETWEEN :from AND :to
        """
    )
    fun totalAmount(isIncome: Boolean, from: Long, to: Long): Flow<Long>

    @Query("SELECT DISTINCT appId FROM transactions")
    fun distinctAppIds(): Flow<List<String>>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
