package com.tingting.notifier.ingest

import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.repository.TransactionRepository
import com.tingting.notifier.data.repository.UserSettingsRepository
import com.tingting.notifier.tts.TtsManager
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * The single funnel every [com.tingting.notifier.source.TransactionSource] feeds.
 * Persists the transaction always (so history is complete) and announces it iff
 * [AnnouncePolicy] permits. Dedupe stays at the source/caller, NOT here.
 *
 * This is the impurity boundary for the announce decision: the clock is read here
 * via [LocalTime.now]; the actual rule it feeds ([AnnouncePolicy]) is pure and tested.
 */
@Singleton
class TransactionIngestor @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ttsManager: TtsManager,
    private val userSettingsRepository: UserSettingsRepository,
    private val announcePolicy: AnnouncePolicy,
) {
    /** Persist [model], then announce it when the current settings + time permit. */
    suspend fun ingest(model: TransactionModel) {
        val settings = userSettingsRepository.settings.first()

        // Persist regardless of announcement policy so history is always complete.
        runCatching { transactionRepository.addTransaction(model) }
            .onFailure { Timber.w(it, "Failed to persist transaction") }

        val nowMinutesOfDay = LocalTime.now().let { it.hour * 60 + it.minute }
        if (announcePolicy.shouldAnnounce(model, settings, nowMinutesOfDay)) {
            runCatching { ttsManager.speak(model, settings) }
                .onFailure { Timber.w(it, "Failed to announce transaction") }
        }
    }
}
