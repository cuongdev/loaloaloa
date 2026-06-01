package com.tingting.notifier.ui.settings

import com.google.common.truth.Truth.assertThat
import com.tingting.notifier.data.model.WebhookConfig
import com.tingting.notifier.data.model.WebhookTrigger
import com.tingting.notifier.ui.fake.FakeUserSettingsRepository
import com.tingting.notifier.webhook.WebhookTester
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
class WebhookViewModelTest {

    private val okTester = WebhookTester { Result.success(200) }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `setWebhookConfig reaches the repository`() = runTest {
        val repo = FakeUserSettingsRepository()
        val vm = WebhookViewModel(repo, okTester)

        val config = WebhookConfig(
            enabled = true,
            url = "https://hook.example/abc",
            secret = "s3cr3t",
            trigger = WebhookTrigger.INCOME,
        )
        vm.setWebhookConfig(config)
        runCurrent()

        assertThat(repo.current.webhook).isEqualTo(config)
    }

    @Test fun `test success publishes the http status code`() = runTest {
        val vm = WebhookViewModel(FakeUserSettingsRepository(), WebhookTester { Result.success(204) })

        vm.test(WebhookConfig(enabled = true, url = "https://hook.example"))
        runCurrent()

        assertThat(vm.testState.first()).isEqualTo(WebhookTestState.Success(204))
    }

    @Test fun `test failure publishes the error message`() = runTest {
        val vm = WebhookViewModel(
            FakeUserSettingsRepository(),
            WebhookTester { Result.failure(IllegalStateException("HTTP 500")) },
        )

        vm.test(WebhookConfig(enabled = true, url = "https://hook.example"))
        runCurrent()

        assertThat(vm.testState.first()).isEqualTo(WebhookTestState.Failure("HTTP 500"))
    }

    @Test fun `test passes the supplied config to the tester`() = runTest {
        var seen: WebhookConfig? = null
        val vm = WebhookViewModel(FakeUserSettingsRepository(), WebhookTester { cfg -> seen = cfg; Result.success(200) })
        val config = WebhookConfig(enabled = true, url = "https://entered", secret = "k", trigger = WebhookTrigger.OUTGOING)

        vm.test(config)
        runCurrent()

        assertThat(seen).isEqualTo(config)
    }

    @Test fun `clearTest resets to idle`() = runTest {
        val vm = WebhookViewModel(FakeUserSettingsRepository(), okTester)
        vm.test(WebhookConfig(enabled = true, url = "https://hook.example"))
        runCurrent()

        vm.clearTest()

        assertThat(vm.testState.first()).isEqualTo(WebhookTestState.Idle)
    }
}
