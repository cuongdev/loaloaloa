package com.tingting.notifier.reliability

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchdogDecisionTest {

    @Test fun `rebinds when service enabled and access granted`() {
        assertThat(
            WatchdogDecision.shouldRebind(enableService = true, accessGranted = true),
        ).isTrue()
    }

    @Test fun `does not rebind when service disabled`() {
        assertThat(
            WatchdogDecision.shouldRebind(enableService = false, accessGranted = true),
        ).isFalse()
    }

    @Test fun `does not rebind when access revoked`() {
        assertThat(
            WatchdogDecision.shouldRebind(enableService = true, accessGranted = false),
        ).isFalse()
    }

    @Test fun `does not rebind when both off`() {
        assertThat(
            WatchdogDecision.shouldRebind(enableService = false, accessGranted = false),
        ).isFalse()
    }
}
