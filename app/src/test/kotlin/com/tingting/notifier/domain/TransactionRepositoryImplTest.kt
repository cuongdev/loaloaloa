package com.tingting.notifier.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepositoryImpl

    private fun model(
        appId: String,
        amount: Long,
        isIncome: Boolean,
        timestamp: Long,
    ) = TransactionModel(
        appId = appId,
        bankName = appId,
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
        repo = TransactionRepositoryImpl(db.transactionDao())
    }

    @After fun tearDown() = db.close()

    @Test fun `add then getAll emits domain models newest first`() = runTest {
        repo.addTransaction(model("com.VCB", 100, true, timestamp = 1_000))
        repo.addTransaction(model("com.mbmobile", 200, false, timestamp = 2_000))

        val all = repo.getAll().first()
        assertThat(all).hasSize(2)
        assertThat(all.first()).isEqualTo(model("com.mbmobile", 200, false, timestamp = 2_000))
        assertThat(all[1]).isEqualTo(model("com.VCB", 100, true, timestamp = 1_000))
    }

    @Test fun `filter maps to domain models`() = runTest {
        repo.addTransaction(model("com.VCB", 100, true, timestamp = 1_000))
        repo.addTransaction(model("com.VCB", 200, false, timestamp = 2_000))

        val income = repo.filter(isIncome = true, appId = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(income).containsExactly(model("com.VCB", 100, true, timestamp = 1_000))
    }

    @Test fun `totalAmount sums income`() = runTest {
        repo.addTransaction(model("com.VCB", 100, true, timestamp = 1_000))
        repo.addTransaction(model("com.VCB", 250, true, timestamp = 2_000))

        assertThat(repo.totalAmount(isIncome = true, from = 0, to = Long.MAX_VALUE).first()).isEqualTo(350L)
    }

    @Test fun `distinctAppIds returns unique ids`() = runTest {
        repo.addTransaction(model("com.VCB", 100, true, timestamp = 1_000))
        repo.addTransaction(model("com.VCB", 200, true, timestamp = 2_000))
        repo.addTransaction(model("com.mbmobile", 300, true, timestamp = 3_000))

        assertThat(repo.distinctAppIds().first()).containsExactly("com.VCB", "com.mbmobile")
    }

    @Test fun `delete removes the matching row`() = runTest {
        repo.addTransaction(model("com.VCB", 100, true, timestamp = 1_000))
        repo.addTransaction(model("com.VCB", 200, true, timestamp = 2_000))
        // addTransaction returns the new row id
        val id = repo.addTransaction(model("com.VCB", 300, true, timestamp = 3_000))

        repo.delete(id)

        assertThat(repo.getAll().first().map { it.amount }).containsExactly(200L, 100L).inOrder()
    }

    @Test fun `deleteAll empties history`() = runTest {
        repo.addTransaction(model("com.VCB", 100, true, timestamp = 1_000))
        repo.deleteAll()
        assertThat(repo.getAll().first()).isEmpty()
    }

    @Test fun `observeRecords carries row ids newest first`() = runTest {
        val id1 = repo.addTransaction(model("com.VCB", 100, true, timestamp = 1_000))
        val id2 = repo.addTransaction(model("com.mbmobile", 200, false, timestamp = 2_000))

        val records = repo.observeRecords().first()
        assertThat(records.map { it.id }).containsExactly(id2, id1).inOrder()
        assertThat(records.first().transaction).isEqualTo(model("com.mbmobile", 200, false, timestamp = 2_000))
    }

    @Test fun `filterRecords carries row ids for the matching facet`() = runTest {
        val incomeId = repo.addTransaction(model("com.VCB", 100, true, timestamp = 1_000))
        repo.addTransaction(model("com.VCB", 200, false, timestamp = 2_000))

        val income = repo.filterRecords(isIncome = true, appId = null, from = 0, to = Long.MAX_VALUE).first()
        assertThat(income.map { it.id }).containsExactly(incomeId)
    }
}
