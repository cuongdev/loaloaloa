package com.tingting.notifier.ui.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OnboardingUiState(
    val notificationAccessGranted: Boolean = false,
    val batteryExempt: Boolean = false,
    /** Whether the OEM autostart step is offered (an autostart Intent exists for this device). */
    val autostartAvailable: Boolean = false,
) {
    /** Onboarding can finish once the critical (notification access) grant is in place. */
    val canFinish: Boolean get() = notificationAccessGranted
}

@HiltViewModel
class OnboardingViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** The Activity pushes freshly-checked permission state here on launch/resume. */
    fun refresh(notificationAccessGranted: Boolean, batteryExempt: Boolean, autostartAvailable: Boolean) {
        _state.update {
            it.copy(
                notificationAccessGranted = notificationAccessGranted,
                batteryExempt = batteryExempt,
                autostartAvailable = autostartAvailable,
            )
        }
    }
}
