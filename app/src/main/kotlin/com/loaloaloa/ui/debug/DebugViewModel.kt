package com.loaloaloa.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.parser.BankRegistry
import com.loaloaloa.source.notification.NotificationProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Seam over [com.loaloaloa.ingest.TransactionIngestor.ingest] so the ViewModel can be
 * unit-tested with a capturing lambda (mirrors the existing `WebhookTester` fun-interface pattern)
 * without constructing the ingestor's full collaborator graph.
 */
fun interface DebugIngest {
    suspend operator fun invoke(model: TransactionModel)
}

/** Outcome of a "Chạy thử" run, surfaced to the UI. */
sealed interface DebugResult {
    data object Idle : DebugResult
    data object Running : DebugResult
    data class Success(val model: TransactionModel) : DebugResult
    data object Failure : DebugResult
}

/** Default sample notification texts for the presets. */
object DebugSamples {
    const val INCOME = "TK 0399999999(VND) +500,000 ND:Thanh toan don hang"
    const val OUTGOING = "TK 0399999999(VND) -1,200,000 ND:Thanh toan don hang"
    const val DEFAULT_PACKAGE = "com.mbmobile" // MB Bank
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val processor: NotificationProcessor,
    private val ingest: DebugIngest,
    private val clock: Clock,
) : ViewModel() {

    private val _bankPackage = MutableStateFlow(DebugSamples.DEFAULT_PACKAGE)
    val bankPackage: StateFlow<String> = _bankPackage.asStateFlow()

    private val _text = MutableStateFlow(DebugSamples.INCOME)
    val text: StateFlow<String> = _text.asStateFlow()

    private val _result = MutableStateFlow<DebugResult>(DebugResult.Idle)
    val result: StateFlow<DebugResult> = _result.asStateFlow()

    fun setBankPackage(packageName: String) { _bankPackage.value = packageName }

    fun setText(value: String) { _text.value = value }

    /** Preset: replace the text with a realistic income sample. */
    fun useIncomeSample() { _text.value = DebugSamples.INCOME }

    /** Preset: replace the text with a realistic outgoing sample. */
    fun useOutgoingSample() { _text.value = DebugSamples.OUTGOING }

    /**
     * Fire the current text through the REAL pipeline: parse with [NotificationProcessor]; if it
     * yields a [TransactionModel], ingest it (persist + speak + webhook via [DebugIngest]) and
     * report [DebugResult.Success]; otherwise [DebugResult.Failure].
     */
    fun run() {
        _result.value = DebugResult.Running
        val pkg = _bankPackage.value
        val body = _text.value
        viewModelScope.launch {
            val model = processor.process(
                packageName = pkg,
                title = BankRegistry.nameFor(pkg),
                text = body,
                bigText = body,
                timestampMillis = clock.millis(),
            )
            _result.value = if (model != null) {
                ingest(model)
                DebugResult.Success(model)
            } else {
                DebugResult.Failure
            }
        }
    }

    fun clearResult() { _result.value = DebugResult.Idle }
}
