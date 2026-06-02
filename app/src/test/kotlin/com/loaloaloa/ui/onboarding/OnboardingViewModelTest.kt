package com.loaloaloa.ui.onboarding

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OnboardingViewModelTest {

    @Test fun `starts incomplete and cannot finish`() = runTest {
        val state = OnboardingViewModel().uiState.first()
        assertThat(state.notificationAccessGranted).isFalse()
        assertThat(state.canFinish).isFalse()
    }

    @Test fun `refresh updates each step flag`() = runTest {
        val vm = OnboardingViewModel()
        vm.refresh(notificationAccessGranted = true, batteryExempt = true, autostartAvailable = true)
        val state = vm.uiState.first()
        assertThat(state.notificationAccessGranted).isTrue()
        assertThat(state.batteryExempt).isTrue()
        assertThat(state.autostartAvailable).isTrue()
    }

    @Test fun `canFinish once notification access granted`() = runTest {
        val vm = OnboardingViewModel()
        vm.refresh(notificationAccessGranted = true, batteryExempt = false, autostartAvailable = false)
        assertThat(vm.uiState.first().canFinish).isTrue()
    }
}
