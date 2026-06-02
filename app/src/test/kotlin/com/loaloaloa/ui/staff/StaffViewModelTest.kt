package com.loaloaloa.ui.staff

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.AppMode
import com.loaloaloa.data.model.RelayRegisterState
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.relay.RelayRegistrar
import com.loaloaloa.ui.fake.FakeTransactionRepository
import com.loaloaloa.ui.fake.FakeUserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
        val vm = StaffViewModel(repo, FakeRegistrar(), FakeTransactionRepository())

        vm.unpairToShop()
        dispatcher.scheduler.advanceUntilIdle()

        val s = repo.current
        assertThat(s.relayRole).isEqualTo(RelayRole.NONE)
        assertThat(s.relayRoom).isEqualTo(RelayRoom())
        assertThat(s.relayRegisterState).isEqualTo(RelayRegisterState.IDLE)
        assertThat(s.appMode).isEqualTo(AppMode.UNSET)
    }
}
