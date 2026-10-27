package com.munawar.ai

import org.json.JSONObject
import java.util.Calendar

/**
 * Habit learning (on-device only). Counts which commands you run in which hour of the day, then suggests them
 * around the same time ("you usually open YouTube around now"). Nothing leaves the phone.
 */
object Habits {
    private fun prefs() = Core.prefs

    private fun key(cmd: String): String {
        val w = cmd.lowercase().trim().split(Regex("\\s+")).take(4).joinToString(" ")
        return w.take(40)
    }

    fun record(cmd: String) {
        try {
            if (!prefs().habitLearning) return
            val k = key(cmd)
            if (k.length < 3 || k.startsWith("what") || k.startsWith("who")) return
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val root = JSONObject(prefs().habitsJson)
            val per = root.optJSONObject(k) ?: JSONObject()
            per.put(h.toString(), per.optInt(h.toString(), 0) + 1)
            root.put(k, per)
            if (root.length() > 60) { // keep it small: drop the weakest habit
                var worst: String? = null; var wc = Int.MAX_VALUE
                for (name in root.keys()) {
                    val o = root.getJSONObject(name); var tot = 0
                    for (hh in o.keys()) tot += o.optInt(hh)
                    if (tot < wc) { wc = tot; worst = name }
                }
                if (worst != null) root.remove(worst)
            }
            prefs().habitsJson = root.toString()
        } catch (e: Exception) {}
    }

    fun onNotification(pkg: String) {}

    /** A command you often run near this hour (needs at least 3 uses), or null. */
    fun suggestion(): String? {
        return try {
            if (!prefs().habitLearning) return null
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val root = JSONObject(prefs().habitsJson)
            var best: String? = null; var bc = 0
            for (name in root.keys()) {
                val o = root.getJSONObject(name)
                val c = o.optInt(h.toString(), 0) + o.optInt(((h + 23) % 24).toString(), 0) / 2
                if (c > bc) { bc = c; best = name }
            }
            if (bc >= 3) best else null
        } catch (e: Exception) { null }
    }

    fun clear() { try { prefs().habitsJson = "{}" } catch (e: Exception) {} }
}
