package com.tingting.notifier.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tingting.notifier.database.dao.TransactionDao
import com.tingting.notifier.database.entity.TransactionEntity

@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
