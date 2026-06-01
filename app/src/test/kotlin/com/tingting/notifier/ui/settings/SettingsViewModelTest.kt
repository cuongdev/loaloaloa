package com.tingting.notifier.ui.settings

import com.google.common.truth.Truth.assertThat
import com.tingting.notifier.data.model.ApiConfig
import com.tingting.notifier.data.model.AudioOutput
import com.tingting.notifier.data.model.QuietHours
import com.tingting.notifier.data.model.SpeakOption
import com.tingting.notifier.source.api.SePayConnectionTester
import com.tingting.notifier.ui.fake.FakeUserSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val okTester = SePayConnectionTester { Result.success(3) }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `every setter reaches the repository`() = runTest {
        val repo = FakeUserSettingsRepository()
        val vm = SettingsViewModel(repo, okTester)

        vm.setSpeakOption(SpeakOption.INCOME_ONLY)
        vm.setPlayChime(false)
        vm.setRepeat(true)
        vm.setSpeakShortMessage(true)
        vm.setForceMaxVolume(true)
        vm.setSpeakInSilentMode(true)
        vm.setAudioOutput(AudioOutput.ALARM)
        vm.setEnableAudioFocus(false)
        vm.setQuietHours(QuietHours(enabled = true, startMinutes = 1320, endMinutes = 420))
        vm.setExcludedApps(listOf("com.spam.app"))
        vm.setApiConfig(ApiConfig(enabled = true, baseUrl = "https://my.sepay", token = "t", account = "123", pollSeconds = 15))
        runCurrent()

        val s = repo.current
        assertThat(s.speakOption).isEqualTo(SpeakOption.INCOME_ONLY)
        assertThat(s.playChime).isFalse()
        assertThat(s.repeat).isTrue()
        assertThat(s.speakShortMessage).isTrue()
        assertThat(s.forceMaxVolume).isTrue()
        assertThat(s.speakInSilentMode).isTrue()
        assertThat(s.audioOutput).isEqualTo(AudioOutput.ALARM)
        assertThat(s.enableAudioFocus).isFalse()
        assertThat(s.quietHours).isEqualTo(QuietHours(enabled = true, startMinutes = 1320, endMinutes = 420))
        assertThat(s.excludedApps).containsExactly("com.spam.app")
        assertThat(s.api.enabled).isTrue()
        assertThat(s.api.baseUrl).isEqualTo("https://my.sepay")
        assertThat(s.api.pollSeconds).isEqualTo(15)
    }

    @Test fun `testConnection success publishes row count`() = runTest {
        val vm = SettingsViewModel(FakeUserSettingsRepository(), SePayConnectionTester { Result.success(5) })

        vm.testConnection(ApiConfig(baseUrl = "https://x", token = "t"))
        runCurrent()

        val state = vm.connectionTest.first()
        assertThat(state).isEqualTo(ConnectionTestState.Success(5))
    }

    @Test fun `testConnection failure publishes the error message`() = runTest {
        val vm = SettingsViewModel(
            FakeUserSettingsRepository(),
            SePayConnectionTester { Result.failure(IllegalStateException("timeout")) },
        )

        vm.testConnection(ApiConfig(baseUrl = "https://x", token = "t"))
        runCurrent()

        assertThat(vm.connectionTest.first()).isEqualTo(ConnectionTestState.Failure("timeout"))
    }

    @Test fun `testConnection passes the supplied config to the tester`() = runTest {
        var seen: ApiConfig? = null
        val vm = SettingsViewModel(FakeUserSettingsRepository(), SePayConnectionTester { cfg -> seen = cfg; Result.success(0) })
        val config = ApiConfig(baseUrl = "https://entered", token = "typed", account = "999", pollSeconds = 20)

        vm.testConnection(config)
        runCurrent()

        assertThat(seen).isEqualTo(config)
    }

    @Test fun `clearConnectionTest resets to idle`() = runTest {
        val vm = SettingsViewModel(FakeUserSettingsRepository(), okTester)
        vm.testConnection(ApiConfig(baseUrl = "https://x", token = "t"))
        runCurrent()
        vm.clearConnectionTest()
        assertThat(vm.connectionTest.first()).isEqualTo(ConnectionTestState.Idle)
    }
}
