package com.tingting.notifier.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tingting.notifier.database.dao.TransactionDao
import com.tingting.notifier.database.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao

    private fun txn(
        appId: String,
        amount: Long,
        isIncome: Boolean,
        timestamp: Long,
        bankName: String = appId,
    ) = TransactionEntity(
        appId = appId,
        bankName = bankName,
        amount = amount,
        isIncome = isIncome,
        rawText = "raw-$amount",
        timestamp = timestamp,
    )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.transactionDao()
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun `insert and getAll returns rows newest first`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000))
        dao.insert(txn("com.mbmobile", 200, false, timestamp = 3_000))
        dao.insert(txn("com.tpb.mb.gprsandroid", 300, true, timestamp = 2_000))

        val all = dao.getAll().first()
        assertThat(all.map { it.timestamp }).containsExactly(3_000L, 2_000L, 1_000L).inOrder()
        assertThat(all.map { it.amount }).containsExactly(200L, 300L, 100L).inOrder()
    }

    @Test fun `filter by income only`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000))
        dao.insert(txn("com.VCB", 200, false, timestamp = 2_000))
        dao.insert(txn("com.VCB", 300, true, timestamp = 3_000))

        val income = dao.filter(isIncome = true, appId = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(income.map { it.amount }).containsExactly(300L, 100L).inOrder()
    }

    @Test fun `filter by outgoing only`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000))
        dao.insert(txn("com.VCB", 200, false, timestamp = 2_000))

        val out = dao.filter(isIncome = false, appId = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(out.map { it.amount }).containsExactly(200L)
    }

    @Test fun `filter ignores income flag when null`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000))
        dao.insert(txn("com.VCB", 200, false, timestamp = 2_000))

        val all = dao.filter(isIncome = null, appId = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(all).hasSize(2)
    }

    @Test fun `filter by appId`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000))
        dao.insert(txn("com.mbmobile", 200, true, timestamp = 2_000))

        val vcb = dao.filter(isIncome = null, appId = "com.VCB", from = 0, to = Long.MAX_VALUE).first()
        assertThat(vcb.map { it.amount }).containsExactly(100L)
    }

    @Test fun `filter by date range is inclusive`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000))
        dao.insert(txn("com.VCB", 200, true, timestamp = 2_000))
        dao.insert(txn("com.VCB", 300, true, timestamp = 3_000))

        val mid = dao.filter(isIncome = null, appId = null, from = 2_000, to = 3_000).first()
        assertThat(mid.map { it.amount }).containsExactly(300L, 200L).inOrder()
    }

    @Test fun `totalAmount sums matching income in range`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000))
        dao.insert(txn("com.VCB", 250, true, timestamp = 2_000))
        dao.insert(txn("com.VCB", 999, false, timestamp = 2_500)) // excluded: outgoing

        val total = dao.totalAmount(isIncome = true, from = 0, to = Long.MAX_VALUE).first()
        assertThat(total).isEqualTo(350L)
    }

    @Test fun `totalAmount is zero when nothing matches`() = runTest {
        dao.insert(txn("com.VCB", 100, false, timestamp = 1_000))
        val total = dao.totalAmount(isIncome = true, from = 0, to = Long.MAX_VALUE).first()
        assertThat(total).isEqualTo(0L)
    }

    @Test fun `distinctAppIds returns unique ids`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000))
        dao.insert(txn("com.VCB", 200, true, timestamp = 2_000))
        dao.insert(txn("com.mbmobile", 300, true, timestamp = 3_000))

        val ids = dao.distinctAppIds().first()
        assertThat(ids).containsExactly("com.VCB", "com.mbmobile")
    }

    @Test fun `deleteById removes one row`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000))
        dao.insert(txn("com.VCB", 200, true, timestamp = 2_000))
        val first = dao.getAll().first()
        val idToDelete = first.first { it.amount == 100L }.id

        dao.deleteById(idToDelete)

        val remaining = dao.getAll().first()
        assertThat(remaining.map { it.amount }).containsExactly(200L)
    }

    @Test fun `deleteAll empties the table`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000))
        dao.insert(txn("com.VCB", 200, true, timestamp = 2_000))

        dao.deleteAll()

        assertThat(dao.getAll().first()).isEmpty()
    }
}
