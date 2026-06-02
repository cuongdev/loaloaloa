package com.loaloaloa.ui.navigation

import com.loaloaloa.data.model.AppMode
import com.loaloaloa.data.model.RelayRegisterState
import com.loaloaloa.data.model.RelayRole

/** Coarse staff connection state shown on the staff status card. */
enum class StaffConnStatus { NOT_PAIRED, REGISTERING, CONNECTED, NO_FCM }

/**
 * One-time migration: map an existing install to a UI mode so it isn't dumped at the picker.
 * A spoke becomes STAFF; a hub or a device that has already granted notification access (i.e. an
 * existing standalone shop) becomes SHOP_OWNER; anything else is treated as a fresh install → UNSET
 * (show the picker). Runs only while settingsVersion < 1.
 */
fun migrateAppMode(relayRole: RelayRole, notifAccessGranted: Boolean): AppMode = when {
    relayRole == RelayRole.SPOKE -> AppMode.STAFF
    relayRole == RelayRole.HUB -> AppMode.SHOP_OWNER
    notifAccessGranted -> AppMode.SHOP_OWNER
    else -> AppMode.UNSET
}

/** Derive the staff connection status from transport role + persisted FCM register state. */
fun connectionStatusOf(relayRole: RelayRole, registerState: RelayRegisterState): StaffConnStatus = when {
    relayRole != RelayRole.SPOKE -> StaffConnStatus.NOT_PAIRED
    registerState == RelayRegisterState.NO_FCM -> StaffConnStatus.NO_FCM
    registerState == RelayRegisterState.REGISTERED -> StaffConnStatus.CONNECTED
    else -> StaffConnStatus.REGISTERING
}
