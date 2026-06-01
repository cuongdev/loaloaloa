package com.tingting.notifier.ui.report

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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    // 2026-06-01 is a Monday. Fixed now = Mon 12:00 +07.
    private val clock = Clock.fixed(Instant.parse("2026-06-01T05:00:00Z"), zone)

    private fun atLocalNoon(isoDate: String) =
        Instant.parse("${isoDate}T05:00:00Z").toEpochMilli() // 12:00 +07 on that date

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun tx(amount: Long, isIncome: Boolean, ts: Long) =
        TransactionModel("com.VCB", "Vietcombank", amount, isIncome, "raw", ts)

    private fun vm(repo: FakeTransactionRepository) = ReportViewModel(repo, clock, zone)

    @Test fun `bars cover seven days ending today`() = runTest {
        val state = vm(FakeTransactionRepository().apply { seed(tx(1, true, atLocalNoon("2026-06-01"))) })
            .uiState.first { it.bars.isNotEmpty() }
        assertThat(state.bars).hasSize(7)
        assertThat(state.bars.last().isToday).isTrue()
        // 2026-06-01 is Monday => last bar label T2
        assertThat(state.bars.last().label).isEqualTo("T2")
    }

    @Test fun `income aggregates into the correct day bar`() = runTest {
        val repo = FakeTransactionRepository().apply {
            seed(
                tx(100_000, true, atLocalNoon("2026-06-01")), // today (Mon)
                tx(200_000, true, atLocalNoon("2026-06-01")), // today again
                tx(500_000, true, atLocalNoon("2026-05-30")), // Sat within window
                tx(999_000, false, atLocalNoon("2026-06-01")), // outgoing ignored
            )
        }
        val state = vm(repo).uiState.first { it.weekTotal > 0 }
        assertThat(state.bars.last().income).isEqualTo(300_000) // today income
        assertThat(state.weekTotal).isEqualTo(800_000)
        assertThat(state.todayIncome).isEqualTo(300_000)
    }

    @Test fun `excludes transactions older than the window`() = runTest {
        val repo = FakeTransactionRepository().apply {
            seed(
                tx(100_000, true, atLocalNoon("2026-06-01")),
                tx(777_000, true, atLocalNoon("2026-05-20")), // outside 7-day window
            )
        }
        val state = vm(repo).uiState.first { it.weekTotal > 0 }
        assertThat(state.weekTotal).isEqualTo(100_000)
        assertThat(state.largestTransaction).isEqualTo(100_000)
    }

    @Test fun `average is week total divided by seven`() = runTest {
        val repo = FakeTransactionRepository().apply {
            seed(tx(700_000, true, atLocalNoon("2026-06-01")))
        }
        val state = vm(repo).uiState.first { it.weekTotal > 0 }
        assertThat(state.averagePerDay).isEqualTo(100_000)
    }

    @Test fun `largest transaction is the max income in window`() = runTest {
        val repo = FakeTransactionRepository().apply {
            seed(
                tx(100_000, true, atLocalNoon("2026-05-31")),
                tx(3_500_000, true, atLocalNoon("2026-05-29")),
                tx(900_000, true, atLocalNoon("2026-06-01")),
            )
        }
        val state = vm(repo).uiState.first { it.weekTotal > 0 }
        assertThat(state.largestTransaction).isEqualTo(3_500_000)
    }
}
