package com.loaloaloa.ui.debug

import com.google.common.truth.Truth.assertThat
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.parser.TransactionParser
import com.loaloaloa.source.notification.NotificationProcessor
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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
class DebugViewModelTest {

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val clock = Clock.fixed(Instant.parse("2026-06-01T05:00:00Z"), zone)

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private var captured: TransactionModel? = null

    private fun vm() = DebugViewModel(
        processor = NotificationProcessor(TransactionParser()),
        ingest = DebugIngest { captured = it },
        clock = clock,
    )

    @Test fun `parseable income sample ingests and reports success`() = runTest {
        val model = vm()
        model.setBankPackage("com.mbmobile")
        model.setText("TK 0399999999(VND) +500,000 ND:Thanh toan don hang")

        model.run()
        runCurrent()

        assertThat(captured).isNotNull()
        assertThat(captured!!.amount).isEqualTo(500_000)
        assertThat(captured!!.isIncome).isTrue()
        assertThat(captured!!.bankName).isEqualTo("MB Bank")

        val result = model.result.first()
        assertThat(result).isInstanceOf(DebugResult.Success::class.java)
        assertThat((result as DebugResult.Success).model.amount).isEqualTo(500_000)
    }

    @Test fun `parseable outgoing sample has isIncome false`() = runTest {
        val model = vm()
        model.setBankPackage("com.mbmobile")
        model.setText("TK 0399999999(VND) -1,200,000 ND:Thanh toan")

        model.run()
        runCurrent()

        assertThat(captured).isNotNull()
        assertThat(captured!!.isIncome).isFalse()
        assertThat(model.result.first()).isInstanceOf(DebugResult.Success::class.java)
    }

    @Test fun `unparseable text reports failure and does not ingest`() = runTest {
        val model = vm()
        model.setBankPackage("com.mbmobile")
        model.setText("Khuyến mãi tháng 6, không có số tiền")

        model.run()
        runCurrent()

        assertThat(captured).isNull()
        assertThat(model.result.first()).isEqualTo(DebugResult.Failure)
    }

    @Test fun `timestamp comes from the clock`() = runTest {
        val model = vm()
        model.setBankPackage("com.mbmobile")
        model.setText("TK 0399999999(VND) +500,000 ND:test")

        model.run()
        runCurrent()

        assertThat(captured!!.timestamp).isEqualTo(clock.millis())
    }
}
