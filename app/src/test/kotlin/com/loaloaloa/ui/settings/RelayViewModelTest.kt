package com.loaloaloa.ui.settings

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.relay.RelayCrypto
import com.loaloaloa.relay.RelayPairing
import com.loaloaloa.relay.RelayPairingCodec
import com.loaloaloa.relay.RelayDevice
import com.loaloaloa.relay.RelayDevicesResponse
import com.loaloaloa.relay.RelayDirectory
import com.loaloaloa.relay.RelayRegistrar
import com.loaloaloa.ui.fake.FakeUserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RelayViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `createRoom makes this device a paired hub`() = runTest {
        val repo = FakeUserSettingsRepository()
        val vm = RelayViewModel(repo, FakeRelayRegistrar(), FakeRelayDirectory())

        vm.createRoom("https://relay.example.workers.dev")
        runCurrent()

        assertThat(repo.current.relayRole).isEqualTo(RelayRole.HUB)
        assertThat(repo.current.relayRoom.roomId).isNotEmpty()
        assertThat(repo.current.relayRoom.roomSecret).isNotEmpty()
        assertThat(repo.current.relayRoom.senderUrl).isEqualTo("https://relay.example.workers.dev")
        // The hub publishes a pairing token that round-trips to the same room.
        val token = vm.pairingToken(repo.current)!!
        assertThat(RelayPairingCodec.decode(token)!!.roomSecret).isEqualTo(repo.current.relayRoom.roomSecret)
    }

    @Test fun `createRoom with blank url is a no-op`() = runTest {
        val repo = FakeUserSettingsRepository()
        val vm = RelayViewModel(repo, FakeRelayRegistrar(), FakeRelayDirectory())

        vm.createRoom("   ")
        runCurrent()

        assertThat(repo.current.relayRole).isEqualTo(RelayRole.NONE)
        assertThat(repo.current.relayRoom.roomId).isEmpty()
    }

    @Test fun `pairFromScan joins the room as a spoke`() = runTest {
        val repo = FakeUserSettingsRepository()
        val vm = RelayViewModel(repo, FakeRelayRegistrar(), FakeRelayDirectory())
        val pairing = RelayPairing("room-1", RelayCrypto.newRoomSecret(), "https://relay")
        val token = RelayPairingCodec.encode(pairing)

        val ok = vm.pairFromScan(token)
        runCurrent()

        assertThat(ok).isTrue()
        assertThat(vm.scanInvalid.value).isFalse()
        assertThat(repo.current.relayRole).isEqualTo(RelayRole.SPOKE)
        assertThat(repo.current.relayRoom.roomId).isEqualTo("room-1")
        assertThat(repo.current.relayRoom.roomSecret).isEqualTo(pairing.roomSecret)
    }

    @Test fun `pairFromScan with garbage raises scanInvalid and changes nothing`() = runTest {
        val repo = FakeUserSettingsRepository()
        val vm = RelayViewModel(repo, FakeRelayRegistrar(), FakeRelayDirectory())

        val ok = vm.pairFromScan("not a token")
        runCurrent()

        assertThat(ok).isFalse()
        assertThat(vm.scanInvalid.value).isTrue()
        assertThat(repo.current.relayRole).isEqualTo(RelayRole.NONE)

        vm.clearScanInvalid()
        assertThat(vm.scanInvalid.value).isFalse()
    }

    @Test fun `unpair returns the device to standalone`() = runTest {
        val repo = FakeUserSettingsRepository()
        val vm = RelayViewModel(repo, FakeRelayRegistrar(), FakeRelayDirectory())
        vm.createRoom("https://relay")
        runCurrent()

        vm.unpair()
        runCurrent()

        assertThat(repo.current.relayRole).isEqualTo(RelayRole.NONE)
        assertThat(repo.current.relayRoom.roomId).isEmpty()
    }

    @Test fun `setRole NONE clears the pairing`() = runTest {
        val repo = FakeUserSettingsRepository()
        val vm = RelayViewModel(repo, FakeRelayRegistrar(), FakeRelayDirectory())
        vm.createRoom("https://relay")
        runCurrent()

        vm.setRole(RelayRole.NONE)
        runCurrent()

        assertThat(repo.current.relayRoom.senderUrl).isEmpty()
    }

    @Test fun `pairingToken is null when not a paired hub`() = runTest {
        val repo = FakeUserSettingsRepository()
        val vm = RelayViewModel(repo, FakeRelayRegistrar(), FakeRelayDirectory())
        // Spoke side: paired, but not a hub → no token to publish.
        vm.pairFromScan(RelayPairingCodec.encode(RelayPairing("r", RelayCrypto.newRoomSecret(), "https://u")))
        runCurrent()
        assertThat(vm.pairingToken(repo.current)).isNull()
    }

    /** No-op registrar: the VM's role/room state is what these tests assert, not the network side. */
    private class FakeRelayRegistrar : RelayRegistrar {
        override fun provision(room: RelayRoom) = Unit
        override fun registerToken(room: RelayRoom, token: String) = Unit
        override suspend fun registerCurrentToken(room: RelayRoom) = Unit
    }

    /** No-op directory: these tests don't exercise the hub's paired-devices network calls. */
    private class FakeRelayDirectory : RelayDirectory {
        override suspend fun listDevices(room: RelayRoom): Result<RelayDevicesResponse> =
            Result.success(RelayDevicesResponse(ok = true))

        override suspend fun revoke(room: RelayRoom, token: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun closeRoom(room: RelayRoom): Result<Unit> =
            Result.success(Unit)
    }
}
