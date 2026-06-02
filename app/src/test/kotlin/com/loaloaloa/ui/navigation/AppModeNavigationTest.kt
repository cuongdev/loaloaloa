package com.loaloaloa.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.AppMode
import com.loaloaloa.data.model.RelayRegisterState
import com.loaloaloa.data.model.RelayRole
import org.junit.Test

class AppModeNavigationTest {

    @Test fun `migration maps spoke to staff`() {
        assertThat(migrateAppMode(RelayRole.SPOKE, notifAccessGranted = false)).isEqualTo(AppMode.STAFF)
    }

    @Test fun `migration maps hub or notif-granted to shop owner`() {
        assertThat(migrateAppMode(RelayRole.HUB, notifAccessGranted = false)).isEqualTo(AppMode.SHOP_OWNER)
        assertThat(migrateAppMode(RelayRole.NONE, notifAccessGranted = true)).isEqualTo(AppMode.SHOP_OWNER)
    }

    @Test fun `migration maps a truly fresh install to UNSET`() {
        assertThat(migrateAppMode(RelayRole.NONE, notifAccessGranted = false)).isEqualTo(AppMode.UNSET)
    }

    @Test fun `connection status derives from role and register state`() {
        assertThat(connectionStatusOf(RelayRole.NONE, RelayRegisterState.IDLE))
            .isEqualTo(StaffConnStatus.NOT_PAIRED)
        assertThat(connectionStatusOf(RelayRole.SPOKE, RelayRegisterState.IDLE))
            .isEqualTo(StaffConnStatus.REGISTERING)
        assertThat(connectionStatusOf(RelayRole.SPOKE, RelayRegisterState.REGISTERED))
            .isEqualTo(StaffConnStatus.CONNECTED)
        assertThat(connectionStatusOf(RelayRole.SPOKE, RelayRegisterState.NO_FCM))
            .isEqualTo(StaffConnStatus.NO_FCM)
    }
}
