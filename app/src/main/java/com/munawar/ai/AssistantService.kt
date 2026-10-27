package com.munawar.ai

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Shared state the UI (home screen, orbs, floating bubble) reads from the background assistant. */
object AssistantBus {
    @Volatile var state = OrbView.S.IDLE
    @Volatile var level = 0f
    @Volatile var status = "Tap the orb to talk"
    @Volatile var sub = ""
    @Volatile var sessionOn = false
    @Volatile var muted = false
    @Volatile var standby = false
    @Volatile var mood = 0
    @Volatile var moodAt = 0L
    @Volatile var startMs = 0L
    val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    private var lastLevelPost = 0L

    /** Level updates arrive ~20x/second; only forward ~20 fps to keep the UI smooth. */
    fun postLevel(l: Float) {
        level = l
        val n = android.os.SystemClock.uptimeMillis()
        if (n - lastLevelPost < 45) return
        lastLevelPost = n
        post()
    }

    fun post() {
        main.post {
            EdgeLight.update(state, level)
            for (l in listeners) l()
        }
    }
}

/**
 * Foreground microphone service. It owns the Gemini Live session (or the classic fallback loop) and the "Hey Munawar"
 * wake word, so everything keeps running when the app is minimised or closed. It never speaks anything except Gemini's replies.
 */
class AssistantService : Service() {
    private val h = Handler(Looper.getMainLooper())
    internal var wake: WakeEngine? = null
    private var live: LiveSession? = null
    private var ears: Ears? = null
    private var liveMode = false
    private var muted = false
    private var gen = 0
    private var silence = 0
    private var errRetry = 0
    private var restartCount = 0
    private var job: Job? = null
    private var wl: PowerManager.WakeLock? = null
    private val userBuf = StringBuilder()
    private val modelBuf = StringBuilder()
    private var lastWasModel = false
    private var cmdDone = false
    private val prefs get() = Core.prefs

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); Core.init(this); inst = this; Watchdog.schedule(this); try { Triggers.startLive(this) } catch (e: Exception) {} }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Core.init(this)
        if (!Perms.has(this, Manifest.permission.RECORD_AUDIO)) { stopSelf(); return START_NOT_STICKY }
        try { goForeground() } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        running = true
        when (intent?.action) {
            A_SESSION -> startSession()
            A_STOP_SESSION -> stopSession("Stopped")
            A_VISION -> { goForegroundQuiet(); startVision() }
            A_WAKE_ON -> { prefs.wakeEnabled = true; startWake() }
            A_WAKE_OFF -> { prefs.wakeEnabled = false; stopWake() }
            A_MUTE -> toggleMute()
            A_INTERRUPT -> interrupt()
            A_QUIT -> { prefs.wakeEnabled = false; stopWake(); stopSession("Stopped"); stopSelf(); return START_NOT_STICKY }
            else -> if (prefs.wakeEnabled) startWake() // restarted by the system
        }
        if (!AssistantBus.sessionOn && !prefs.wakeEnabled) { stopSelf(); return START_NOT_STICKY }
        refreshNotif()
        return START_STICKY
    }

    // ================= notification =================
    private fun pi(code: Int, action: String) =
        PendingIntent.getService(this, code, Intent(this, AssistantService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    private fun notif(text: String): Notification {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("munawar_service", "Munawar is running", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, "munawar_service")
            .setContentTitle(if (AssistantBus.sessionOn) "Munawar AI • in conversation" else "Munawar AI • listening for “Hey Munawar”")
            .setContentText(text).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).setContentIntent(open)
        fun act(label: String, code: Int, action: String) =
            b.addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), label, pi(code, action)).build())
        if (AssistantBus.sessionOn) {
            act(if (muted) "Unmute" else "Mute", 1, A_MUTE)
            act("Stop", 2, A_STOP_SESSION)
        }
        act("Stop all", 3, A_QUIT)
        return b.build()
    }

    private fun refreshNotif() {
        val t = if (AssistantBus.sessionOn) AssistantBus.status else "Microphone is on. Tap “Stop all” to turn it off."
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, notif(t))
    }

    // ================= greeting / proactive talk =================
    private var greeted = false
    @Volatile private var lastActivity = System.currentTimeMillis()
    private var captureGreet = false
    private val greetBuf = StringBuilder()
    private var chatJob: Job? = null

    /** Sends a hidden instruction from the app to the live model (calls, messages, routines...). False when there is no live conversation. */
    fun hiddenTurn(text: String): Boolean {
        val l = live ?: return false
        if (!AssistantBus.sessionOn || !liveMode) return false
        l.sendText(text)
        lastActivity = System.currentTimeMillis()
        return true
    }

    private fun startProactive() {
        chatJob?.cancel()
        chatJob = Core.scope.launch {
            while (isActive && AssistantBus.sessionOn) {
                delay(60_000L)
                val every = prefs.chatEveryMin
                if (every <= 0 || muted || live == null || AssistantBus.state != OrbView.S.LISTENING) continue
                val jitter = 0.8 + Math.random() * 0.5
                if (System.currentTimeMillis() - lastActivity > every * 60_000L * jitter) {
                    lastActivity = System.currentTimeMillis()
                    live?.sendText(Buddy.chatPrompt(this@AssistantService))
                }
            }
        }
    }

    private fun greetOnce() {
        if (greeted || !prefs.autoGreet) return
        greeted = true
        captureGreet = true; greetBuf.setLength(0)
        live?.sendText(Buddy.greetPrompt(this))
    }

    // ================= senses (screen / camera frames to Gemini) =================
    private var visionJob: Job? = null

    private fun goForeground() {
        val n = notif("Starting…")
        if (Build.VERSION.SDK_INT >= 30) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (Perms.has(this, Manifest.permission.CAMERA)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            try { startForeground(ID, n, type) } catch (e: Exception) { startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) }
        } else startForeground(ID, n)
    }

    private fun goForegroundQuiet() { try { goForeground() } catch (e: Exception) {} }

    private fun startVision() {
        stopVision()
        val mode = prefs.visionMode
        if (mode == "off" || !AssistantBus.sessionOn || !liveMode) return
        if (mode == "screen") {
            visionJob = Core.scope.launch {
                while (isActive && AssistantBus.sessionOn) {
                    val f = Vision.screenJpegIfChanged()
                    if (f != null) live?.sendFrame(f)
                    delay(1500)
                }
            }
        } else {
            val ok = Vision.startCamera(this, mode == "camera_front") { f -> live?.sendFrame(f) }
            if (!ok) Hosts.get().toast("Camera couldn't start (allow Camera and keep Munawar open once)")
        }
    }

    private fun stopVision() { visionJob?.cancel(); visionJob = null; Vision.stopCamera() }

    // ================= wake word =================
    private fun startWake() {
        if (wake == null) {
            wake = WakeEngine(this, prefs, object : WakeEngine.Cb {
                override fun onWake(cmd: String) {
                    Core.scope.launch {
                        if (cmd.isBlank()) startSession()
                        else {
                            val r = try { Commands.run(Hosts.get(), cmd, true) } catch (e: Exception) { null }
                            if (r == null) startSession()
                            else { AssistantBus.sub = "⚙ " + r.message; AssistantBus.post(); h.postDelayed({ applyWakePause() }, 1500) }
                        }
                    }
                }
                override fun onLevel(l: Float) { if (!AssistantBus.sessionOn) AssistantBus.postLevel(l) }
                override fun onActive(active: Boolean) {
                    if (AssistantBus.sessionOn) return
                    AssistantBus.state = if (active) OrbView.S.LISTENING else OrbView.S.IDLE
                    if (!active) AssistantBus.level = 0f
                    AssistantBus.post()
                }
                override fun onFatal(msg: String) { Hosts.get().toast(msg); prefs.wakeEnabled = false; stopWake(); refreshNotif() }
            })
        }
        wake?.start()
        AssistantBus.standby = true
        applyWakePause()
        AssistantBus.post()
        wakeOn = true
    }

    private fun stopWake() {
        wake?.stop(); wake = null
        AssistantBus.standby = false
        wakeOn = false
        if (!AssistantBus.sessionOn) { AssistantBus.state = OrbView.S.IDLE; AssistantBus.level = 0f }
        AssistantBus.post()
    }

    internal fun applyWakePause() { wake?.setPaused(AssistantBus.sessionOn || uiPaused) }

    // ================= session =================
    private fun status(s: String) { AssistantBus.status = s; AssistantBus.post(); refreshNotif() }
    private fun state(s: OrbView.S, t: String) { AssistantBus.state = s; status(t) }

    private fun startSession() {
        if (AssistantBus.sessionOn) return
        if (!prefs.hasApiKey()) { status("⚠ Add your Gemini API key (☰ → Settings)"); return }
        AssistantBus.sessionOn = true
        muted = false; AssistantBus.muted = false
        gen++; silence = 0; errRetry = 0; restartCount = 0
        AssistantBus.startMs = System.currentTimeMillis()
        AssistantBus.sub = ""
        userBuf.clear(); modelBuf.clear(); lastWasModel = false
        try {
            wl = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "munawar:session")
                .apply { setReferenceCounted(false); acquire(24 * 60 * 60 * 1000L) }
        } catch (e: Exception) {}
        applyWakePause()
        EdgeLight.show(this)
        startLive()
    }

    private fun stopSession(reason: String) {
        if (!AssistantBus.sessionOn) return
        AssistantBus.sessionOn = false
        gen++
        job?.cancel(); ears?.stop(); Core.speaker.stop()
        stopVision()
        chatJob?.cancel(); chatJob = null
        greeted = false; captureGreet = false
        live?.stop(); live = null; liveMode = false
        h.removeCallbacksAndMessages(null)
        try { wl?.release() } catch (e: Exception) {}
        wl = null
        EdgeLight.hide()
        AssistantBus.state = OrbView.S.IDLE; AssistantBus.level = 0f; AssistantBus.muted = false
        status(reason)
        applyWakePause()
        if (!prefs.wakeEnabled) stopSelf()
    }

    // ---------- Live ----------
    private fun render() { AssistantBus.sub = "You: $userBuf\nMunawar: $modelBuf"; AssistantBus.post() }

    private fun startLive() {
        val key = prefs.apiKey() ?: return
        liveMode = true
        state(OrbView.S.THINKING, "Connecting to Gemini…")
        val sys = Persona.build(prefs, Core.memory.all().map { it.text }, false, true, true)
        val s = LiveSession(this, key, prefs.liveModel, prefs.liveVoice, sys, object : LiveSession.Cb {
            override fun onConnected() { if (AssistantBus.sessionOn && liveMode) { restartCount = 0; state(OrbView.S.LISTENING, "Listening • just talk (you can minimise the app)"); startVision(); greetOnce(); startProactive() } }
            override fun onStatus(s: String) { if (AssistantBus.sessionOn) status(s) }
            override fun onUserText(t: String) {
                if (!AssistantBus.sessionOn) return
                lastActivity = System.currentTimeMillis()
                if (lastWasModel) { userBuf.clear(); modelBuf.clear(); lastWasModel = false; cmdDone = false }
                userBuf.append(t); render()
            }
            override fun onModelText(t: String) {
                if (!AssistantBus.sessionOn) return
                lastWasModel = true; modelBuf.append(t)
                lastActivity = System.currentTimeMillis()
                if (captureGreet) greetBuf.append(t)
                val m = Emotion.detect(t)
                if (m != 0) { AssistantBus.mood = m; AssistantBus.moodAt = System.currentTimeMillis() }
                render()
            }
            override fun onSpeaking(on: Boolean) {
                if (AssistantBus.sessionOn && liveMode && !muted) state(if (on) OrbView.S.SPEAKING else OrbView.S.LISTENING, if (on) "Speaking…" else "Listening…")
            }
            override fun onLevel(l: Float) { if (AssistantBus.sessionOn) AssistantBus.postLevel(l) }
            override fun onCommand(command: String, done: (String) -> Unit) {
                if (!AssistantBus.sessionOn) { done("Stopped"); return }
                status("⚙ $command")
                Core.scope.launch {
                    val r = try { Commands.run(Hosts.get(), command, false) } catch (e: Exception) { null }
                    val msg = if (r == null) "Not a recognised phone command." else (if (r.ok) "" else "FAILED: ") + r.message
                    AssistantBus.sub = AssistantBus.sub + "\n⚙ " + msg; AssistantBus.post()
                    done(msg)
                }
            }
            override fun onTurnComplete() {
                if (captureGreet && greetBuf.isNotEmpty()) { Buddy.rememberGreeting(greetBuf.toString()); captureGreet = false }
                if (live?.toolsActive != false) return
                val u = userBuf.toString().trim()
                if (u.isEmpty() || cmdDone) return
                cmdDone = true
                Core.scope.launch {
                    val r = try { Commands.run(Hosts.get(), u, false) } catch (e: Exception) { null }
                    if (r != null) { AssistantBus.sub = AssistantBus.sub + "\n⚙ " + r.message; AssistantBus.post() }
                }
            }
            override fun onClosed(reason: String, wasConnected: Boolean) {
                if (!AssistantBus.sessionOn) return
                live = null
                if (!wasConnected && liveMode && restartCount == 0) {
                    AssistantBus.sub = "⚠ Gemini Live unavailable: $reason\nUsing classic mode (Settings → Gemini Live → Load Live models)."
                    liveMode = false
                    startClassic()
                } else {
                    // never give up: reconnect with a growing delay
                    restartCount++
                    status("Reconnecting… ($reason)")
                    val myGen = gen
                    h.postDelayed({ if (AssistantBus.sessionOn && myGen == gen) { if (liveMode) startLive() else startClassic() } }, minOf(30000L, 2000L * restartCount))
                }
            }
        })
        s.muted = muted
        live = s
        s.start()
    }

    // ---------- Classic (continuous fallback; speaks only Gemini's replies) ----------
    private fun startClassic() {
        liveMode = false
        if (ears == null) ears = Ears(this)
        listen()
    }

    private fun listen() {
        val e = ears ?: return
        if (!AssistantBus.sessionOn || muted || liveMode || e.active || Core.speaker.speaking) return
        state(OrbView.S.LISTENING, "Listening… (classic mode)")
        e.start(prefs.sttLocale(), object : Ears.Cb {
            override fun onLevel(l: Float) { AssistantBus.postLevel(l) }
            override fun onPartial(t: String) { AssistantBus.sub = "You: $t"; AssistantBus.post() }
            override fun onFinal(t: String) { AssistantBus.level = 0f; handleUser(t) }
            override fun onError(code: Int) { AssistantBus.level = 0f; handleError(code) }
        })
    }

    private fun handleUser(t: String) {
        if (!AssistantBus.sessionOn) return
        if (t.isBlank()) { h.postDelayed({ listen() }, 250); return }
        silence = 0; errRetry = 0
        AssistantBus.sub = "You: $t"
        state(OrbView.S.THINKING, "Thinking…")
        val g = gen
        job = Core.scope.launch {
            var failed = false
            val reply = try {
                val auto = Commands.run(Hosts.get(), t)
                if (auto != null) auto.message else Core.callEngine.reply(t).text
            } catch (e: Exception) { failed = true; e.message ?: "Unknown error" }
            if (!AssistantBus.sessionOn || g != gen) return@launch
            if (failed) {
                AssistantBus.sub = "⚠ $reply"
                state(OrbView.S.IDLE, "Problem - listening again")
                h.postDelayed({ listen() }, 1500)
            } else {
                AssistantBus.sub = "You: $t\nMunawar: $reply"
                state(OrbView.S.SPEAKING, "Speaking…")
                Core.speaker.speak(reply) { if (AssistantBus.sessionOn && g == gen) listen() }
            }
        }
    }

    private fun handleError(code: Int) {
        if (!AssistantBus.sessionOn) return
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> h.postDelayed({ listen() }, 250)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stopSession("Microphone permission missing")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> h.postDelayed({ listen() }, 800)
            else -> { errRetry++; state(OrbView.S.IDLE, "Reconnecting… ($errRetry)"); h.postDelayed({ listen() }, minOf(10000L, 1000L * errRetry)) }
        }
    }

    private fun toggleMute() {
        if (!AssistantBus.sessionOn) return
        muted = !muted
        AssistantBus.muted = muted
        live?.muted = muted
        if (muted) { ears?.stop(); state(OrbView.S.IDLE, "Muted") }
        else if (liveMode) state(OrbView.S.LISTENING, "Listening…") else listen()
    }

    private fun interrupt() {
        if (!AssistantBus.sessionOn) return
        if (liveMode) { live?.interruptLocal(); if (!muted) state(OrbView.S.LISTENING, "Listening…"); return }
        gen++; job?.cancel(); Core.speaker.stop(); ears?.stop()
        muted = false; AssistantBus.muted = false
        h.postDelayed({ listen() }, 200)
    }

    override fun onDestroy() {
        stopSession("Stopped")
        wake?.stop(); wake = null
        AssistantBus.standby = false
        running = false; wakeOn = false
        if (inst === this) inst = null
        AssistantBus.post()
        super.onDestroy()
    }

    companion object {
        private const val ID = 21
        const val A_SESSION = "com.munawar.ai.SESSION"
        const val A_STOP_SESSION = "com.munawar.ai.STOP_SESSION"
        const val A_WAKE_ON = "com.munawar.ai.WAKE_ON"
        const val A_WAKE_OFF = "com.munawar.ai.WAKE_OFF"
        const val A_MUTE = "com.munawar.ai.MUTE"
        const val A_INTERRUPT = "com.munawar.ai.INTERRUPT"
        const val A_VISION = "com.munawar.ai.VISION"
        const val A_QUIT = "com.munawar.ai.QUIT"

        @Volatile var running = false
        @Volatile var wakeOn = false
        @Volatile var uiPaused = false
        var inst: AssistantService? = null

        fun send(ctx: Context, action: String) {
            try { ctx.startForegroundService(Intent(ctx, AssistantService::class.java).setAction(action)) } catch (e: Exception) {}
        }

        /** Pause the wake word while the system voice-typing dialog uses the microphone. */
        fun uiPause(p: Boolean) { uiPaused = p; inst?.applyWakePause() }
    }
}
