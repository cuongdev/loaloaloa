package com.loaloaloa.tts

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.QuietHours
import org.junit.Test

class QuietHoursGateTest {

    private val gate = QuietHoursGate()

    @Test fun `disabled quiet hours are never quiet`() {
        val qh = QuietHours(enabled = false, startMinutes = 1320, endMinutes = 420)
        assertThat(gate.isQuiet(nowMinutesOfDay = 1380, quietHours = qh)).isFalse()
    }

    @Test fun `equal start and end is never quiet`() {
        val qh = QuietHours(enabled = true, startMinutes = 600, endMinutes = 600)
        assertThat(gate.isQuiet(nowMinutesOfDay = 600, quietHours = qh)).isFalse()
    }

    // Same-day window: 09:00 (540) .. 17:00 (1020)
    @Test fun `same-day window is quiet inside the range`() {
        val qh = QuietHours(enabled = true, startMinutes = 540, endMinutes = 1020)
        assertThat(gate.isQuiet(nowMinutesOfDay = 600, quietHours = qh)).isTrue()
    }

    @Test fun `same-day window is quiet at the start boundary`() {
        val qh = QuietHours(enabled = true, startMinutes = 540, endMinutes = 1020)
        assertThat(gate.isQuiet(nowMinutesOfDay = 540, quietHours = qh)).isTrue()
    }

    @Test fun `same-day window is not quiet at the end boundary`() {
        val qh = QuietHours(enabled = true, startMinutes = 540, endMinutes = 1020)
        assertThat(gate.isQuiet(nowMinutesOfDay = 1020, quietHours = qh)).isFalse()
    }

    @Test fun `same-day window is not quiet outside the range`() {
        val qh = QuietHours(enabled = true, startMinutes = 540, endMinutes = 1020)
        assertThat(gate.isQuiet(nowMinutesOfDay = 1021, quietHours = qh)).isFalse()
        assertThat(gate.isQuiet(nowMinutesOfDay = 539, quietHours = qh)).isFalse()
    }

    // Wrap-around window: 22:00 (1320) .. 07:00 (420)
    @Test fun `wrap-around window is quiet late at night`() {
        val qh = QuietHours(enabled = true, startMinutes = 1320, endMinutes = 420)
        assertThat(gate.isQuiet(nowMinutesOfDay = 1380, quietHours = qh)).isTrue() // 23:00
    }

    @Test fun `wrap-around window is quiet early in the morning`() {
        val qh = QuietHours(enabled = true, startMinutes = 1320, endMinutes = 420)
        assertThat(gate.isQuiet(nowMinutesOfDay = 60, quietHours = qh)).isTrue() // 01:00
    }

    @Test fun `wrap-around window is quiet at the start boundary`() {
        val qh = QuietHours(enabled = true, startMinutes = 1320, endMinutes = 420)
        assertThat(gate.isQuiet(nowMinutesOfDay = 1320, quietHours = qh)).isTrue()
    }

    @Test fun `wrap-around window is not quiet at the end boundary`() {
        val qh = QuietHours(enabled = true, startMinutes = 1320, endMinutes = 420)
        assertThat(gate.isQuiet(nowMinutesOfDay = 420, quietHours = qh)).isFalse() // 07:00
    }

    @Test fun `wrap-around window is not quiet during the day`() {
        val qh = QuietHours(enabled = true, startMinutes = 1320, endMinutes = 420)
        assertThat(gate.isQuiet(nowMinutesOfDay = 720, quietHours = qh)).isFalse() // 12:00
    }
}
