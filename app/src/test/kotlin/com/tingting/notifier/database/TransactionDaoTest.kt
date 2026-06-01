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
        rawText: String = "raw-$amount",
    ) = TransactionEntity(
        appId = appId,
        bankName = bankName,
        amount = amount,
        isIncome = isIncome,
        rawText = rawText,
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

    // --- search --------------------------------------------------------------

    private val sampleRaw = "TK 0399999999(VND) +500,000 ND:Thanh toan don hang"

    @Test fun `search matches content in rawText`() = runTest {
        dao.insert(txn("com.mbmobile", 500_000, true, timestamp = 1_000, rawText = sampleRaw))
        dao.insert(txn("com.VCB", 999, false, timestamp = 2_000, rawText = "khac"))

        val hits = dao.search(q = "Thanh toan", amountQ = "", isIncome = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(hits.map { it.rawText }).containsExactly(sampleRaw)
    }

    @Test fun `search matches account number substring in rawText`() = runTest {
        dao.insert(txn("com.mbmobile", 500_000, true, timestamp = 1_000, rawText = sampleRaw))
        dao.insert(txn("com.VCB", 999, false, timestamp = 2_000, rawText = "khac"))

        val hits = dao.search(q = "0399999999", amountQ = "0399999999", isIncome = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(hits.map { it.rawText }).containsExactly(sampleRaw)
    }

    @Test fun `search matches bankName`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000, bankName = "Vietcombank", rawText = "abc"))
        dao.insert(txn("com.mbmobile", 200, true, timestamp = 2_000, bankName = "MB Bank", rawText = "abc"))

        val hits = dao.search(q = "Vietcom", amountQ = "", isIncome = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(hits.map { it.bankName }).containsExactly("Vietcombank")
    }

    @Test fun `search matches exact amount`() = runTest {
        dao.insert(txn("com.VCB", 500_000, true, timestamp = 1_000, rawText = "abc"))
        dao.insert(txn("com.VCB", 12, true, timestamp = 2_000, rawText = "abc"))

        val hits = dao.search(q = "500000", amountQ = "500000", isIncome = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(hits.map { it.amount }).containsExactly(500_000L)
    }

    @Test fun `search matches partial amount`() = runTest {
        dao.insert(txn("com.VCB", 500_000, true, timestamp = 1_000, rawText = "abc"))
        dao.insert(txn("com.VCB", 12, true, timestamp = 2_000, rawText = "abc"))

        val hits = dao.search(q = "5000", amountQ = "5000", isIncome = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(hits.map { it.amount }).containsExactly(500_000L)
    }

    @Test fun `search with empty query returns all newest first`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000, rawText = "abc"))
        dao.insert(txn("com.VCB", 200, true, timestamp = 3_000, rawText = "def"))
        dao.insert(txn("com.VCB", 300, true, timestamp = 2_000, rawText = "ghi"))

        val hits = dao.search(q = "", amountQ = "", isIncome = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(hits.map { it.timestamp }).containsExactly(3_000L, 2_000L, 1_000L).inOrder()
    }

    @Test fun `search with no-digit query does not false-match amounts`() = runTest {
        dao.insert(txn("com.VCB", 999, true, timestamp = 1_000, bankName = "abc", rawText = "abc"))

        val hits = dao.search(q = "zzz", amountQ = "", isIncome = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(hits).isEmpty()
    }

    @Test fun `search combined with isIncome filter`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000, rawText = "shop coffee"))
        dao.insert(txn("com.VCB", 200, false, timestamp = 2_000, rawText = "shop tea"))

        val hits = dao.search(q = "shop", amountQ = "", isIncome = true, from = 0, to = Long.MAX_VALUE).first()
        assertThat(hits.map { it.amount }).containsExactly(100L)
    }

    @Test fun `search combined with date range is inclusive newest first`() = runTest {
        dao.insert(txn("com.VCB", 100, true, timestamp = 1_000, rawText = "x"))
        dao.insert(txn("com.VCB", 200, true, timestamp = 2_000, rawText = "x"))
        dao.insert(txn("com.VCB", 300, true, timestamp = 3_000, rawText = "x"))

        val hits = dao.search(q = "x", amountQ = "", isIncome = null, from = 2_000, to = 3_000).first()
        assertThat(hits.map { it.amount }).containsExactly(300L, 200L).inOrder()
    }
}
