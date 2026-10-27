package com.munawar.ai

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Speaks text. Uses ElevenLabs when enabled and configured (falls back to the phone's TTS if ElevenLabs fails),
 * otherwise Android TextToSpeech.
 */
class Speaker(private val ctx: Context, private val prefs: Prefs) {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var ready = false
    private var initFailed = false
    private var pendingRun: (() -> Unit)? = null
    private var currentId: String? = null
    private var onDone: (() -> Unit)? = null
    private var token = 0
    private var job: Job? = null
    private var mp: MediaPlayer? = null

    @Volatile var speaking = false
        private set
    @Volatile var lastError: String? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) {}
        override fun onDone(id: String?) { finish(id) }
        @Deprecated("Deprecated in Java")
        override fun onError(id: String?) { finish(id) }
    }

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            initFailed = !ready
            if (ready) tts?.setOnUtteranceProgressListener(listener)
            pendingRun?.invoke(); pendingRun = null
        }
    }

    private fun finish(id: String?) {
        if (id != null && id == currentId) {
            speaking = false
            val d = onDone
            onDone = null; currentId = null
            main.post { d?.invoke() }
        }
    }

    fun speak(text: String, done: (() -> Unit)? = null) {
        stop()
        val clean = clean(text)
        if (clean.isBlank()) { done?.invoke(); return }
        speaking = true
        val my = ++token
        val key = prefs.secret("eleven")
        if (prefs.elevenEnabled && key != null && prefs.elevenVoiceId.isNotBlank()) {
            job = scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) { ElevenLabs.tts(key, prefs.elevenVoiceId, prefs.elevenModel, clean) }
                    if (my != token) return@launch
                    lastError = null
                    playMp3(bytes, my, done)
                } catch (e: Exception) {
                    if (my != token) return@launch
                    lastError = e.message
                    speakTts(clean, my, done)
                }
            }
        } else speakTts(clean, my, done)
    }

    private fun playMp3(bytes: ByteArray, my: Int, done: (() -> Unit)?) {
        try {
            val f = File(ctx.cacheDir, "tts.mp3")
            f.writeBytes(bytes)
            val m = MediaPlayer()
            mp = m
            m.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            m.setDataSource(f.absolutePath)
            m.setOnCompletionListener { finishPlay(my, done) }
            m.setOnErrorListener { _, _, _ -> finishPlay(my, done); true }
            m.prepare()
            m.start()
        } catch (e: Exception) {
            lastError = e.message
            finishPlay(my, done)
        }
    }

    private fun finishPlay(my: Int, done: (() -> Unit)?) {
        if (my != token) return
        releaseMp()
        speaking = false
        done?.invoke()
    }

    private fun releaseMp() {
        mp?.let { try { it.stop() } catch (e: Exception) {}; try { it.release() } catch (e: Exception) {} }
        mp = null
    }

    private fun speakTts(clean: String, my: Int, done: (() -> Unit)?) {
        val run: () -> Unit = run@{
            if (my != token) return@run
            val t = tts
            if (!ready || t == null) {
                lastError = "Text-to-speech engine not available"
                speaking = false; done?.invoke(); return@run
            }
            applyVoice(t, clean)
            t.setPitch(prefs.pitch)
            t.setSpeechRate(prefs.rate)
            val id = "u$my"
            currentId = id; onDone = done
            t.speak(clean.take(3000), TextToSpeech.QUEUE_FLUSH, null, id)
        }
        if (ready || initFailed) run() else pendingRun = run
    }

    fun stop() {
        token++
        job?.cancel(); job = null
        onDone = null; currentId = null; speaking = false; pendingRun = null
        releaseMp()
        try { tts?.stop() } catch (e: Exception) {}
    }

    fun shutdown() {
        stop(); scope.cancel(); tts?.shutdown(); tts = null
    }

    private fun applyVoice(t: TextToSpeech, text: String) {
        val hindi = text.any { it in '\u0900'..'\u097F' }
        val loc = if (hindi) Locale("hi", "IN") else Locale("en", "IN")
        val all = try { t.voices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }
        val same = all.filter { it.locale.language == loc.language }
        val v = same.firstOrNull { it.name == prefs.voiceName }
            ?: same.firstOrNull { it.name.contains("female", true) && !it.isNetworkConnectionRequired }
            ?: same.firstOrNull { it.name.contains("female", true) }
        if (v != null) t.setVoice(v) else t.setLanguage(loc)
    }

    fun voiceNames(): List<String> = try {
        tts?.voices?.filter { it.locale.language == "hi" || it.locale.language == "en" }?.map { it.name }?.sorted() ?: emptyList()
    } catch (e: Exception) { emptyList() }

    private fun clean(s: String): String = s
        .replace(Regex("```[\\s\\S]*?```"), " code block. ")
        .replace(Regex("https?://\\S+"), " link ")
        .replace(Regex("[*_#`>~|]"), "")
        .replace(Regex("[\\p{So}\\p{Cs}\\uFE0F]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

/** Speech-to-text (one session at a time). */
class Ears(private val ctx: Context) {
    interface Cb {
        fun onReady() {}
        fun onLevel(l: Float) {}
        fun onPartial(t: String) {}
        fun onFinal(t: String)
        fun onError(code: Int)
    }

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null

    @Volatile var active = false
        private set

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(lang: String, cb: Cb) {
        if (active) return
        active = true
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { if (rec === r) cb.onReady() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) { if (rec === r) cb.onLevel(((rms + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { if (rec === r) { release(); cb.onError(error) } }
            override fun onResults(b: Bundle?) {
                if (rec === r) {
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    release(); cb.onFinal(t)
                }
            }
            override fun onPartialResults(b: Bundle?) {
                if (rec === r) b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { cb.onPartial(it) }
            }
            override fun onEvent(type: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        }
        r.startListening(i)
    }

    fun stop() = release()

    private fun release() {
        val r = rec
        rec = null; active = false
        if (r != null) main.post {
            try { r.cancel() } catch (e: Exception) {}
            try { r.destroy() } catch (e: Exception) {}
        }
    }
}
