package com.loaloaloa.data.serializer

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.ApiConfig
import com.loaloaloa.data.model.AudioOutput
import com.loaloaloa.data.model.QuietHours
import com.loaloaloa.data.model.SpeakOption
import com.loaloaloa.data.model.RelayRegisterState
import com.loaloaloa.data.model.UserSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UserSettingsSerializerTest {

    private val serializer = UserSettingsSerializer

    @Test fun `default value matches UserSettings defaults`() {
        assertThat(serializer.defaultValue).isEqualTo(UserSettings())
    }

    @Test fun `round trips a non-default settings tree`() = runTest {
        val original = UserSettings(
            enableService = true,
            speakOption = SpeakOption.INCOME_ONLY,
            excludedApps = listOf("com.facebook.katana", "com.whatsapp"),
            speakShortMessage = true,
            audioOutput = AudioOutput.ALARM,
            forceMaxVolume = true,
            enableAudioFocus = false,
            speakInSilentMode = true,
            playChime = false,
            repeat = true,
            quietHours = QuietHours(enabled = true, startMinutes = 1320, endMinutes = 420),
            api = ApiConfig(
                enabled = true,
                baseUrl = "https://my.sepay.vn",
                token = "secret-token",
                account = "0123456789",
                pollSeconds = 15,
            ),
        )

        val out = ByteArrayOutputStream()
        serializer.writeTo(original, out)
        val restored = serializer.readFrom(ByteArrayInputStream(out.toByteArray()))

        assertThat(restored).isEqualTo(original)
    }

    @Test fun `corrupt input returns default instead of throwing`() = runTest {
        val garbage = ByteArrayInputStream("not json at all }{".toByteArray())
        assertThat(serializer.readFrom(garbage)).isEqualTo(UserSettings())
    }

    @Test fun `empty input returns default`() = runTest {
        val empty = ByteArrayInputStream(ByteArray(0))
        assertThat(serializer.readFrom(empty)).isEqualTo(UserSettings())
    }

    private suspend fun roundTrip(settings: UserSettings): UserSettings {
        val out = ByteArrayOutputStream()
        UserSettingsSerializer.writeTo(settings, out)
        return UserSettingsSerializer.readFrom(out.toByteArray().inputStream())
    }

    @Test fun `relayRegisterState defaults to IDLE and round-trips`() = runTest {
        val fresh = roundTrip(UserSettings())
        assertThat(fresh.relayRegisterState).isEqualTo(RelayRegisterState.IDLE)

        val saved = roundTrip(UserSettings(relayRegisterState = RelayRegisterState.NO_FCM))
        assertThat(saved.relayRegisterState).isEqualTo(RelayRegisterState.NO_FCM)
    }

    @Test fun `legacy JSON without relayRegisterState loads as IDLE`() = runTest {
        val legacyJson = """{"enableService":true}"""
        val loaded = UserSettingsSerializer.readFrom(legacyJson.byteInputStream())
        assertThat(loaded.relayRegisterState).isEqualTo(RelayRegisterState.IDLE)
    }
}
