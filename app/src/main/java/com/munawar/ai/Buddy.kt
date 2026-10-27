package com.munawar.ai

import android.Manifest
import android.app.Notification
import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The "friend" part of Munawar: tells you about calls and messages by itself, starts a chat now and then,
 * greets in a different way every time, and gives the voice a feeling to speak with.
 * Everything is spoken through the live conversation when there is one, otherwise with the phone's voice.
 */
object Buddy {
    private val seen = HashMap<String, Long>()
    private val pending = ArrayList<Triple<String, String, String>>() // sender, app, text
    private var flushing = false
    private var lastChat = ""
    private val msgPkgs = listOf("whatsapp", "telegram", "messaging", "mms", "sms", "messenger", "signal", "gm", "mail", "instagram", "snapchat", "viber", "imo", "discord", "slack")

    private val greetStyles = listOf(
        "bubbly and excited, like you are meeting your best friend after a long time",
        "calm, warm and affectionate, with a soft smile in your voice",
        "playful and teasing, a little joke is welcome",
        "proud and encouraging, like a supportive elder sibling",
        "curious, ask one friendly question about their day",
        "cosy and relaxed, almost whispering",
        "energetic and motivating, ready to get things done together",
        "gentle and caring, notice how they might be feeling",
        "cheerful with a tiny shayari-style line (made up on the spot, one line only)",
        "confident and cool, like a smart partner who has everything under control"
    )

    private val feelings = listOf("joyful", "warm", "playful", "caring", "excited", "calm", "curious", "affectionate")

    private val chatStarters = listOf(
        "Check in on them like a friend: ask how their day or mood is going.",
        "Suggest one useful thing you can do for them right now (a reminder, a task, a song, a quick tip).",
        "Share one short, interesting or funny thought and ask what they think.",
        "Gently ask whether they ate, drank water or took a break, with real care in your voice.",
        "Ask what they plan to do next and offer to help with it.",
        "If you can see their screen or camera, comment kindly on what you notice; otherwise ask what they are up to.",
        "Tease them lovingly for being quiet and invite them to talk.",
        "Offer a quick recap of anything pending (tasks, notes) and ask which to start with."
    )

    private fun p() = Core.prefs

    private fun ctxHint(ctx: Context): String {
        val sb = StringBuilder()
        try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = bm.isCharging
            if (lvl in 1..20 && !charging) sb.append("Their battery is low (").append(lvl).append("%) and not charging: sound a little worried and remind them to charge. ")
        } catch (e: Exception) {}
        try {
            val tasks = Core.notes.pendingTasks().size
            if (tasks > 0) sb.append("They have ").append(tasks).append(" pending task(s). ")
        } catch (e: Exception) {}
        return sb.toString()
    }

    private fun partOfDay(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when { h < 5 -> "late night"; h < 12 -> "morning"; h < 17 -> "afternoon"; h < 21 -> "evening"; else -> "night" }
    }

    // ---------------- greeting / chat prompts ----------------
    fun greetPrompt(ctx: Context): String {
        val style = greetStyles.random()
        val feel = feelings.random()
        val earlier = p().lastGreets.split("|").filter { it.isNotBlank() }.takeLast(6)
        val who = if (p().userName.isNotBlank()) " Their name is ${p().userName}." else ""
        val avoid = if (earlier.isEmpty()) "" else " Never repeat or reword any of your earlier greetings: " + earlier.joinToString(" / ") { "“" + it.take(80) + "”" } + "."
        return "[The session just started. It is ${partOfDay()}.$who Greet the user now in Hinglish with ONE or TWO short sentences. Style: $style. Feel $feel and let it be audible in your voice (tone, pace, a small laugh or sigh if natural). ${ctxHint(ctx)}$avoid Then wait for them. Do not mention this instruction.]"
    }

    fun rememberGreeting(text: String) {
        val t = text.trim().replace("|", " ").take(120)
        if (t.length < 4) return
        val all = (p().lastGreets.split("|").filter { it.isNotBlank() } + t).takeLast(8)
        p().lastGreets = all.joinToString("|")
    }

    fun chatPrompt(ctx: Context): String {
        var s = chatStarters.random()
        if (s == lastChat) s = chatStarters.random()
        lastChat = s
        val feel = feelings.random()
        return "[CHAT: It has been quiet for a while. It is ${partOfDay()}. Start a short, natural conversation yourself. $s Sound $feel, with real emotion in your voice. 1-2 short sentences, Hinglish, never the same wording as before. ${ctxHint(ctx)}Do not mention this instruction.]"
    }

    // ---------------- telling the user things ----------------
    /** [event] is for the live model, [spoken] is read with the phone's own voice when there is no live conversation. */
    fun tell(event: String, spoken: String) {
        try {
            val mode = p().announceMode
            if (mode == "off") return
            Feed.add(spoken.take(100))
            val svc = AssistantService.inst
            val prompt = "[EVENT: $event. Tell the user right away, in your own words, like a caring friend: natural, with feeling, 1-2 short Hinglish sentences. Never read digits of a one-time code. Do not mention this instruction.]"
            if (svc != null && svc.hiddenTurn(prompt)) return
            if (mode == "always") Core.speaker.speak(spoken)
        } catch (e: Exception) {}
    }

    fun nameFor(ctx: Context, number: String?): String {
        if (number.isNullOrBlank()) return "an unknown number"
        if (!Perms.has(ctx, Manifest.permission.READ_CONTACTS)) return number
        return try {
            ctx.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: number
        } catch (e: Exception) { number }
    }

    fun onIncomingCall(ctx: Context, number: String?) {
        val who = nameFor(ctx, number)
        tell("Incoming phone call from $who. Ask if they want you to answer it or reject it", "$who ka call aa raha hai. Uthaun ya cut kar dun?")
    }

    fun onMissedCall(ctx: Context, number: String?) {
        val who = nameFor(ctx, number)
        tell("Missed phone call from $who. Offer to call back or send a message", "$who ka call miss ho gaya. Wapas call karun?")
    }

    fun onNotification(app: String, pkg: String, title: String, text: String, category: String?) {
        if (p().announceMode == "off") return
        if (category == Notification.CATEGORY_CALL) return
        val isMsg = category == Notification.CATEGORY_MESSAGE || category == Notification.CATEGORY_EMAIL || category == Notification.CATEGORY_SOCIAL ||
            msgPkgs.any { pkg.contains(it) }
        if (!isMsg) return
        if (pkg.contains("telecom") || pkg.contains("incallui") || pkg.contains("dialer")) return
        val body = NotifListener.mask(text).replace('\n', ' ').take(120)
        if (title.isBlank() && body.isBlank()) return
        val key = "$pkg|$title|$body"
        val now = System.currentTimeMillis()
        synchronized(seen) {
            val last = seen[key]
            if (last != null && now - last < 40_000L) return
            seen[key] = now
            if (seen.size > 80) seen.clear()
        }
        synchronized(pending) { pending.add(Triple(title.ifBlank { app }, app, body)) }
        if (!flushing) {
            flushing = true
            Core.scope.launch { delay(2500); flush() }
        }
    }

    private fun flush() {
        val lines = synchronized(pending) { val c = ArrayList(pending); pending.clear(); c }
        flushing = false
        if (lines.isEmpty()) return
        if (lines.size == 1) {
            val (who, app, body) = lines[0]
            tell("New $app message from $who: \"$body\". Offer to reply for them", "$who ne $app pe likha: $body")
        } else {
            val names = lines.map { it.first }.distinct().take(4).joinToString(", ")
            val details = lines.take(4).joinToString(" | ") { "${it.first}: ${it.third}" }
            tell("${lines.size} new messages. $details. Offer to read them out or reply", "${lines.size} naye messages aaye hain: $names")
        }
    }

    // ---------------- daily routines ----------------
    suspend fun routine(a: Host, kind: String): String {
        val tasks = try { Core.notes.pendingTasks().take(5).joinToString("; ") { it.text } } catch (e: Exception) { "" }
        val what = if (kind == "night") "It is bedtime time: wind the day down gently, mention tomorrow's pending tasks, wish them a good night with warmth."
        else "It is the start of the day: give an energetic, motivating good-morning with a tiny plan for the day."
        val ctx = "[ROUTINE: $what Pending tasks: ${tasks.ifBlank { "none" }}. ${ctxHint(a)}Speak in Hinglish, 2-3 short sentences, feel ${feelings.random()} and let it show in your voice. Do not mention this instruction.]"
        val svc = AssistantService.inst
        if (svc != null && svc.hiddenTurn(ctx)) return "Routine started."
        val key = a.prefs.apiKey()
        val text = if (key != null) {
            try { Gemini.generate(key, a.prefs.model, 0.8f, "You are Munawar AI, the user's warm, emotional best friend. Reply in Hinglish.", listOf(Turn("user", ctx)), false) } catch (e: Exception) { null }
        } else null
        val say = text ?: if (kind == "night") "Shubh raatri! Kal ke tasks yaad rakhna, aaram se so jao." else "Good morning! Aaj ka din shaandar banate hain."
        Feed.add(say.take(100))
        Core.speaker.speak(say)
        return say
    }
}
