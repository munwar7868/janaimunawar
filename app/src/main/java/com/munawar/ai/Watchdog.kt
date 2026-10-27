package com.munawar.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Every 15 minutes: if Android killed the background listener, start it again. */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try { Core.init(c) } catch (e: Exception) { return }
        Watchdog.check(c)
        Watchdog.schedule(c)
    }
}

object Watchdog {
    fun schedule(ctx: Context) {
        try {
            if (!Core.prefs.keepAlive) return
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(ctx, 7777, Intent(ctx, WatchdogReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 15 * 60_000L, pi)
        } catch (e: Exception) {}
    }

    fun check(ctx: Context) {
        try {
            if (Core.prefs.keepAlive && Core.prefs.wakeEnabled && AssistantService.inst == null) AssistantService.send(ctx, AssistantService.A_WAKE_ON)
        } catch (e: Exception) {}
    }
}
