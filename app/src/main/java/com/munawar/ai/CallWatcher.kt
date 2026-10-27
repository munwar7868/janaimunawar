package com.munawar.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Hears the phone ring (needs the Phone permission) so Munawar can tell you who is calling without being asked. */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        try { Core.init(c) } catch (e: Exception) { return }
        val state = i.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val num = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        CallWatcher.onState(c.applicationContext, state, num)
    }
}

object CallWatcher {
    private var ringing = false
    private var answered = false
    private var ringingNumber: String? = null
    private var announcedAt = 0L

    fun onState(ctx: Context, state: String, number: String?) {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (number != null) ringingNumber = number
                if (!ringing) { ringing = true; answered = false }
                val now = System.currentTimeMillis()
                if (now - announcedAt > 15_000L) {
                    announcedAt = now
                    // some phones send the first broadcast without the number: wait a moment for the second one
                    Core.scope.launch { delay(if (number == null) 1200L else 0L); Buddy.onIncomingCall(ctx, ringingNumber) }
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> { if (ringing) answered = true }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (ringing && !answered) Buddy.onMissedCall(ctx, ringingNumber)
                ringing = false; answered = false; ringingNumber = null
            }
        }
    }
}
