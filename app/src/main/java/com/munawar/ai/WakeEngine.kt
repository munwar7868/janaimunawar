package com.munawar.ai

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Silences the recogniser's start/stop "ding" while the wake word listens. Only the SYSTEM and NOTIFICATION streams are muted
 * all the time; the MEDIA stream is muted for a moment around each restart and only when no music/video is playing.
 * Everything is un-muted again when the wake word stops (and on the next app start, in case the process was killed).
 */
object Hush {
    private val h = Handler(Looper.getMainLooper())
    private var am: AudioManager? = null
    private var sysMuted = false
    private var musicMuted = false
    private val release = Runnable { unmuteMusic() }

    private fun flag(ctx: Context, on: Boolean) = ctx.getSharedPreferences("hush", Context.MODE_PRIVATE).edit().putBoolean("muted", on).apply()

    fun now(ctx: Context, holdMs: Long = 1300) {
        val m = am ?: (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also { am = it }
        if (!sysMuted) {
            sysMuted = true; flag(ctx, true)
            for (st in intArrayOf(AudioManager.STREAM_SYSTEM, AudioManager.STREAM_NOTIFICATION)) try { m.adjustStreamVolume(st, AudioManager.ADJUST_MUTE, 0) } catch (e: Exception) {}
        }
        h.removeCallbacks(release)
        if (!musicMuted && !m.isMusicActive) {
            musicMuted = true
            try { m.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0) } catch (e: Exception) {}
        }
        h.postDelayed(release, holdMs)
    }

    private fun unmuteMusic() {
        if (!musicMuted) return
        musicMuted = false
        try { am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) {}
    }

    /** Give every stream back. */
    fun off(ctx: Context) {
        val m = am ?: (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        am = m
        h.removeCallbacks(release)
        unmuteMusic()
        if (sysMuted) {
            sysMuted = false
            for (st in intArrayOf(AudioManager.STREAM_SYSTEM, AudioManager.STREAM_NOTIFICATION)) try { m.adjustStreamVolume(st, AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) {}
        }
        flag(ctx, false)
    }

    /** Called at app start: if the process died while muted, restore the streams. */
    fun rescue(ctx: Context) {
        if (ctx.getSharedPreferences("hush", Context.MODE_PRIVATE).getBoolean("muted", false)) {
            sysMuted = true; musicMuted = true
            off(ctx)
        }
    }
}

/**
 * "Hey Munawar" detector. Android has no free always-on keyword API, so this restarts SpeechRecognizer in a loop and
 * looks for the name in each result. It uses battery and the recognizer may beep on some phones (see README).
 */
class WakeEngine(private val ctx: Context, private val prefs: Prefs, private val cb: Cb) {
    interface Cb {
        fun onWake(cmd: String)
        fun onLevel(l: Float)
        fun onActive(active: Boolean)
        fun onFatal(msg: String)
    }

    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var paused = false
    private var fails = 0

    @Volatile var on = false
        private set

    private val re = Regex(
        "(?:\\b(?:hey|hay|hi|hello|ok|okay)\\b\\s*|हे\\s*|हेय\\s*)?" +
            "(munawwar|munawar|munavar|munnawar|manawar|munawer|munnavar|मुनव्वर|मुनावर|मुनव्वार|मुनवर|मुनव्वर)"
    )

    fun start() {
        if (on) return
        on = true; paused = false; fails = 0
        loop()
    }

    fun stop() {
        on = false
        Hush.off(ctx)
        h.removeCallbacksAndMessages(null) // FIX: clear first, otherwise the pending destroy() was cancelled and the mic stayed busy
        release()
        cb.onActive(false)
    }

    fun setPaused(p: Boolean) {
        if (!on) return
        paused = p
        h.removeCallbacksAndMessages(null)
        if (p) { release(); Hush.off(ctx); cb.onActive(false) } else loop()
    }

    private fun first(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun loop() {
        if (!on || paused || rec != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            on = false; cb.onFatal("Speech recognition isn't available on this phone."); return
        }
        if (prefs.wakeBeepFix) Hush.now(ctx)
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { if (rec === r) cb.onActive(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { if (rec === r) cb.onLevel(((rms + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { if (prefs.wakeBeepFix) Hush.now(ctx, 900) }
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onError(e: Int) {
                if (rec !== r) return
                if (prefs.wakeBeepFix) Hush.now(ctx, 900)
                release(); fails++
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    on = false; cb.onFatal("Microphone permission is missing."); return
                }
                val delay = when {
                    e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
                    e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
                    fails > 8 -> 5000L
                    else -> 800L
                }
                if (e != SpeechRecognizer.ERROR_NO_MATCH && e != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) cb.onActive(false)
                h.postDelayed({ loop() }, delay)
            }
            override fun onResults(b: Bundle?) {
                if (rec !== r) return
                val text = first(b)
                if (prefs.wakeBeepFix) Hush.now(ctx, 900)
                release(); fails = 0
                val m = if (text != null) re.find(text.lowercase()) else null
                if (m != null && text != null) {
                    paused = true
                    cb.onActive(false)
                    cb.onWake(text.substring(m.range.last + 1).trim())
                } else h.postDelayed({ loop() }, 500)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.sttLocale())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            // longer sessions = fewer restarts (every restart can make the recogniser ding)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        r.startListening(i)
    }

    private fun release() {
        val r = rec
        rec = null
        if (r != null) h.post {
            try { r.cancel() } catch (e: Exception) {}
            try { r.destroy() } catch (e: Exception) {}
        }
    }
}
