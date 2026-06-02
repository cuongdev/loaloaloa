package com.loaloaloa.ui.home

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.ui.fake.FakeTransactionRepository
import com.loaloaloa.ui.fake.FakeUserSettingsRepository
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
class HomeViewModelTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    // Fixed "now" = 2026-06-01 12:00 local.
    private val now = Instant.parse("2026-06-01T05:00:00Z") // 12:00 +07
    private val clock = Clock.fixed(now, zone)

    private val midToday = Instant.parse("2026-06-01T03:00:00Z").toEpochMilli() // 10:00 local today
    private val earlyToday = Instant.parse("2026-06-01T01:00:00Z").toEpochMilli()
    private val yesterday = Instant.parse("2026-05-31T03:00:00Z").toEpochMilli()

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun tx(amount: Long, isIncome: Boolean, ts: Long, bank: String = "Vietcombank") =
        TransactionModel("com.VCB", bank, amount, isIncome, "raw", ts)

    private fun vm(
        txRepo: FakeTransactionRepository = FakeTransactionRepository(),
        settings: FakeUserSettingsRepository = FakeUserSettingsRepository(),
    ) = HomeViewModel(txRepo, settings, clock, zone)

    @Test fun `today income sums only today's income`() = runTest {
        val repo = FakeTransactionRepository()
        repo.seed(
            tx(150_000, true, midToday),
            tx(500_000, true, earlyToday),
            tx(99_000, false, midToday),   // outgoing, excluded from income
            tx(1_000_000, true, yesterday), // not today
        )
        val state = vm(repo).uiState.first { it.recent.isNotEmpty() }
        assertThat(state.todayIncome).isEqualTo(650_000)
    }

    @Test fun `today count includes income and outgoing for today only`() = runTest {
        val repo = FakeTransactionRepository()
        repo.seed(
            tx(150_000, true, midToday),
            tx(99_000, false, midToday),
            tx(1_000_000, true, yesterday),
        )
        val state = vm(repo).uiState.first { it.recent.isNotEmpty() }
        assertThat(state.todayCount).isEqualTo(2)
    }

    @Test fun `recent is capped at five newest`() = runTest {
        val repo = FakeTransactionRepository()
        repo.seed(*(1..8).map { tx(it * 1000L, true, midToday - it * 60_000L) }.toTypedArray())
        val state = vm(repo).uiState.first { it.recent.isNotEmpty() }
        assertThat(state.recent).hasSize(5)
        // newest first => largest timestamp (smallest i) first
        assertThat(state.recent.first().transaction.amount).isEqualTo(1000L)
    }

    @Test fun `service enabled reflects settings`() = runTest {
        val settings = FakeUserSettingsRepository(UserSettings(enableService = true))
        val repo = FakeTransactionRepository().apply { seed(tx(1, true, midToday)) }
        val state = vm(repo, settings).uiState.first { it.recent.isNotEmpty() }
        assertThat(state.serviceEnabled).isTrue()
    }

    @Test fun `setServiceEnabled writes through to settings`() = runTest {
        val settings = FakeUserSettingsRepository()
        val model = vm(settings = settings)
        model.setServiceEnabled(true)
        runCurrent()
        assertThat(settings.current.enableService).isTrue()
    }

    @Test fun `permission updates surface battery warning and access flag`() = runTest {
        val repo = FakeTransactionRepository().apply { seed(tx(1, true, midToday)) }
        val model = vm(repo)
        model.updatePermissions(notificationAccessGranted = true, isIgnoringBatteryOptimizations = false)
        val state = model.uiState.first { it.notificationAccessGranted }
        assertThat(state.notificationAccessGranted).isTrue()
        assertThat(state.batteryWarning).isTrue() // not exempt => warning
    }
}
