package com.tingting.notifier.ui.util

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class DateLabelsTest {

    private val today = LocalDate.of(2026, 6, 1) // Monday

    @Test fun `today and yesterday have vietnamese labels`() {
        assertThat(DateLabels.groupLabel(today, today)).isEqualTo("Hôm nay")
        assertThat(DateLabels.groupLabel(today.minusDays(1), today)).isEqualTo("Hôm qua")
    }

    @Test fun `older dates use d thg M`() {
        assertThat(DateLabels.groupLabel(LocalDate.of(2026, 5, 30), today)).isEqualTo("30 thg 5")
        assertThat(DateLabels.groupLabel(LocalDate.of(2026, 1, 9), today)).isEqualTo("9 thg 1")
    }

    @Test fun `weekday abbreviations are vietnamese`() {
        assertThat(DateLabels.weekdayShort(LocalDate.of(2026, 6, 1))).isEqualTo("T2") // Monday
        assertThat(DateLabels.weekdayShort(LocalDate.of(2026, 6, 7))).isEqualTo("CN") // Sunday
    }
}
