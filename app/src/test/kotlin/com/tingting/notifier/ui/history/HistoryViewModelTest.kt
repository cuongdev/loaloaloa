package com.tingting.notifier.ui.history

import com.google.common.truth.Truth.assertThat
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.ui.fake.FakeTransactionRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val clock = Clock.fixed(Instant.parse("2026-06-01T05:00:00Z"), zone) // 12:00 +07

    private val today = Instant.parse("2026-06-01T03:00:00Z").toEpochMilli()
    private val todayEarlier = Instant.parse("2026-06-01T01:00:00Z").toEpochMilli()
    private val yesterday = Instant.parse("2026-05-31T03:00:00Z").toEpochMilli()
    private val older = Instant.parse("2026-05-30T03:00:00Z").toEpochMilli()

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun tx(appId: String, bank: String, amount: Long, isIncome: Boolean, ts: Long, memo: String = "memo") =
        TransactionModel(appId, bank, amount, isIncome, memo, ts)

    private fun vm(repo: FakeTransactionRepository) = HistoryViewModel(repo, clock, zone)

    private fun seededRepo() = FakeTransactionRepository().apply {
        seed(
            tx("com.VCB", "Vietcombank", 150_000, true, today),
            tx("com.mbmobile", "MB Bank", 450_000, true, todayEarlier),
            tx("com.mservice.momotransfer", "MoMo", 1_100_000, false, yesterday),
            tx("com.VCB", "Vietcombank", 2_100_000, true, older),
        )
    }

    @Test fun `default shows all with income and outgoing totals`() = runTest {
        val state = vm(seededRepo()).uiState.first { !it.isEmpty }
        assertThat(state.incomeTotal).isEqualTo(150_000 + 450_000 + 2_100_000)
        assertThat(state.outgoingTotal).isEqualTo(1_100_000)
    }

    @Test fun `income filter keeps only credits`() = runTest {
        val model = vm(seededRepo())
        model.setDirection(DirectionFilter.INCOME)
        val state = model.uiState.first { it.direction == DirectionFilter.INCOME && !it.isEmpty }
        val rows = state.sections.flatMap { it.rows }
        assertThat(rows.all { it.isIncome }).isTrue()
        assertThat(state.outgoingTotal).isEqualTo(0)
    }

    @Test fun `outgoing filter keeps only debits`() = runTest {
        val model = vm(seededRepo())
        model.setDirection(DirectionFilter.OUTGOING)
        val state = model.uiState.first { it.direction == DirectionFilter.OUTGOING && !it.isEmpty }
        val rows = state.sections.flatMap { it.rows }
        assertThat(rows).hasSize(1)
        assertThat(rows.single().isIncome).isFalse()
    }

    @Test fun `bank filter narrows to one appId`() = runTest {
        val model = vm(seededRepo())
        model.setBankFilter("com.VCB")
        val state = model.uiState.first { it.bankFilter == "com.VCB" && !it.isEmpty }
        val rows = state.sections.flatMap { it.rows }
        assertThat(rows).hasSize(2)
        assertThat(rows.all { it.bankName == "Vietcombank" }).isTrue()
    }

    @Test fun `groups by date with vietnamese headers newest first`() = runTest {
        val state = vm(seededRepo()).uiState.first { !it.isEmpty }
        assertThat(state.sections.map { it.header })
            .containsExactly("Hôm nay", "Hôm qua", "30 thg 5").inOrder()
        assertThat(state.sections.first().rows).hasSize(2) // two today
    }

    @Test fun `availableBanks lists distinct banks sorted`() = runTest {
        val state = vm(seededRepo()).uiState.first { !it.isEmpty }
        assertThat(state.availableBanks.map { it.displayName })
            .containsExactly("MB Bank", "MoMo", "Vietcombank").inOrder()
    }

    @Test fun `delete then undo restores the row`() = runTest {
        val repo = seededRepo()
        val model = vm(repo)
        val before = model.uiState.first { !it.isEmpty }
        val row = before.sections.first().rows.first()

        model.delete(row.record)
        runCurrent()
        val afterDelete = model.uiState.first { state -> state.sections.flatMap { it.rows }.none { it.id == row.id } }
        assertThat(afterDelete.sections.flatMap { it.rows }.map { it.id }).doesNotContain(row.id)

        model.undoDelete(row.record)
        runCurrent()
        val afterUndo = model.uiState.first { state -> state.sections.sumOf { it.rows.size } == before.sections.sumOf { it.rows.size } }
        assertThat(afterUndo.sections.sumOf { it.rows.size }).isEqualTo(before.sections.sumOf { it.rows.size })
    }

    @Test fun `deleteAll empties history`() = runTest {
        val repo = seededRepo()
        val model = vm(repo)
        model.uiState.first { !it.isEmpty }
        model.deleteAll()
        runCurrent()
        val state = model.uiState.first { it.isEmpty }
        assertThat(state.sections).isEmpty()
    }
}
