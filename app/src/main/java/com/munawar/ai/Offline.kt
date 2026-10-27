package com.munawar.ai

import android.content.Context
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Basic answers that need no internet. */
object Offline {
    fun tryHandle(ctx: Context, raw: String): String? {
        val l = raw.lowercase().trim()
        if (l.split(" ").size > 7) return null
        val now = Date()
        val time = Regex("^(?:what(?:'s| is)? (?:the )?(?:current )?time(?: is it)?(?: now)?|(?:abhi )?time kya hai|kitne baje hain?|samay kya hai|current time|time)\\??$")
        val date = Regex("^(?:what(?:'s| is)? (?:the )?(?:today's )?date(?: today)?|today's date|aaj ki date(?: kya hai)?|date kya hai|aaj kya tarikh hai|tarikh kya hai|aaj kaun sa din hai)\\??$")
        val who = Regex("(who (made|created|built|developed|designed) you|who is your (creator|developer|owner|maker)|your creator|tumhe kisne banaya|tumhen kisne banaya|aapko kisne banaya|tujhe kisne banaya|kisne banaya|tumhara creator|tera creator|tumhare creator)")
        return when {
            who.containsMatchIn(l) -> "Mujhe ${Persona.CREATOR} ne banaya hai. 💜"
            time.matches(l) -> "Abhi time hai " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
            date.matches(l) -> "Aaj ki date: " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(now)
            l.contains("battery") && l.split(" ").size <= 5 && !l.contains("info") && !l.contains("status") && !l.contains("health") -> {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                "Battery ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}% hai."
            }
            else -> null
        }
    }
}
