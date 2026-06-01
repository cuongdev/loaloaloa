package com.tingting.notifier.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tingting.notifier.data.model.SpeakOption
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.UserSettings
import com.tingting.notifier.data.repository.TransactionRepository
import com.tingting.notifier.data.repository.UserSettingsRepository
import com.tingting.notifier.di.IoDispatcher
import com.tingting.notifier.source.notification.DedupeGate
import com.tingting.notifier.source.notification.NotificationProcessor
import com.tingting.notifier.tts.TtsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Thin Android glue for the notification detection source. Extracts notification
 * extras and delegates all logic to the pure [NotificationProcessor] / [DedupeGate],
 * then persists and announces on a background scope. This is the impurity boundary:
 * `System.currentTimeMillis()` is read here, not in the testable classes.
 *
 * TODO(Plan 4): reboot restart (BootReceiver), task-removal restart, WorkManager
 * watchdog, and battery-optimization exemption are out of scope for this plan.
 */
@AndroidEntryPoint
class TransferNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var processor: NotificationProcessor
    @Inject lateinit var dedupeGate: DedupeGate
    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var userSettingsRepository: UserSettingsRepository
    @Inject lateinit var ttsManager: TtsManager

    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    private val scope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification?.extras ?: return
        val packageName = sbn.packageName ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val timestamp = System.currentTimeMillis()

        scope.launch {
            val settings = userSettingsRepository.settings.first()
            if (!settings.enableService) return@launch
            if (packageName in settings.excludedApps) return@launch

            val model = processor.process(packageName, title, text, bigText, timestamp)
                ?: return@launch

            val key = dedupeKey(model)
            if (dedupeGate.isDuplicate(key, System.currentTimeMillis())) {
                Timber.d("Duplicate notification dropped: %s", key)
                return@launch
            }

            runCatching { transactionRepository.addTransaction(model) }
                .onFailure { Timber.w(it, "Failed to persist transaction") }

            if (shouldAnnounce(model, settings)) {
                runCatching { ttsManager.speak(model, settings) }
                    .onFailure { Timber.w(it, "Failed to announce transaction") }
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Ask the platform to rebind so we keep receiving notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, TransferNotificationListenerService::class.java))
        }
    }

    override fun onDestroy() {
        ttsManager.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun shouldAnnounce(model: TransactionModel, settings: UserSettings): Boolean =
        when (settings.speakOption) {
            SpeakOption.BOTH -> true
            SpeakOption.INCOME_ONLY -> model.isIncome
            SpeakOption.OUTGOING_ONLY -> !model.isIncome
        }

    private fun dedupeKey(model: TransactionModel): String =
        model.appId + "|" + model.amount + "|" + model.rawText.filter { !it.isWhitespace() }

    private fun startForegroundNotification() {
        val channelId = CHANNEL_ID
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "TingTing service",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, TransferNotificationListenerService::class.java).setAction(ACTION_STOP),
            pendingIntentFlags(),
        )

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("TingTing đang lắng nghe")
            .setContentText("Đang theo dõi thông báo chuyển khoản")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, "Dừng", stopIntent).build(),
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    private fun pendingIntentFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    companion object {
        private const val CHANNEL_ID = "tingting_service"
        private const val FOREGROUND_ID = 1001
        private const val ACTION_STOP = "com.tingting.notifier.action.STOP"
    }
}
