package com.tingting.notifier.source.notification

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DedupeGateTest {

    @Test fun `first sighting of a key is not a duplicate`() {
        val gate = DedupeGate(windowMillis = 500)
        assertThat(gate.isDuplicate("k", nowMillis = 1_000)).isFalse()
    }

    @Test fun `same key within window is a duplicate`() {
        val gate = DedupeGate(windowMillis = 500)
        assertThat(gate.isDuplicate("k", nowMillis = 1_000)).isFalse()
        assertThat(gate.isDuplicate("k", nowMillis = 1_200)).isTrue()
    }

    @Test fun `same key exactly at window boundary is not a duplicate`() {
        val gate = DedupeGate(windowMillis = 500)
        assertThat(gate.isDuplicate("k", nowMillis = 1_000)).isFalse()
        // now - last == window → outside the window → not a duplicate
        assertThat(gate.isDuplicate("k", nowMillis = 1_500)).isFalse()
    }

    @Test fun `same key after window is not a duplicate`() {
        val gate = DedupeGate(windowMillis = 500)
        assertThat(gate.isDuplicate("k", nowMillis = 1_000)).isFalse()
        assertThat(gate.isDuplicate("k", nowMillis = 2_000)).isFalse()
    }

    @Test fun `different keys never dedupe each other`() {
        val gate = DedupeGate(windowMillis = 500)
        assertThat(gate.isDuplicate("a", nowMillis = 1_000)).isFalse()
        assertThat(gate.isDuplicate("b", nowMillis = 1_001)).isFalse()
    }

    @Test fun `recording resets the window so a later repeat within window dedupes`() {
        val gate = DedupeGate(windowMillis = 500)
        assertThat(gate.isDuplicate("k", nowMillis = 1_000)).isFalse() // records 1000
        assertThat(gate.isDuplicate("k", nowMillis = 2_000)).isFalse() // records 2000 (outside)
        assertThat(gate.isDuplicate("k", nowMillis = 2_300)).isTrue()  // within 500 of 2000
    }

    @Test fun `default window is 500ms`() {
        val gate = DedupeGate()
        assertThat(gate.isDuplicate("k", nowMillis = 0)).isFalse()
        assertThat(gate.isDuplicate("k", nowMillis = 499)).isTrue()
        assertThat(gate.isDuplicate("k", nowMillis = 999)).isFalse() // 999 - 499 == 500 → not dup
    }
}
