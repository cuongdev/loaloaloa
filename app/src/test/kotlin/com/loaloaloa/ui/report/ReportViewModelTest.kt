package com.loaloaloa.ui.report

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.ui.fake.FakeTransactionRepository
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

    private fun repo(vararg models: TransactionModel) =
        FakeTransactionRepository(zone = zone).apply { seed(*models) }

    private fun vm(repo: FakeTransactionRepository) = ReportViewModel(repo, clock, zone)

    @Test fun `today hero reflects income only, with three growth cards`() = runTest {
        val state = vm(
            repo(
                tx(300_000, true, atLocalNoon("2026-06-01")),
                tx(999_000, false, atLocalNoon("2026-06-01")), // outgoing ignored
            ),
        ).uiState.first { it.periodCount > 0 }

        assertThat(state.period).isEqualTo(ReportPeriod.TODAY)
        assertThat(state.periodTotal).isEqualTo(300_000)
        assertThat(state.periodCount).isEqualTo(1)
        assertThat(state.growth).hasSize(3)
        assertThat(state.records.biggestTransaction).isEqualTo(300_000)
    }

    @Test fun `selecting a period switches the hero range`() = runTest {
        val viewModel = vm(repo(tx(200_000, true, atLocalNoon("2026-06-01"))))
        viewModel.uiState.first { it.periodCount > 0 }

        viewModel.selectPeriod(ReportPeriod.WEEK)
        val week = viewModel.uiState.first { it.period == ReportPeriod.WEEK }

        assertThat(week.periodTotal).isEqualTo(200_000) // today falls in this week
    }
}
