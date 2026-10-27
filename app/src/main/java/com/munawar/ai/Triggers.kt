package com.munawar.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** type: time | charging | battery | headphones | screen | wifi | app | appclose.  action: speak | run | talk. */
data class Trigger(val id: Long, val type: String, val arg: String, val action: String, val payload: String, val enabled: Boolean)

/**
 * Automation & triggers. Time triggers and "charging" triggers work even when Munawar is closed (alarm / boot / power
 * broadcasts). Screen, headphones, battery %, Wi-Fi and app-launch triggers work while Munawar's background service or
 * the app is running (app-launch also needs Accessibility).
 */
object Triggers {
    const val ACTION_TIME = "com.munawar.ai.TRIGGER_TIME"

    fun all(): List<Trigger> = try {
        val a = JSONArray(Core.prefs.triggersJson)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Trigger(o.getLong("id"), o.getString("type"), o.optString("arg"), o.getString("action"), o.optString("payload"), o.optBoolean("on", true))
        }
    } catch (e: Exception) { emptyList() }

    private fun save(list: List<Trigger>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("type", it.type).put("arg", it.arg).put("action", it.action).put("payload", it.payload).put("on", it.enabled)) }
        Core.prefs.triggersJson = a.toString()
    }

    fun add(ctx: Context, t: Trigger) { save(all() + t); scheduleTimes(ctx) }
    fun remove(ctx: Context, id: Long) { cancelAlarm(ctx, id); save(all().filter { it.id != id }); scheduleTimes(ctx) }
    fun toggle(ctx: Context, id: Long) { save(all().map { if (it.id == id) it.copy(enabled = !it.enabled) else it }); scheduleTimes(ctx) }

    fun describe(t: Trigger): String {
        val whenTxt = when (t.type) {
            "time" -> "Every day at ${t.arg}"
            "charging" -> if (t.arg == "off") "When charger is unplugged" else "When charger is plugged in"
            "battery" -> "When battery drops to ${t.arg}%"
            "headphones" -> if (t.arg == "off") "When headphones are unplugged" else "When headphones are plugged in"
            "screen" -> if (t.arg == "off") "When screen turns off" else "When screen turns on"
            "wifi" -> if (t.arg == "off") "When Wi-Fi disconnects" else "When Wi-Fi connects"
            "app" -> "When you open ${t.arg}"
            "appclose" -> "When you leave ${t.arg}"
            else -> t.type
        }
        val doTxt = when (t.action) {
            "speak" -> "speak “${t.payload}”"
            "talk" -> "start a conversation"
            else -> "run “${t.payload}”"
        }
        return "$whenTxt → $doTxt"
    }

    // ---------- time triggers ----------
    private fun pi(ctx: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(ctx, (id % 1_000_000L).toInt(), Intent(ctx, TriggerAlarmReceiver::class.java).setAction(ACTION_TIME).putExtra("id", id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun cancelAlarm(ctx: Context, id: Long) {
        try { (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(ctx, id)) } catch (e: Exception) {}
    }

    private fun nextMillis(hhmm: String): Long? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(hhmm.trim()) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        if (h > 23 || mi > 59) return null
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun scheduleTimes(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (t in all()) {
            if (t.type != "time") continue
            if (!t.enabled) { cancelAlarm(ctx, t.id); continue }
            val at = nextMillis(t.arg) ?: continue
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx, t.id)) } catch (e: Exception) {}
        }
    }

    // ---------- firing ----------
    private val lastFire = HashMap<Long, Long>()

    fun fire(type: String, arg: String) {
        val now = System.currentTimeMillis()
        for (t in all()) {
            if (!t.enabled || t.type != type) continue
            val ok = when (type) {
                "app", "appclose" -> arg.contains(t.arg, true) || appLabel(arg).contains(t.arg, true)
                "battery" -> true
                else -> t.arg.isEmpty() || t.arg == arg
            }
            if (!ok) continue
            if (now - (lastFire[t.id] ?: 0L) < 4000L) continue // debounce
            lastFire[t.id] = now
            execute(t)
        }
    }

    fun fireById(id: Long) { all().firstOrNull { it.id == id && it.enabled }?.let { execute(it) } }

    private fun appLabel(pkg: String): String = try {
        val pm = Core.appCtx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun execute(t: Trigger) {
        Core.scope.launch {
            try {
                when (t.action) {
                    "speak" -> Core.speaker.speak(t.payload)
                    "talk" -> AssistantService.send(Core.appCtx, AssistantService.A_SESSION)
                    else -> Commands.run(Hosts.get(), t.payload, false)
                }
            } catch (e: Exception) {}
        }
    }

    // ---------- live triggers (need the process alive) ----------
    private var rx: BroadcastReceiver? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private var batteryFired = false
    private var lastApp = ""

    fun startLive(ctx: Context) {
        if (rx != null) return
        val app = ctx.applicationContext
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_POWER_CONNECTED -> fire("charging", "on")
                    Intent.ACTION_POWER_DISCONNECTED -> fire("charging", "off")
                    Intent.ACTION_SCREEN_ON -> fire("screen", "on")
                    Intent.ACTION_SCREEN_OFF -> fire("screen", "off")
                    AudioManager.ACTION_HEADSET_PLUG -> fire("headphones", if (i.getIntExtra("state", 0) == 1) "on" else "off")
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val lvl = i.getIntExtra("level", -1); val scale = i.getIntExtra("scale", 100)
                        val plugged = i.getIntExtra("plugged", 0) != 0
                        if (lvl < 0 || scale <= 0) return
                        val pct = lvl * 100 / scale
                        for (t in all()) {
                            if (!t.enabled || t.type != "battery") continue
                            val limit = t.arg.toIntOrNull() ?: continue
                            if (pct <= limit && !plugged && !batteryFired) { batteryFired = true; execute(t) }
                            else if (pct > limit + 2 || plugged) batteryFired = false
                        }
                    }
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(AudioManager.ACTION_HEADSET_PLUG); addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED) else app.registerReceiver(r, f)
            rx = r
        } catch (e: Exception) {}
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { fire("wifi", "on") }
                override fun onLost(network: Network) { fire("wifi", "off") }
            }
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb)
            netCb = cb
        } catch (e: Exception) {}
    }

    fun stopLive(ctx: Context) {
        val app = ctx.applicationContext
        try { rx?.let { app.unregisterReceiver(it) } } catch (e: Exception) {}
        rx = null
        try { netCb?.let { (app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) } } catch (e: Exception) {}
        netCb = null
    }

    /** Called by the accessibility service when another app comes to the front. */
    fun onForegroundApp(pkg: String) {
        if (pkg == lastApp) return
        if (pkg == Core.appCtx.packageName || pkg.contains("systemui") || pkg.contains("inputmethod") || pkg.contains("keyboard")) return
        val old = lastApp
        lastApp = pkg
        if (old.isNotEmpty()) fire("appclose", old)
        fire("app", pkg)
    }
}

/** Alarm for time triggers (not exported: only this app can fire it). */
class TriggerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Core.init(c)
        val id = i.getLongExtra("id", -1L)
        if (id >= 0) Triggers.fireById(id)
        Triggers.scheduleTimes(c) // schedule tomorrow
    }
}

/** Boot + charger plug/unplug (these still work when the app is closed). */
class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Core.init(c)
        when (i.action) {
            Intent.ACTION_BOOT_COMPLETED -> Triggers.scheduleTimes(c)
            Intent.ACTION_POWER_CONNECTED -> Triggers.fire("charging", "on")
            Intent.ACTION_POWER_DISCONNECTED -> Triggers.fire("charging", "off")
        }
    }
}
