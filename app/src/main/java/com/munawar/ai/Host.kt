package com.munawar.ai

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope

/**
 * Where commands run. ActivityHost = app on screen (real dialogs). BackgroundHost = app minimised/closed:
 * confirmations use an overlay card or a notification, and apps are opened from the background only when Android allows.
 */
abstract class Host(base: Context) : ContextWrapper(base) {
    val prefs: Prefs get() = Core.prefs
    val notes: NotesStore get() = Core.notes
    val scope: CoroutineScope get() = Core.scope

    /** Set when an app launch had to be replaced by a notification. */
    var launchNote: String? = null

    abstract fun toast(msg: String)
    abstract fun requestPerm(perm: String, cb: (Boolean) -> Unit)
    abstract fun countdown(title: String, detail: String, onGo: () -> Unit)
    abstract fun generateImage(prompt: String)

    open fun requestAnyPerm(perms: Array<String>, cb: (Boolean) -> Unit) {
        if (perms.any { Perms.has(this, it) }) cb(true)
        else { toast("Allow ${perms[0].substringAfterLast('.').lowercase()} in Munawar → Control Center."); cb(false) }
    }
}

class ActivityHost(private val act: MainActivity) : Host(act) {
    override fun toast(msg: String) = act.toast(msg)
    override fun requestPerm(perm: String, cb: (Boolean) -> Unit) = act.requestPerm(perm, cb)
    override fun requestAnyPerm(perms: Array<String>, cb: (Boolean) -> Unit) = act.requestAnyPerm(perms, cb)
    override fun generateImage(prompt: String) = act.generateImage(prompt)

    /** Shows what is about to happen with a Cancel button; goes ahead after the countdown (Settings → cancel window). */
    override fun countdown(title: String, detail: String, onGo: () -> Unit) {
        val secs = prefs.confirmSeconds
        if (secs <= 0) { onGo(); return }
        val h = Handler(Looper.getMainLooper())
        var left = secs
        var done = false
        val dlg = AlertDialog.Builder(act).setTitle(title).setCancelable(false)
            .setMessage("$detail\n\nGoing ahead in ${left}s…")
            .setPositiveButton("Do it now") { _, _ -> if (!done) { done = true; onGo() } }
            .setNegativeButton("Cancel") { _, _ -> done = true }
            .create()
        dlg.show()
        val tick = object : Runnable {
            override fun run() {
                if (done || !dlg.isShowing) return
                left--
                if (left <= 0) { done = true; dlg.dismiss(); onGo() }
                else { dlg.setMessage("$detail\n\nGoing ahead in ${left}s…"); h.postDelayed(this, 1000) }
            }
        }
        h.postDelayed(tick, 1000)
    }
}

class BackgroundHost(base: Context) : Host(base) {
    private val main = Handler(Looper.getMainLooper())

    override fun toast(msg: String) { main.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } }

    override fun requestPerm(perm: String, cb: (Boolean) -> Unit) {
        if (Perms.has(this, perm)) cb(true)
        else { toast("Allow ${perm.substringAfterLast('.').lowercase()} in Munawar → Control Center first."); cb(false) }
    }

    override fun generateImage(prompt: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra("genimage", prompt))
    }

    private fun channel(id: String, name: String, imp: Int) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(id, name, imp))
    }

    /** Android blocks starting apps from the background unless 'Display over other apps' is allowed; otherwise we post a tap-to-open notification. */
    override fun startActivity(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Perms.overlay(this)) { super.startActivity(intent); return }
        channel("munawar_open", "Open requests", NotificationManager.IMPORTANCE_HIGH)
        val pi = PendingIntent.getActivity(this, (System.nanoTime() and 0xffff).toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            78, Notification.Builder(this, "munawar_open").setContentTitle("Munawar: tap to continue")
                .setContentText("Android doesn't let apps open other apps from the background.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setAutoCancel(true).setContentIntent(pi).build()
        )
        launchNote = "Android blocks opening apps in the background, so I posted a notification: tap it. (Allow 'Display over other apps' in Control Center and I can do it automatically.)"
    }

    override fun countdown(title: String, detail: String, onGo: () -> Unit) {
        val secs = prefs.confirmSeconds
        if (secs <= 0) { onGo(); return }
        if (Perms.overlay(this)) { Overlays.countdown(this, title, detail, secs, onGo); return }
        if (Build.VERSION.SDK_INT >= 33 && !Perms.has(this, android.Manifest.permission.POST_NOTIFICATIONS)) {
            toast("I can't show a Cancel option, so I did NOT do it. Allow notifications or 'Display over other apps' in Control Center.")
            return
        }
        val id = (System.nanoTime() and 0x7fffffff).toInt()
        channel("munawar_confirm", "Confirm actions", NotificationManager.IMPORTANCE_HIGH)
        val cancel = PendingIntent.getBroadcast(this, id, Intent(this, CancelReceiver::class.java).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, Notification.Builder(this, "munawar_confirm").setContentTitle(title).setContentText("$detail • going ahead in ${secs}s")
            .setStyle(Notification.BigTextStyle().bigText("$detail\n\nGoing ahead in ${secs}s unless you cancel."))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Cancel", cancel).build())
            .build())
        main.postDelayed({
            if (!CancelReceiver.cancelled.remove(id)) { nm.cancel(id); onGo() }
        }, secs * 1000L)
    }
}

class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        cancelled.add(id)
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
    }

    companion object { val cancelled: MutableSet<Int> = java.util.Collections.synchronizedSet(HashSet<Int>()) }
}

object Overlays {
    /** Small floating card with the details and a Cancel button; works over any app (needs 'Display over other apps'). */
    fun countdown(ctx: Context, title: String, detail: String, secs: Int, onGo: () -> Unit) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val h = Handler(Looper.getMainLooper())
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundBg(Color.parseColor("#F2161026"), ctx.dp(20).toFloat(), Theme.PURPLE)
            setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
        }
        box.addView(TextView(ctx).apply { text = title; textSize = 17f; setTextColor(Theme.CYAN); setTypeface(typeface, android.graphics.Typeface.BOLD) })
        box.addView(TextView(ctx).apply { text = detail; textSize = 14f; setTextColor(Theme.TEXT); setPadding(0, ctx.dp(6), 0, ctx.dp(6)) })
        val c = TextView(ctx).apply { text = "Going ahead in ${secs}s"; textSize = 13f; setTextColor(Theme.MUTED) }
        box.addView(c)
        var done = false
        fun close() { done = true; try { wm.removeView(box) } catch (e: Exception) {} }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(ctx.button("Cancel", Theme.RED) { if (!done) close() }, ctx.lp(0, WRAP, 1f, 3))
        row.addView(ctx.button("Do it now", Theme.PURPLE) { if (!done) { close(); onGo() } }, ctx.lp(0, WRAP, 1f, 3))
        box.addView(row)
        val lp = WindowManager.LayoutParams(
            ctx.dp(320), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = ctx.dp(70); alpha = 0.79f }
        try { wm.addView(box, lp) } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't show the cancel card, so I did NOT do it.", Toast.LENGTH_LONG).show(); return
        }
        var left = secs
        val tick = object : Runnable {
            override fun run() {
                if (done) return
                left--
                if (left <= 0) { close(); onGo() } else { c.text = "Going ahead in ${left}s"; h.postDelayed(this, 1000) }
            }
        }
        h.postDelayed(tick, 1000)
    }
}
