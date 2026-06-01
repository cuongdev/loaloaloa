package com.tingting.notifier.domain

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.tingting.notifier.data.model.ApiConfig
import com.tingting.notifier.data.model.AudioOutput
import com.tingting.notifier.data.model.QuietHours
import com.tingting.notifier.data.model.SpeakOption
import com.tingting.notifier.data.model.UserSettings
import com.tingting.notifier.data.serializer.UserSettingsSerializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UserSettingsRepositoryImplTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var dataStore: DataStore<UserSettings>
    private lateinit var repo: UserSettingsRepositoryImpl

    @Before fun setUp() {
        val file = File(tmp.newFolder(), "user_settings.json")
        dataStore = DataStoreFactory.create(serializer = UserSettingsSerializer) { file }
        repo = UserSettingsRepositoryImpl(dataStore)
    }

    @Test fun `settings starts at defaults`() = runTest {
        assertThat(repo.settings.first()).isEqualTo(UserSettings())
    }

    @Test fun `updateEnableService toggles the field`() = runTest {
        repo.updateEnableService(true)
        assertThat(repo.settings.first().enableService).isTrue()
    }

    @Test fun `updateSpeakOption changes the field`() = runTest {
        repo.updateSpeakOption(SpeakOption.OUTGOING_ONLY)
        assertThat(repo.settings.first().speakOption).isEqualTo(SpeakOption.OUTGOING_ONLY)
    }

    @Test fun `updateExcludedApps replaces the list`() = runTest {
        repo.updateExcludedApps(listOf("com.whatsapp", "com.facebook.katana"))
        assertThat(repo.settings.first().excludedApps)
            .containsExactly("com.whatsapp", "com.facebook.katana").inOrder()
    }

    @Test fun `updateSpeakShortMessage toggles`() = runTest {
        repo.updateSpeakShortMessage(true)
        assertThat(repo.settings.first().speakShortMessage).isTrue()
    }

    @Test fun `updateAudioOutput changes the field`() = runTest {
        repo.updateAudioOutput(AudioOutput.MEDIA)
        assertThat(repo.settings.first().audioOutput).isEqualTo(AudioOutput.MEDIA)
    }

    @Test fun `updateForceMaxVolume toggles`() = runTest {
        repo.updateForceMaxVolume(true)
        assertThat(repo.settings.first().forceMaxVolume).isTrue()
    }

    @Test fun `updateEnableAudioFocus toggles`() = runTest {
        repo.updateEnableAudioFocus(false)
        assertThat(repo.settings.first().enableAudioFocus).isFalse()
    }

    @Test fun `updateSpeakInSilentMode toggles`() = runTest {
        repo.updateSpeakInSilentMode(true)
        assertThat(repo.settings.first().speakInSilentMode).isTrue()
    }

    @Test fun `updatePlayChime toggles`() = runTest {
        repo.updatePlayChime(false)
        assertThat(repo.settings.first().playChime).isFalse()
    }

    @Test fun `updateRepeat toggles`() = runTest {
        repo.updateRepeat(true)
        assertThat(repo.settings.first().repeat).isTrue()
    }

    @Test fun `setQuietHours replaces the whole block`() = runTest {
        val qh = QuietHours(enabled = true, startMinutes = 1320, endMinutes = 420)
        repo.setQuietHours(qh)
        assertThat(repo.settings.first().quietHours).isEqualTo(qh)
    }

    @Test fun `setApiConfig replaces the whole block`() = runTest {
        val api = ApiConfig(
            enabled = true,
            baseUrl = "https://my.sepay.vn",
            token = "tok",
            account = "0123",
            pollSeconds = 10,
        )
        repo.setApiConfig(api)
        assertThat(repo.settings.first().api).isEqualTo(api)
    }

    @Test fun `updates are independent and persist together`() = runTest {
        repo.updateEnableService(true)
        repo.updatePlayChime(false)
        val s = repo.settings.first()
        assertThat(s.enableService).isTrue()
        assertThat(s.playChime).isFalse()
        // unchanged fields keep defaults
        assertThat(s.speakOption).isEqualTo(SpeakOption.BOTH)
    }
}
