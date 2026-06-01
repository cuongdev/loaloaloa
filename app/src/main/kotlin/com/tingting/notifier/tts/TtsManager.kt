package com.tingting.notifier.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.tingting.notifier.data.model.AudioOutput
import com.tingting.notifier.data.model.TransactionModel
import com.tingting.notifier.data.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Announces transactions out loud in Vietnamese. Wraps [TextToSpeech] (Locale
 * `vi-VN`) and applies the user's audio policy: silent-mode awareness, audio focus,
 * force-max-volume (save/restore), optional chime, and repeat. Calls are serialized
 * with a [Mutex] so concurrent transfers queue rather than overlap.
 *
 * Pure speech-text construction is delegated to [SpeechTextBuilder]; this class is
 * intentionally thin framework glue and degrades (logs, no crash) when the engine or
 * the vi-VN voice is unavailable.
 *
 * Chime: an optional `res/raw/chime` audio asset. None ships yet, so [chimeResId]
 * is 0 and [UserSettings.playChime] no-ops until an asset is added — no code change
 * needed when one is.
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speechTextBuilder: SpeechTextBuilder,
) {
    private val mutex = Mutex()
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.forLanguageTag("vi-VN"))
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Timber.w("vi-VN TTS voice unavailable (code=%d); announcements degraded", result)
            }
            ready = true
        } else {
            Timber.w("TTS engine init failed (status=%d); announcements disabled", status)
        }
    }

    /** Resolve res/raw/chime if present; 0 when no asset ships (playChime no-ops). */
    private val chimeResId: Int =
        context.resources.getIdentifier("chime", "raw", context.packageName)

    /**
     * Speak [model] honoring [settings]. Suspends until the utterance(s) finish so
     * volume/focus are restored deterministically. Never throws on engine errors.
     */
    suspend fun speak(model: TransactionModel, settings: UserSettings) = mutex.withLock {
        if (!ready) {
            Timber.w("TTS not ready; dropping announcement")
            return@withLock
        }
        if (isSilenced(settings)) {
            Timber.d("Silent mode and speakInSilentMode=false; skipping announcement")
            return@withLock
        }

        val stream = streamFor(settings.audioOutput)
        val focusRequest = if (settings.enableAudioFocus) requestFocus(settings.audioOutput) else null
        val savedVolume = if (settings.forceMaxVolume) forceMaxVolume(stream) else null

        try {
            if (settings.playChime) playChime(settings.audioOutput)
            val sentence = speechTextBuilder.build(model, settings)
            val times = if (settings.repeat) 2 else 1
            repeat(times) { index -> speakOnce(sentence, settings.audioOutput, index) }
        } catch (e: Exception) {
            Timber.w(e, "Announcement failed")
        } finally {
            if (savedVolume != null) restoreVolume(stream, savedVolume)
            if (focusRequest != null) abandonFocus(focusRequest)
        }
    }

    private fun isSilenced(settings: UserSettings): Boolean {
        if (settings.speakInSilentMode) return false
        return audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    private fun streamFor(output: AudioOutput): Int = when (output) {
        AudioOutput.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        AudioOutput.ALARM -> AudioManager.STREAM_ALARM
        AudioOutput.MEDIA -> AudioManager.STREAM_MUSIC
    }

    private fun usageFor(output: AudioOutput): Int = when (output) {
        AudioOutput.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
        AudioOutput.ALARM -> AudioAttributes.USAGE_ALARM
        AudioOutput.MEDIA -> AudioAttributes.USAGE_MEDIA
    }

    private fun audioAttributes(output: AudioOutput): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(usageFor(output))
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    private fun requestFocus(output: AudioOutput): AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes(output))
                .build()
            audioManager.requestAudioFocus(request)
            request
        } else {
            null
        }

    private fun abandonFocus(request: AudioFocusRequest) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(request)
        }
    }

    /** @return the volume to restore, or null if it could not be changed. */
    private fun forceMaxVolume(stream: Int): Int? = try {
        val current = audioManager.getStreamVolume(stream)
        val max = audioManager.getStreamMaxVolume(stream)
        audioManager.setStreamVolume(stream, max, 0)
        current
    } catch (e: SecurityException) {
        Timber.w(e, "Cannot force max volume (DND restriction?)")
        null
    }

    private fun restoreVolume(stream: Int, volume: Int) {
        try {
            audioManager.setStreamVolume(stream, volume, 0)
        } catch (e: SecurityException) {
            Timber.w(e, "Cannot restore volume")
        }
    }

    /**
     * Play the chime and SUSPEND until it finishes, so speech starts only after the
     * chime (spec §6: "chime, then speech"). No-ops immediately when no asset ships
     * ([chimeResId] == 0). Resumes on completion, on error, or after [CHIME_TIMEOUT_MS]
     * so a stuck player can never hang the announcement.
     */
    private suspend fun playChime(output: AudioOutput) {
        if (chimeResId == 0) return // no asset shipped yet
        val player = try {
            MediaPlayer.create(context, chimeResId)?.apply {
                setAudioAttributes(audioAttributes(output))
            }
        } catch (e: Exception) {
            Timber.w(e, "Chime creation failed")
            null
        } ?: return

        try {
            withTimeoutOrNull(CHIME_TIMEOUT_MS) {
                suspendCancellableCoroutine<Unit> { cont ->
                    player.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                    player.setOnErrorListener { _, what, extra ->
                        Timber.w("Chime playback error (what=%d, extra=%d)", what, extra)
                        if (cont.isActive) cont.resume(Unit)
                        true
                    }
                    cont.invokeOnCancellation { runCatching { player.stop() } }
                    player.start()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Chime playback failed")
        } finally {
            runCatching { player.release() }
        }
    }

    private suspend fun speakOnce(sentence: String, output: AudioOutput, index: Int) =
        suspendCancellableCoroutine<Unit> { cont ->
            val utteranceId = "tingting-$index-${System.nanoTime()}"
            tts.setAudioAttributes(audioAttributes(output))
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (cont.isActive) cont.resume(Unit)
                }
            })
            val result = tts.speak(sentence, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (result != TextToSpeech.SUCCESS && cont.isActive) {
                Timber.w("tts.speak returned error %d", result)
                cont.resume(Unit)
            }
        }

    /** Release the engine. Call from the owning service's onDestroy. */
    fun shutdown() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (e: Exception) {
            Timber.w(e, "TTS shutdown failed")
        }
    }

    private companion object {
        /** Upper bound on how long to wait for the chime so it can never hang speech. */
        const val CHIME_TIMEOUT_MS = 5_000L
    }
}
