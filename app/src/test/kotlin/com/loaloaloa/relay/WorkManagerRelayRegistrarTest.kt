package com.loaloaloa.relay

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.RelayRegisterState
import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.ui.fake.FakeUserSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WorkManagerRelayRegistrarTest {

    private class FakeFcmTokenProvider(
        private val available: Boolean,
        private val token: String?,
    ) : FcmTokenProvider {
        override fun isFcmAvailable() = available
        override suspend fun currentToken() = token
    }

    @Test fun `registerCurrentToken persists NO_FCM when FCM is unavailable and does not enqueue`() = runTest {
        val repo = FakeUserSettingsRepository(UserSettings())
        val registrar = WorkManagerRelayRegistrar(
            ApplicationProvider.getApplicationContext(),
            FakeFcmTokenProvider(available = false, token = null),
            repo,
        )
        registrar.registerCurrentToken(RelayRoom(roomId = "r1", roomSecret = "s", senderUrl = "https://x"))
        assertThat(repo.current.relayRegisterState).isEqualTo(RelayRegisterState.NO_FCM)
    }
}
