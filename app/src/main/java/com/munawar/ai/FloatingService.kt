package com.munawar.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Floating orb. Tap: open Munawar and start a call. Long-press: explain the current screen (needs Accessibility). Drag to move. */
class FloatingService : Service() {
    private var wm: WindowManager? = null
    private var orb: OrbView? = null
    private var speaker: Speaker? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (!Perms.overlay(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            val n = notification()
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        addBubble()
        if (!AssistantBus.listeners.contains(busListener)) AssistantBus.listeners.add(busListener)
        busListener()
        running = true
        return START_STICKY
    }

    private fun notification(): Notification {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("munawar_bubble", "Floating orb", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 3, Intent(this, FloatingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "munawar_bubble")
            .setContentTitle("Munawar floating orb is on")
            .setContentText("Tap orb: start/stop Gemini • Long-press: explain screen • Drag to move")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Hide", stop).build())
            .build()
    }

    private fun addBubble() {
        if (orb != null) return
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = manager
        val size = (Prefs(this).floatSize * resources.displayMetrics.density).toInt()
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 360; alpha = 0.79f }
        val v = OrbView(this)
        v.lite = Prefs(this).liteMode
        orb = v

        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        var moved = false; var longFired = false
        val longRun = Runnable { if (!moved) { longFired = true; explain() } }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false; longFired = false
                    h.postDelayed(longRun, 700); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) > 12 || abs(dy) > 12) { moved = true; h.removeCallbacks(longRun) }
                    if (moved) { lp.x = (ox + dx).toInt(); lp.y = (oy + dy).toInt(); try { manager.updateViewLayout(v, lp) } catch (x: Exception) {} }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    h.removeCallbacks(longRun)
                    if (!moved && !longFired && e.action == MotionEvent.ACTION_UP) openApp()
                    true
                }
                else -> false
            }
        }
        try { manager.addView(v, lp) } catch (e: Exception) { stopSelf() }
    }

    /** Tap = start/stop the Gemini conversation right here (no need to open the app). */
    private fun openApp() {
        if (!Perms.has(this, android.Manifest.permission.RECORD_AUDIO)) {
            val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            try { startActivity(i) } catch (e: Exception) {}
            return
        }
        AssistantService.send(this, if (AssistantBus.sessionOn) AssistantService.A_STOP_SESSION else AssistantService.A_SESSION)
    }

    private val busListener: () -> Unit = { orb?.let { it.state = AssistantBus.state; it.level = AssistantBus.level } }

    private fun explain() {
        val o = orb ?: return
        val prefs = Prefs(this)
        o.state = OrbView.S.THINKING
        scope.launch {
            val (ok, msg) = ScreenAI.explain(prefs, "Explain my current screen briefly in 3-4 sentences.")
            Toast.makeText(this@FloatingService, msg.take(500), Toast.LENGTH_LONG).show()
            if (ok && prefs.speak) {
                o.state = OrbView.S.SPEAKING
                val sp = speaker ?: Speaker(this@FloatingService, prefs).also { speaker = it }
                sp.speak(msg) { o.state = OrbView.S.IDLE }
            } else o.state = OrbView.S.IDLE
        }
    }

    override fun onDestroy() {
        try { orb?.let { wm?.removeView(it) } } catch (e: Exception) {}
        orb = null
        AssistantBus.listeners.remove(busListener)
        speaker?.shutdown(); scope.cancel()
        running = false
        super.onDestroy()
    }

    companion object {
        private const val ID = 22
        const val ACTION_STOP = "com.munawar.ai.STOP_BUBBLE"
        @Volatile var running = false
        fun start(ctx: Context) { try { ctx.startForegroundService(Intent(ctx, FloatingService::class.java)) } catch (e: Exception) {} }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, FloatingService::class.java)) }
        fun refresh(ctx: Context) {
            if (!running) return
            stop(ctx)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ start(ctx) }, 400)
        }
    }
}
