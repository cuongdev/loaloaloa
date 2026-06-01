package com.tingting.notifier.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyFormatTest {

    @Test fun `income gets plus sign and trailing dong`() {
        assertThat(MoneyFormat.format(500_000, isIncome = true)).isEqualTo("+500.000 đ")
    }

    @Test fun `outgoing gets minus sign U+2212 and trailing dong`() {
        assertThat(MoneyFormat.format(1_100_000, isIncome = false)).isEqualTo("−1.100.000 đ")
    }

    @Test fun `groups thousands with dots`() {
        assertThat(MoneyFormat.format(2_450_000, isIncome = true)).isEqualTo("+2.450.000 đ")
        assertThat(MoneyFormat.format(12_800_000, isIncome = true)).isEqualTo("+12.800.000 đ")
    }

    @Test fun `small amounts are not grouped`() {
        assertThat(MoneyFormat.format(500, isIncome = true)).isEqualTo("+500 đ")
        assertThat(MoneyFormat.format(0, isIncome = true)).isEqualTo("+0 đ")
    }

    @Test fun `plain has no sign`() {
        assertThat(MoneyFormat.plain(12_800_000)).isEqualTo("12.800.000 đ")
    }

    @Test fun `negative input is treated as absolute`() {
        assertThat(MoneyFormat.format(-500_000, isIncome = false)).isEqualTo("−500.000 đ")
    }
}
