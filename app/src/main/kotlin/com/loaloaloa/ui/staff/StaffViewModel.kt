package com.loaloaloa.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loaloaloa.data.model.AppMode
import com.loaloaloa.data.model.RelayRegisterState
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.RelayRoom
import com.loaloaloa.data.model.TransactionRecord
import com.loaloaloa.data.model.UserSettings
import com.loaloaloa.data.repository.TransactionRepository
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.relay.RelayPairingCodec
import com.loaloaloa.relay.RelayRegistrar
import com.loaloaloa.relay.toRoom
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the staff (spoke) shell: pairing via QR, the locally-derived connection status, the most
 * recent received transaction, and the "unpair / switch back to picker" action. Distinct from the
 * shop-side [com.loaloaloa.ui.settings.RelayViewModel] so the staff shell stays minimal.
 */
@HiltViewModel
class StaffViewModel @Inject constructor(
    private val repo: UserSettingsRepository,
    private val registrar: RelayRegistrar,
    transactions: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<UserSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    /** Most recent received transaction (staff DB is entirely relay-origin), or null when empty. */
    val latest: StateFlow<TransactionRecord?> = transactions.observeRecords()
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _scanInvalid = MutableStateFlow(false)
    val scanInvalid: StateFlow<Boolean> = _scanInvalid.asStateFlow()

    /** Join the room encoded in [token]; persists SPOKE + room and registers the FCM token. */
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
            registrar.registerCurrentToken(room)
        }
        return true
    }

    /** Re-attempt FCM registration (e.g. after the user installs Google Play Services). */
    fun retryRegister() {
        viewModelScope.launch {
            val room = repo.settings.first().relayRoom
            if (room.roomId.isNotBlank()) registrar.registerCurrentToken(room)
        }
    }

    /** Leave the room and return to the mode picker (appMode = UNSET). */
    fun unpairToShop() {
        viewModelScope.launch {
            repo.setRelayRoom(RelayRoom())
            repo.updateRelayRole(RelayRole.NONE)
            repo.setRelayRegisterState(RelayRegisterState.IDLE)
            repo.updateAppMode(AppMode.UNSET)
        }
    }

    fun clearScanInvalid() { _scanInvalid.value = false }
}
