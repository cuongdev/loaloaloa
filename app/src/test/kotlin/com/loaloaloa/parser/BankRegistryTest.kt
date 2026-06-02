package com.loaloaloa.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BankRegistryTest {

    @Test fun `resolves known bank package to display name`() {
        assertThat(BankRegistry.nameFor("com.VCB")).isEqualTo("Vietcombank")
        assertThat(BankRegistry.nameFor("com.mbmobile")).isEqualTo("MB Bank")
        assertThat(BankRegistry.nameFor("vn.com.techcombank.bb.app")).isEqualTo("Techcombank")
        assertThat(BankRegistry.nameFor("com.mservice.momotransfer")).isEqualTo("MoMo")
        assertThat(BankRegistry.nameFor("vn.com.vng.zalopay")).isEqualTo("ZaloPay")
    }

    @Test fun `returns null for non-bank package`() {
        assertThat(BankRegistry.nameFor("com.whatsapp")).isNull()
        assertThat(BankRegistry.nameFor("com.facebook.katana")).isNull()
    }

    @Test fun `isBank reflects membership`() {
        assertThat(BankRegistry.isBank("com.VCB")).isTrue()
        assertThat(BankRegistry.isBank("com.whatsapp")).isFalse()
    }

    @Test fun `registry covers a broad supported set and excludes the malformed entry`() {
        // 54 real package ids recovered from the original (its empty-string slot is dropped).
        assertThat(BankRegistry.supportedPackages().size).isAtLeast(50)
        assertThat(BankRegistry.supportedPackages()).doesNotContain("")
    }
}
