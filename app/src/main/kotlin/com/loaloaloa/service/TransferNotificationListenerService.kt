package com.loaloaloa.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.loaloaloa.data.model.TransactionModel
import com.loaloaloa.data.repository.UserSettingsRepository
import com.loaloaloa.di.IoDispatcher
import com.loaloaloa.ingest.TransactionIngestor
import com.loaloaloa.reliability.WatchdogScheduler
import com.loaloaloa.source.api.ApiTransactionSource
import com.loaloaloa.source.notification.DedupeGate
import com.loaloaloa.source.notification.NotificationProcessor
import com.loaloaloa.tts.TtsManager
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
 */
@AndroidEntryPoint
class TransferNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var processor: NotificationProcessor
    @Inject lateinit var dedupeGate: DedupeGate
    @Inject lateinit var userSettingsRepository: UserSettingsRepository
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var transactionIngestor: TransactionIngestor
    @Inject lateinit var apiSource: ApiTransactionSource

    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    private val scope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

    private val powerManager by lazy { getSystemService(PowerManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        WatchdogScheduler.schedule(this)
        startApiSourceIfEnabled()
    }

    /**
     * When the API source is enabled, start its polling loop and ingest every emitted
     * transaction through the same [TransactionIngestor] the notification path uses.
     * Guarded by [com.loaloaloa.data.model.ApiConfig.enabled]; no-ops otherwise.
     * The foreground service is already kept alive by the watchdog, so it hosts the loop.
     */
    private fun startApiSourceIfEnabled() {
        scope.launch {
            if (!userSettingsRepository.settings.first().api.enabled) return@launch
            apiSource.start()
            apiSource.transactions.collect { model ->
                runCatching { transactionIngestor.ingest(model) }
                    .onFailure { Timber.w(it, "Failed to ingest API transaction") }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification?.extras ?: return
        val packageName = sbn.packageName ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        // Read the clock once at the impurity boundary; reuse for both the model
        // timestamp and the dedupe window so they describe the same instant.
        val now = System.currentTimeMillis()

        // Acquire a partial wake lock SYNCHRONOUSLY here on the binder thread, before handing the
        // work to the background coroutine. With the screen off the CPU can suspend the instant
        // this callback returns — i.e. before the coroutine reaches TtsManager.speak, which only
        // takes its own lock deep inside the suspend chain. That unprotected gap froze the
        // announcement: the reported "screen off → no announcement" bug. Holding the lock from
        // here bridges delivery → speak; it is always released in the coroutine's finally, with a
        // timeout as a battery safety cap.
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        runCatching { wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS) }

        scope.launch {
            try {
                val settings = userSettingsRepository.settings.first()
                if (!settings.enableService) return@launch
                if (packageName in settings.excludedApps) return@launch

                val model = processor.process(
                    packageName, title, text, bigText,
                    timestampMillis = now,
                    customApps = settings.customApps,
                ) ?: return@launch

                val key = dedupeKey(model)
                if (dedupeGate.isDuplicate(key, now)) {
                    Timber.d("Duplicate notification dropped: %s", key)
                    return@launch
                }

                // Shared funnel: persist always, announce per AnnouncePolicy (enableService,
                // speakOption direction, quiet hours). Dedupe stays here at the source.
                transactionIngestor.ingest(model)
            } finally {
                if (wakeLock.isHeld) runCatching { wakeLock.release() }
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
        apiSource.stop()
        ttsManager.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    /** Schedule a near-future restart broadcast so the listener is nudged back alive. */
    private fun scheduleRestart() {
        try {
            val intent = Intent(this, ServiceRestartReceiver::class.java)
                .setAction(ServiceRestartReceiver.ACTION_RESTART_SERVICE)
            val pending = PendingIntent.getBroadcast(this, 1, intent, pendingIntentFlags())
            val alarmManager = getSystemService(AlarmManager::class.java)
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + RESTART_DELAY_MILLIS,
                pending,
            )
            Timber.d("Scheduled listener restart broadcast")
        } catch (e: Exception) {
            Timber.w(e, "Failed to schedule restart")
        }
    }

    private fun dedupeKey(model: TransactionModel): String =
        model.appId + "|" + model.amount + "|" + model.rawText.filter { !it.isWhitespace() }

    private fun startForegroundNotification() {
        val channelId = CHANNEL_ID
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Loa Loa Loa service",
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
            .setContentTitle("Loa Loa Loa đang lắng nghe")
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
        private const val CHANNEL_ID = "loaloaloa_service"
        private const val FOREGROUND_ID = 1001
        private const val ACTION_STOP = "com.loaloaloa.action.STOP"
        private const val RESTART_DELAY_MILLIS = 2_000L

        /** Wake-lock tag + safety-cap timeout bridging notification delivery → TTS speak. */
        private const val WAKE_LOCK_TAG = "LoaLoaLoa:listener"
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L
    }
}
