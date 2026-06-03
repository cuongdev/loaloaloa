package com.loaloaloa.ui.staff

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.AppMode
import com.loaloaloa.data.model.RelayRegisterState
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.relay.RelayCrypto
import com.loaloaloa.relay.RelayDevicesResponse
import com.loaloaloa.relay.RelayDirectory
import com.loaloaloa.relay.RelayPairing
import com.loaloaloa.relay.RelayPairingCodec
import com.loaloaloa.relay.RelayRegistrar
import com.loaloaloa.ui.fake.FakeTransactionRepository
import com.loaloaloa.ui.fake.FakeUserSettingsRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRegistrar : RelayRegistrar {
        val registeredRooms = mutableListOf<RelayRoom>()
        override fun provision(room: RelayRoom) {}
        override fun registerToken(room: RelayRoom, token: String) {}
        override suspend fun registerCurrentToken(room: RelayRoom) { registeredRooms.add(room) }
    }

    private class FakeDirectory : RelayDirectory {
        override suspend fun listDevices(room: RelayRoom): Result<RelayDevicesResponse> =
            Result.success(RelayDevicesResponse(ok = true))
        override suspend fun revoke(room: RelayRoom, token: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun closeRoom(room: RelayRoom): Result<Unit> =
            Result.success(Unit)
    }

    private val clock = Clock.fixed(Instant.ofEpochMilli(123_000L), ZoneId.of("UTC"))

    private fun makeVm(repo: FakeUserSettingsRepository) =
        StaffViewModel(repo, FakeRegistrar(), FakeDirectory(), FakeTransactionRepository(), clock)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `unpairToShop clears role, room, register state, and returns to picker`() = runTest(dispatcher) {
        val repo = FakeUserSettingsRepository(
            UserSettings(
                appMode = AppMode.STAFF,
                relayRole = RelayRole.SPOKE,
                relayRoom = RelayRoom(roomId = "r1", roomSecret = "s", senderUrl = "https://x"),
                relayRegisterState = RelayRegisterState.REGISTERED,
            ),
        )
        val vm = makeVm(repo)

        vm.unpairToShop()
        advanceUntilIdle()

        val s = repo.current
        assertThat(s.relayRole).isEqualTo(RelayRole.NONE)
        assertThat(s.relayRoom).isEqualTo(RelayRoom())
        assertThat(s.relayRegisterState).isEqualTo(RelayRegisterState.IDLE)
        assertThat(s.appMode).isEqualTo(AppMode.UNSET)
    }

    @Test fun `pairFromScan enables service so TTS announces relayed transactions`() = runTest(dispatcher) {
        val repo = FakeUserSettingsRepository()
        val vm = makeVm(repo)
        val pairing = RelayPairing("room-1", RelayCrypto.newRoomSecret(), "https://relay.example.workers.dev")
        val token = RelayPairingCodec.encode(pairing)

        val ok = vm.pairFromScan(token)
        advanceUntilIdle()

        assertThat(ok).isTrue()
        assertThat(repo.current.relayRole).isEqualTo(RelayRole.SPOKE)
        assertThat(repo.current.enableService).isTrue()
    }

    @Test fun `setLoaEnabled toggles enableService`() = runTest(dispatcher) {
        val repo = FakeUserSettingsRepository()
        val vm = makeVm(repo)

        vm.setLoaEnabled(true)
        advanceUntilIdle()
        assertThat(repo.current.enableService).isTrue()

        vm.setLoaEnabled(false)
        advanceUntilIdle()
        assertThat(repo.current.enableService).isFalse()
    }

    @Test fun `clockIn sets staff name, opens shift, and adds to roster`() = runTest(dispatcher) {
        val repo = FakeUserSettingsRepository()
        val vm = makeVm(repo)

        vm.clockIn("  An  ")
        advanceUntilIdle()

        assertThat(repo.current.staffName).isEqualTo("An")
        assertThat(repo.current.activeStaff).containsExactly("An")
        assertThat(repo.current.shiftStartedAt).isEqualTo(123_000L)
    }

    @Test fun `clockIn ignores blank names`() = runTest(dispatcher) {
        val repo = FakeUserSettingsRepository()
        val vm = makeVm(repo)

        vm.clockIn("   ")
        advanceUntilIdle()

        assertThat(repo.current.activeStaff).isEmpty()
        assertThat(repo.current.shiftStartedAt).isNull()
    }

    @Test fun `clockIn keeps an already-open shift's start time`() = runTest(dispatcher) {
        val repo = FakeUserSettingsRepository(UserSettings(shiftStartedAt = 50_000L))
        val vm = makeVm(repo)

        vm.clockIn("Bình")
        advanceUntilIdle()

        assertThat(repo.current.shiftStartedAt).isEqualTo(50_000L)
    }

    @Test fun `addStaff appends to roster and dedupes`() = runTest(dispatcher) {
        val repo = FakeUserSettingsRepository(UserSettings(activeStaff = listOf("An")))
        val vm = makeVm(repo)

        vm.addStaff("Bình")
        advanceUntilIdle()
        vm.addStaff("An") // duplicate
        advanceUntilIdle()

        assertThat(repo.current.activeStaff).containsExactly("An", "Bình").inOrder()
    }

    @Test fun `removeStaff drops one name from the roster`() = runTest(dispatcher) {
        val repo = FakeUserSettingsRepository(UserSettings(activeStaff = listOf("An", "Bình")))
        val vm = makeVm(repo)

        vm.removeStaff("An")
        advanceUntilIdle()

        assertThat(repo.current.activeStaff).containsExactly("Bình")
    }

    @Test fun `setStaffName changes the device name without touching the roster`() = runTest(dispatcher) {
        val repo = FakeUserSettingsRepository(UserSettings(staffName = "An", activeStaff = listOf("An")))
        val vm = makeVm(repo)

        vm.setStaffName("  Chi  ")
        advanceUntilIdle()

        assertThat(repo.current.staffName).isEqualTo("Chi")
        assertThat(repo.current.activeStaff).containsExactly("An")
    }
}
