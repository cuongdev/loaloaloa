package com.loaloaloa.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.relay.RelayCrypto
import com.loaloaloa.relay.RelayDevice
import com.loaloaloa.relay.RelayEvent
import com.loaloaloa.relay.RelayDirectory
import com.loaloaloa.relay.RelayPairingCodec
import com.loaloaloa.relay.RelayRegistrar
import com.loaloaloa.relay.toPairing
import com.loaloaloa.relay.toRoom
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the "Chia sẻ thông báo" (device relay) settings: choosing this device's [RelayRole] and
 * pairing it into a room. A hub generates a fresh room secret and publishes a pairing QR; a spoke
 * scans that QR to join. The actual sending/receiving lives in the relay package — this VM only
 * manages role + room state through [UserSettingsRepository].
 */
@HiltViewModel
class RelayViewModel @Inject constructor(
    private val repo: UserSettingsRepository,
    private val registrar: RelayRegistrar,
    private val directory: RelayDirectory,
) : ViewModel() {

    val uiState: StateFlow<UserSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    /** Whether the most recent scan/paste failed to decode as a relay pairing token. */
    private val _scanInvalid = MutableStateFlow(false)
    val scanInvalid: StateFlow<Boolean> = _scanInvalid.asStateFlow()

    /** Hub-side state of the "paired employee devices" list (loaded on demand from the Sender). */
    private val _devices = MutableStateFlow(DevicesUiState())
    val devices: StateFlow<DevicesUiState> = _devices.asStateFlow()

    /** Switch this device's role. Switching to [RelayRole.NONE] also clears any pairing. */
    fun setRole(role: RelayRole) {
        viewModelScope.launch {
            repo.updateRelayRole(role)
            if (role == RelayRole.NONE) repo.setRelayRoom(RelayRoom())
        }
    }

    /**
     * Become a hub: generate a fresh room (random id + 256-bit secret) pointing at [senderUrl],
     * persist it, and set the role to [RelayRole.HUB]. The [senderUrl] is the shop's deployed
     * Sender (Cloudflare Worker). No-op if [senderUrl] is blank.
     */
    fun createRoom(senderUrl: String) {
        val url = senderUrl.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            val room = RelayRoom(
                roomId = UUID.randomUUID().toString(),
                roomSecret = RelayCrypto.newRoomSecret(),
                senderUrl = url,
            )
            repo.setRelayRoom(room)
            repo.updateRelayRole(RelayRole.HUB)
            // Publish the room's mac key so the Sender can verify this hub's signed /send requests.
            registrar.provision(room)
        }
    }

    /**
     * Become a spoke by joining the room encoded in a scanned/pasted pairing [token]. Returns true
     * and persists the room + [RelayRole.SPOKE] on success; on a malformed token returns false and
     * raises [scanInvalid] for the UI. Never throws.
     */
    fun pairFromScan(token: String): Boolean {
        val pairing = RelayPairingCodec.decode(token)
        if (pairing == null) {
            _scanInvalid.value = true
            return false
        }
        val room = pairing.toRoom()
        viewModelScope.launch {
            repo.setRelayRoom(room)
            repo.updateRelayRole(RelayRole.SPOKE)
            // Register this spoke's FCM token so the Sender fans relayed transactions out to it.
            registrar.registerCurrentToken(room)
        }
        return true
    }

    /**
     * Clear the pairing and return this device to standalone [RelayRole.NONE]. When this device is the
     * HUB, also destroy the room on the Sender ("Huỷ phòng") so every paired spoke (phone or web) is
     * kicked on its next poll.
     */
    fun unpair() {
        viewModelScope.launch {
            val settings = repo.settings.first()
            val room = settings.relayRoom
            if (settings.relayRole == RelayRole.HUB && room.roomId.isNotBlank()) {
                runCatching { directory.closeRoom(room) }
            }
            repo.setRelayRoom(RelayRoom())
            repo.updateRelayRole(RelayRole.NONE)
        }
    }

    /** Reset the invalid-scan banner after the UI has shown it. */
    fun clearScanInvalid() {
        _scanInvalid.value = false
    }

    /** The pairing token (for QR + copy) the current room publishes, or null when not a paired hub. */
    fun pairingToken(settings: UserSettings): String? {
        if (settings.relayRole != RelayRole.HUB) return null
        val room = settings.relayRoom
        if (room.roomId.isBlank() || room.roomSecret.isBlank() || room.senderUrl.isBlank()) return null
        return RelayPairingCodec.encode(room.toPairing())
    }

    /**
     * The pairing *link* (for QR + copy) the current room publishes, or null when not a paired hub.
     * Same payload as [pairingToken] but framed as "<senderUrl>/pair#<token>" so an employee who
     * scans it without the app lands on the install page; the token stays in the URL fragment and is
     * never sent to the Sender. [pairFromScan] decodes either this link or a bare token.
     */
    fun pairingLink(settings: UserSettings): String? {
        if (settings.relayRole != RelayRole.HUB) return null
        val room = settings.relayRoom
        if (room.roomId.isBlank() || room.roomSecret.isBlank() || room.senderUrl.isBlank()) return null
        return RelayPairingCodec.link(room.toPairing())
    }

    /**
     * Refresh the hub's list of paired spoke devices from the Sender. No-op unless this device is a
     * paired hub. Sets [DevicesUiState.loading] while in flight and [DevicesUiState.error] on failure
     * (e.g. offline) so the UI can offer a retry.
     */
    fun refreshDevices() {
        val settings = uiState.value
        if (settings.relayRole != RelayRole.HUB) return
        val room = settings.relayRoom
        if (room.roomId.isBlank() || room.roomSecret.isBlank() || room.senderUrl.isBlank()) return
        viewModelScope.launch {
            _devices.value = _devices.value.copy(loading = true, error = false)
            _devices.value = directory.listDevices(room).fold(
                onSuccess = {
                    DevicesUiState(loading = false, devices = it.devices, events = it.events, error = false, loaded = true)
                },
                onFailure = { _devices.value.copy(loading = false, error = true, loaded = true) },
            )
        }
    }

    /**
     * Revoke one spoke device by its FCM [token] so the Sender stops fanning relays out to it, then
     * refresh the list. Optimistically drops it from the shown list for immediate feedback.
     */
    fun revokeDevice(token: String) {
        val room = uiState.value.relayRoom
        if (room.roomId.isBlank() || room.roomSecret.isBlank() || room.senderUrl.isBlank()) return
        viewModelScope.launch {
            _devices.value = _devices.value.copy(
                devices = _devices.value.devices.filterNot { it.token == token },
            )
            directory.revoke(room, token)
            refreshDevices()
        }
    }
}

/** UI state for the hub's paired-devices list. */
data class DevicesUiState(
    val loading: Boolean = false,
    val devices: List<RelayDevice> = emptyList(),
    val events: List<RelayEvent> = emptyList(),
    val error: Boolean = false,
    /** True once a load attempt has completed, so the UI shows "empty" only after the first fetch. */
    val loaded: Boolean = false,
)
