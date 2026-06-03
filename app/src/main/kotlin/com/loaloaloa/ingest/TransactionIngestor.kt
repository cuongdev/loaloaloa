package com.loaloaloa.ingest

import android.content.Context
import com.loaloaloa.data.model.RelayRole
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.model.effectiveWebhooks
import com.loaloaloa.data.repository.TransactionRepository
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.relay.RelaySender
import com.loaloaloa.tts.TtsManager
import com.loaloaloa.webhook.WebhookPolicy
import com.loaloaloa.webhook.WebhookSender
import com.loaloaloa.widget.LoaLoaLoaWidget
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * The single funnel every [com.loaloaloa.source.TransactionSource] feeds.
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
    private val webhookSender: WebhookSender,
    private val relaySender: RelaySender,
    @ApplicationContext private val appContext: Context,
) {
    /** Persist [model], then announce it when the current settings + time permit. */
    suspend fun ingest(model: TransactionModel) {
        val settings = userSettingsRepository.settings.first()

        // Persist regardless of announcement policy so history is always complete. On a staff
        // device with an open shift, stamp the on-duty employee name(s) onto the note so each
        // transaction records who was working when it arrived (empty staff = unchanged note).
        val note = StaffNote.combine(base = "", activeStaff = settings.activeStaff)
        runCatching { transactionRepository.addTransaction(model, note) }
            .onFailure { Timber.w(it, "Failed to persist transaction") }

        // Live-refresh the home-screen widget so today's total + latest line update as
        // money arrives. Guarded so a widget failure never affects ingest.
        runCatching { LoaLoaLoaWidget().updateAll(appContext) }
            .onFailure { Timber.w(it, "Failed to refresh widget") }

        val nowMinutesOfDay = LocalTime.now().let { it.hour * 60 + it.minute }

        // Outbound webhooks are independent of the announce decision: fire every configured
        // destination whose policy matches, so transactions are forwarded even when TTS is
        // gated (quiet hours, direction, etc.). All destinations fire together. A destination
        // with a shift window only fires while the current time is inside that window.
        settings.effectiveWebhooks.forEach { webhook ->
            if (WebhookPolicy.shouldFire(webhook, model.isIncome, nowMinutesOfDay)) {
                runCatching { webhookSender.enqueue(model, webhook) }
                    .onFailure { Timber.w(it, "Failed to enqueue webhook %s", webhook.id) }
            }
        }

        // Hub side of the device relay: forward every detected transaction (E2E-encrypted) to the
        // room's spokes. Independent of the announce decision, like webhooks, so spokes still get
        // it while the hub's own TTS is gated. Guarded so a relay failure never affects ingest.
        if (settings.relayRole == RelayRole.HUB) {
            runCatching { relaySender.relay(model, settings.relayRoom) }
                .onFailure { Timber.w(it, "Failed to enqueue relay push") }
        }

        if (announcePolicy.shouldAnnounce(model, settings, nowMinutesOfDay)) {
            // For the optional "Tổng hôm nay …" suffix, read today's income total (which now
            // includes the just-persisted transaction). Computed only when the feature is on
            // and this is income, so the common path keeps its single settings read.
            val dailyIncomeTotal = if (settings.speakDailyTotal && model.isIncome) {
                runCatching {
                    val zone = ZoneId.systemDefault()
                    val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
                    transactionRepository
                        .totalAmount(isIncome = true, from = startOfDay, to = System.currentTimeMillis())
                        .first()
                }.getOrNull()
            } else {
                null
            }
            runCatching { ttsManager.speak(model, settings, dailyIncomeTotal) }
                .onFailure { Timber.w(it, "Failed to announce transaction") }
        }
    }
}
