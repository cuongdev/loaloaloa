package com.loaloaloa.relay

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.RelayRegisterState
import org.junit.Test

class RelayRegistrarTest {

    @Test fun `no FCM availability yields NO_FCM`() {
        assertThat(relayRegisterStateFor(fcmAvailable = false, token = "tok"))
            .isEqualTo(RelayRegisterState.NO_FCM)
    }

    @Test fun `available but blank token yields NO_FCM`() {
        assertThat(relayRegisterStateFor(fcmAvailable = true, token = ""))
            .isEqualTo(RelayRegisterState.NO_FCM)
        assertThat(relayRegisterStateFor(fcmAvailable = true, token = null))
            .isEqualTo(RelayRegisterState.NO_FCM)
    }

    @Test fun `available with a real token yields REGISTERED`() {
        assertThat(relayRegisterStateFor(fcmAvailable = true, token = "tok"))
            .isEqualTo(RelayRegisterState.REGISTERED)
    }
}
