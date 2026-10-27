package com.munawar.ai

import android.content.Context
import android.content.SharedPreferences

/** Simple local AI usage / connection counters (no content stored). */
object Stats {
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) { sp = ctx.getSharedPreferences("munawar_stats", Context.MODE_PRIVATE) }

    fun ok(tokens: Int) {
        val p = sp ?: return
        p.edit().putInt("req", p.getInt("req", 0) + 1).putLong("tok", p.getLong("tok", 0) + tokens)
            .putLong("last_ok", System.currentTimeMillis()).apply()
    }

    fun fail(msg: String) {
        val p = sp ?: return
        p.edit().putInt("err", p.getInt("err", 0) + 1).putString("last_err", msg.take(160)).apply()
    }

    fun reset() { sp?.edit()?.clear()?.apply() }

    fun summary(): String {
        val p = sp ?: return "No data"
        val last = p.getLong("last_ok", 0)
        val ago = if (last == 0L) "never" else "${(System.currentTimeMillis() - last) / 60000} min ago"
        val err = p.getString("last_err", null)
        return "Requests: ${p.getInt("req", 0)} • Tokens: ${p.getLong("tok", 0)} • Errors: ${p.getInt("err", 0)}\n" +
            "Last success: $ago" + (if (err != null) "\nLast error: $err" else "")
    }
}
