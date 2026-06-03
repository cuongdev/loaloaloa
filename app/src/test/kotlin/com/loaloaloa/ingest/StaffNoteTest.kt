package com.loaloaloa.ingest

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure formatting rules for stamping on-shift employee names onto a transaction note. */
class StaffNoteTest {

    @Test fun `empty roster leaves the base note unchanged`() {
        assertThat(StaffNote.combine(base = "", activeStaff = emptyList())).isEqualTo("")
        assertThat(StaffNote.combine(base = "khách quen", activeStaff = emptyList())).isEqualTo("khách quen")
    }

    @Test fun `single name onto an empty base`() {
        assertThat(StaffNote.combine(base = "", activeStaff = listOf("An"))).isEqualTo("NV: An")
    }

    @Test fun `multiple names are joined`() {
        assertThat(StaffNote.combine(base = "", activeStaff = listOf("An", "Bình", "Chi")))
            .isEqualTo("NV: An, Bình, Chi")
    }

    @Test fun `names are trimmed, blanks dropped, and duplicates removed in order`() {
        assertThat(StaffNote.combine(base = "", activeStaff = listOf("  An ", "", "Bình", "An")))
            .isEqualTo("NV: An, Bình")
    }

    @Test fun `existing base note is preserved and the tag appended`() {
        assertThat(StaffNote.combine(base = "tiền cọc", activeStaff = listOf("An")))
            .isEqualTo("tiền cọc — NV: An")
    }

    @Test fun `roster of only blanks leaves the base unchanged`() {
        assertThat(StaffNote.combine(base = "ghi chú", activeStaff = listOf("  ", ""))).isEqualTo("ghi chú")
    }
}
