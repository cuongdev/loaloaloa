package com.tingting.notifier.permission

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OemAutostartTest {

    @Test fun `xiaomi maps to miui security center`() {
        val c = OemAutostart.componentFor("Xiaomi")!!
        assertThat(c.first).isEqualTo("com.miui.securitycenter")
    }

    @Test fun `redmi and poco are treated as xiaomi`() {
        assertThat(OemAutostart.componentFor("Redmi")!!.first).isEqualTo("com.miui.securitycenter")
        assertThat(OemAutostart.componentFor("POCO")!!.first).isEqualTo("com.miui.securitycenter")
    }

    @Test fun `oppo maps to coloros safe center`() {
        assertThat(OemAutostart.componentFor("OPPO")!!.first).isEqualTo("com.coloros.safecenter")
    }

    @Test fun `realme is treated as oppo coloros`() {
        assertThat(OemAutostart.componentFor("realme")!!.first).isEqualTo("com.coloros.safecenter")
    }

    @Test fun `vivo maps to vivo permission manager`() {
        assertThat(OemAutostart.componentFor("vivo")!!.first).isEqualTo("com.vivo.permissionmanager")
    }

    @Test fun `huawei maps to systemmanager`() {
        assertThat(OemAutostart.componentFor("HUAWEI")!!.first).isEqualTo("com.huawei.systemmanager")
    }

    @Test fun `honor is treated as huawei`() {
        assertThat(OemAutostart.componentFor("Honor")!!.first).isEqualTo("com.huawei.systemmanager")
    }

    @Test fun `samsung maps to device care`() {
        assertThat(OemAutostart.componentFor("samsung")!!.first).isEqualTo("com.samsung.android.lool")
    }

    @Test fun `letv maps to letv settings`() {
        assertThat(OemAutostart.componentFor("Letv")!!.first).isEqualTo("com.letv.android.letvsafe")
    }

    @Test fun `matching is case insensitive and trims whitespace`() {
        assertThat(OemAutostart.componentFor("  xIaOmI  ")!!.first).isEqualTo("com.miui.securitycenter")
    }

    @Test fun `unknown manufacturer returns null`() {
        assertThat(OemAutostart.componentFor("Google")).isNull()
        assertThat(OemAutostart.componentFor("")).isNull()
        assertThat(OemAutostart.componentFor("SomeRandomOem")).isNull()
    }

    @Test fun `component class is non-blank for known manufacturers`() {
        assertThat(OemAutostart.componentFor("Xiaomi")!!.second).isNotEmpty()
        assertThat(OemAutostart.componentFor("OPPO")!!.second).isNotEmpty()
        assertThat(OemAutostart.componentFor("vivo")!!.second).isNotEmpty()
    }
}
