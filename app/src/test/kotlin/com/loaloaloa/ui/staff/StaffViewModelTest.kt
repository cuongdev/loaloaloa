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

    private fun makeVm(repo: FakeUserSettingsRepository) =
        StaffViewModel(repo, FakeRegistrar(), FakeDirectory(), FakeTransactionRepository())

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
}
