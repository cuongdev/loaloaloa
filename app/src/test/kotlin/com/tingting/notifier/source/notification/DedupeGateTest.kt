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

    @Test fun `stale entries are evicted once the window has passed`() {
        val gate = DedupeGate(windowMillis = 500)
        gate.isDuplicate("a", nowMillis = 1_000)
        gate.isDuplicate("b", nowMillis = 1_100)
        assertThat(gate.trackedKeyCount()).isEqualTo(2)

        // A new event well past the window sweeps both stale keys before recording itself.
        assertThat(gate.isDuplicate("c", nowMillis = 5_000)).isFalse()
        assertThat(gate.trackedKeyCount()).isEqualTo(1) // only "c" remains
    }

    @Test fun `entries still inside the window are retained during sweep`() {
        val gate = DedupeGate(windowMillis = 500)
        gate.isDuplicate("a", nowMillis = 1_000)
        // "b" arrives within the window of "a", so "a" is not yet stale and survives.
        gate.isDuplicate("b", nowMillis = 1_200)
        assertThat(gate.trackedKeyCount()).isEqualTo(2)
    }

    @Test fun `map stays bounded across many distinct keys past the window`() {
        val gate = DedupeGate(windowMillis = 500)
        // Each key is more than a full window apart, so every sweep clears the prior one.
        for (i in 0 until 1_000) {
            gate.isDuplicate("k$i", nowMillis = i.toLong() * 1_000)
        }
        assertThat(gate.trackedKeyCount()).isEqualTo(1)
    }
}
