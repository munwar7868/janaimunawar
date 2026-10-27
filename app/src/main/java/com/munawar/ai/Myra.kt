package com.munawar.ai

import android.Manifest
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MYRA-style tool pack: communication/SOS, call assistant, device tools, notifications intelligence, parking saver,
 * smart modes, files, missions, PC connect and connectors. Everything is honest about what Android allows.
 */
object Myra {
    private fun ok(m: String) = Automation.Result(true, m)
    private fun no(m: String) = Automation.Result(false, m)

    /** Opens one of Munawar's own screens (the app comes to the front). */
    private fun screen(a: Host, name: String, msg: String): Automation.Result = try {
        a.startActivity(Intent(a, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("screen", name)); ok(msg)
    } catch (e: Exception) { no("Couldn't open the screen.") }

    private fun open(a: Host, i: Intent, okMsg: String): Automation.Result = try {
        a.startActivity(i); ok(okMsg)
    } catch (e: Exception) { no("No app on this phone can handle that.") }

    suspend fun handle(a: Host, t: String): Automation.Result? {
        val l = t.lowercase().trim().trimEnd('.', '!', '?')
        if (l.isEmpty()) return null

        // ---------- device tools ----------
        if (Regex("^(?:check |show |my )?battery (?:info|status|health|report|details)$|^battery$|^battery kitni hai$").matches(l)) return ok(batteryReport(a))
        if (Regex("^(?:storage|storage info|storage status|storage analy[sz]er|analy[sz]e storage|storage kitni hai|free space|space left)$").matches(l)) return ok(storageReport(a))
        if (Regex("^(?:clean|free up|clear) (?:my )?(?:storage|space)$").matches(l)) return open(a, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), "Opened Android's Storage screen. Tap “Free up space” there (Android doesn't let apps delete other apps' data).")
        if (Regex("^(?:phone health|diagnose (?:my )?phone|check (?:my )?phone|phone check ?up|phone doctor)$").matches(l)) return ok(healthDiagnosis(a))
        Regex("^(?:copy|clipboard)\\s+(?:to clipboard\\s+)?(.+)$").find(l)?.let { m ->
            val txt = Regex("^(?:copy|clipboard)\\s+(?:to clipboard\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(t.trim())?.groupValues?.get(1) ?: m.groupValues[1]
            (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Munawar", txt))
            return ok("Copied to clipboard: \"${txt.take(80)}\"")
        }
        if (Regex("^(?:read|show|paste)\\s+(?:my\\s+)?clipboard$|^clipboard$").matches(l)) {
            val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val txt = try { cm.primaryClip?.getItemAt(0)?.coerceToText(a)?.toString() } catch (e: Exception) { null }
            return if (txt.isNullOrBlank()) no("Clipboard is empty, or Android only lets the app on screen read it (open Munawar and try again).") else ok("Clipboard: $txt")
        }
        if (Regex("^(?:lock|lock (?:my )?(?:phone|device|screen)|device lock)$").matches(l)) {
            if (Admin.lockNow(a)) return ok("Device locked.")
            val s = A11y.svc() ?: return no("To lock by voice allow Device admin (☰ → Control center → Open next special permission) or turn on Accessibility.")
            return Automation.Result(s.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN), "Device locked.")
        }

        PhoneControl.handle(a, l)?.let { return it }
        AppControl.handle(a, l, t)?.let { return it }

        if (Regex("^(?:fix yourself|check yourself|self[- ]?check|doctor|diagnose yourself|apne aap ko theek karo|run diagnostics)$").matches(l)) return Doctor.run(a)
        if (Regex("^(?:stop|mute|band karo) (?:announcing|announcements)$|^(?:don't|do not) announce\\b.*$").matches(l)) { a.prefs.announceMode = "off"; return ok("OK, I will stop telling you about calls and messages by myself.") }
        if (Regex("^(?:start|unmute|chalu karo) (?:announcing|announcements)$|^announce (?:calls|messages) (?:on|again)$").matches(l)) { a.prefs.announceMode = "always"; return ok("From now on I will tell you about calls and messages myself.") }
        Regex("^routine (morning|night)$").find(l)?.let { m -> return ok(Buddy.routine(a, m.groupValues[1])) }

        // ---------- SOS ----------
        if (Regex("^(?:send (?:an? )?)?(?:sos|emergency)(?: alert| message| help| sms)?$|^help me sos$|^mujhe bachao$").matches(l)) return sos(a)

        // ---------- call assistant ----------
        if (Regex("^(?:answer|pick up|accept|receive)(?: the| this| my| incoming)?(?: phone)? call$|^call (?:utha|uthao|utha lo|receive karo)$").matches(l)) return answerCall(a)
        if (Regex("^(?:reject|decline|cut|end|hang up)(?: the| this| my| incoming)?(?: phone)? call$|^hang ?up$|^call (?:kaat|kaato|cut karo|reject karo)$").matches(l)) return endCall(a)
        Regex("^(?:lookup|look up|find|search)\\s+(?:contact|number)\\s+(?:of\\s+|for\\s+)?(.+)$|^number of (.+)$|^(.+?) ka number$").find(l)?.let { m ->
            val q = (1..3).map { m.groupValues[it] }.firstOrNull { it.isNotBlank() } ?: return null
            return lookup(a, q.trim())
        }
        if (Regex("^(?:read |show |check )?(?:my )?missed calls?$").matches(l)) return missedCalls(a)

        // ---------- notification intelligence ----------
        if (Regex("^(?:read|tell|show|batao|padh)\\s+(?:me\\s+)?(?:my\\s+|the\\s+|latest\\s+|last\\s+)*otp(?: code)?$|^otp$|^otp kya (?:hai|aaya)$").matches(l)) return readOtp(a)
        if (Regex("^(?:clear|dismiss|remove|saaf karo)\\s+(?:all\\s+)?(?:my\\s+)?notifications?$").matches(l)) {
            if (!Perms.notifAccess(a)) return no("Turn on Notification access first: Control tab → Notification access.")
            return if (NotifListener.clearAll()) ok("All notifications cleared.") else no("Notification access isn't connected yet. Toggle it off and on in Android settings.")
        }
        Regex("^reply(?:\\s+to)?\\s+(.*?)\\s*(?:saying|that|with|:)\\s+(.+)$").find(l)?.let { m ->
            if (!Perms.notifAccess(a)) return no("Turn on Notification access first: Control tab → Notification access.")
            val body = Regex("(?i)(?:saying|that|with|:)\\s+(.+)$").find(t)?.groupValues?.get(1)?.trim() ?: m.groupValues[2]
            val who = m.groupValues[1].replace(Regex("^(?:the|last|latest|message|notification|from)\\s+"), "").trim()
            val r = NotifListener.reply(who, body)
            return Automation.Result(r.startsWith("Replied"), r)
        }

        // ---------- parking saver ----------
        if (Regex("^(?:save|remember|mark)\\s+(?:my\\s+)?(?:parking|car)(?: here| location| spot)?$|^park(?:ed)? here$|^parking save$").matches(l)) return saveParking(a)
        if (Regex("^(?:find|where is|where's|show|navigate to)\\s+(?:my\\s+)?(?:parking|car)(?: spot)?$|^where did i park$|^meri (?:gaadi|car|parking) kahan hai$").matches(l)) return findParking(a)

        // ---------- smart modes ----------
        Regex("^(?:(?:mode|activate|start|turn on|enable|switch to)\\s+)?(driving|sleep|work|game|gaming|normal)(?:\\s+mode)?(?:\\s+on)?$").find(l)?.let { m ->
            if (m.groupValues[1] == "game" || m.groupValues[1] == "gaming" || l.contains("mode") || l.startsWith("activate") || l.startsWith("start") || l.startsWith("turn on") || l.startsWith("enable") || l.startsWith("switch"))
                return setMode(a, m.groupValues[1])
        }
        if (Regex("^(?:mode off|turn off (?:smart )?mode|normal mode)$").matches(l)) return setMode(a, "normal")

        // ---------- files / traffic / maps honesty ----------
        if (Regex("^(?:open )?(?:file manager|my files|file explorer|files)$").matches(l)) {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "vnd.android.document/root")
            val r = open(a, i, "Files opened.")
            return if (r.ok) r else screen(a, "Files", "File manager opened.")
        }
        if (Regex("^(?:open )?gallery$").matches(l)) return open(a, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY), "Gallery opened.")
        if (Regex("^(?:delete|remove|clean|clear|saaf karo)\\s+(?:my\\s+|all\\s+|old\\s+)*(?:photos?|pictures?|images?|screenshots?)(?: .*)?$").matches(l))
            return screen(a, "Files", "Photo cleaner opened: tap “Scan albums”, then Delete next to the album. Android asks you to confirm.")
        if (Regex("^(?:zip|share|delete|manage|browse)\\s+(?:my\\s+)?(?:files?|folders?|documents?)(?: .*)?$").matches(l))
            return screen(a, "Files", "File manager opened: choose a folder, tick files, then Share / Zip / Delete.")
        if (Regex("^(?:traffic|traffic update|how is (?:the )?traffic|live traffic)(?: .*)?$").matches(l))
            return open(a, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/@?api=1&map_action=map&layer=traffic")), "Google Maps opened with the live traffic layer. I don't invent traffic numbers; read them straight off the map.")
        if (Regex("^(?:download |save )?offline maps?(?: .*)?$").matches(l)) return no("Offline maps download is not available yet (coming soon). Google Maps has its own offline maps in its menu.")

        // ---------- missions ----------
        Regex("^(?:start|begin|run)\\s+(?:a\\s+)?mission\\s*[:\\-]?\\s*(.+)$|^mission\\s*[:\\-]\\s*(.+)$").find(t.trim().lowercase())?.let { m ->
            val goal = Regex("(?i)mission\\s*[:\\-]?\\s*(.+)$").find(t)?.groupValues?.get(1)?.trim() ?: m.groupValues[1].ifBlank { m.groupValues[2] }
            return ok(Missions.start(a, goal))
        }
        if (Regex("^pause (?:the |my )?mission$").matches(l)) return ok(Missions.pause())
        if (Regex("^(?:resume|continue) (?:the |my )?mission$").matches(l)) return ok(Missions.resume())
        if (Regex("^(?:cancel|stop|abort) (?:the |my )?mission$").matches(l)) return ok(Missions.cancel())
        if (Regex("^mission status$").matches(l)) return ok(Missions.status())

        // ---------- connectors ----------
        if (Regex("^(?:my )?(?:github )?(?:repos|repositories)$|^github(?: repos| repositories)?$").matches(l)) {
            return try { ok(Connectors.githubRepos(a.prefs)) } catch (e: Exception) { no("GitHub: ${e.message}") }
        }
        Regex("^telegram\\s+(?:saying\\s+|message\\s+|send\\s+)?(.+)$").find(t.trim().lowercase())?.let {
            val body = Regex("(?i)^telegram\\s+(?:saying\\s+|message\\s+|send\\s+)?(.+)$").find(t.trim())?.groupValues?.get(1) ?: it.groupValues[1]
            return try { val r = Connectors.telegram(a.prefs, body); Automation.Result(r.startsWith("Sent"), r) } catch (e: Exception) { no("Telegram: ${e.message}") }
        }
        Regex("^(?:deep\\s+)?research\\s+(?:on\\s+|about\\s+)?(.+)$").find(t.trim().lowercase())?.let {
            val topic = Regex("(?i)^(?:deep\\s+)?research\\s+(?:on\\s+|about\\s+)?(.+)$").find(t.trim())?.groupValues?.get(1) ?: it.groupValues[1]
            return try { ok(Connectors.research(a.prefs, topic)) } catch (e: Exception) { no("Research failed: ${e.message}") }
        }
        Regex("^ask\\s+(groq|openai|chatgpt|claude)\\s+(.+)$").find(t.trim().lowercase())?.let { m ->
            val prov = when (m.groupValues[1]) { "chatgpt" -> "openai"; else -> m.groupValues[1] }
            val q = Regex("(?i)^ask\\s+\\w+\\s+(.+)$").find(t.trim())?.groupValues?.get(1) ?: m.groupValues[2]
            return try { ok(Connectors.altChat(a.prefs, q, prov)) } catch (e: Exception) { no("${m.groupValues[1]}: ${e.message}") }
        }
        return null
    }

    // =============== device reports ===============
    private fun mb(b: Long) = if (b >= 1L shl 30) "%.1f GB".format(b / 1073741824.0) else "${b shr 20} MB"

    fun batteryReport(a: Context): String {
        val i = a.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Battery information isn't available."
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not charging"
            else -> "unknown"
        }
        val plug = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) { BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"; BatteryManager.BATTERY_PLUGGED_USB -> "USB"; BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"; else -> "not plugged in" }
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEATING"; BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over-voltage"; BatteryManager.BATTERY_HEALTH_COLD -> "cold"; else -> "unknown"
        }
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        val saver = (a.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        return "🔋 Battery ${if (lvl >= 0) lvl * 100 / scale else -1}%% • $status ($plug)\nHealth: $health • Temp: %.1f°C • Battery saver: ${if (saver) "on" else "off"}".format(temp)
    }

    fun storageReport(a: Context): String {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.totalBytes; val free = st.availableBytes; val used = total - free
        val pct = if (total > 0) (used * 100 / total).toInt() else 0
        val cache = dirSize(a.cacheDir) + (a.externalCacheDir?.let { dirSize(it) } ?: 0L)
        val tip = if (pct >= 90) "⚠ Almost full: delete big videos/unused apps." else if (pct >= 75) "Getting full: consider cleaning up." else "Looks healthy."
        return "💾 Storage: ${mb(used)} used of ${mb(total)} ($pct%) • ${mb(free)} free\nMunawar's own cache: ${mb(cache)}\n$tip\nSay “clean storage” to open Android's cleanup screen."
    }

    private fun dirSize(f: java.io.File): Long = try {
        if (f.isFile) f.length() else (f.listFiles()?.sumOf { dirSize(it) } ?: 0L)
    } catch (e: Exception) { 0L }

    fun deviceReport(a: Context): String {
        val am = a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        val up = SystemClock.elapsedRealtime() / 3600000.0
        return "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
            batteryReport(a) + "\n" + storageReport(a).substringBefore("\nMunawar") + "\n" +
            "RAM: ${mb(mi.availMem)} free of ${mb(mi.totalMem)}${if (mi.lowMemory) " (LOW MEMORY)" else ""}\nUptime since last restart: %.1f hours".format(up)
    }

    private suspend fun healthDiagnosis(a: Host): String {
        val rep = deviceReport(a)
        val key = a.prefs.apiKey() ?: return rep + "\n\n(Add a Gemini key in Settings for an AI diagnosis.)"
        return try {
            val d = Gemini.generate(key, a.prefs.model, 0.3f,
                "You are a phone-health assistant. Given real device stats, give a short diagnosis: what is fine, what needs attention, and 3 practical tips. Do not invent data that is not in the report.",
                listOf(Turn("user", rep)))
            rep + "\n\n🩺 " + d
        } catch (e: Exception) { rep }
    }

    // =============== SOS ===============
    @Suppress("MissingPermission")
    private fun lastLocation(a: Context): Location? {
        if (!Perms.hasLocation(a)) return null
        val lm = a.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.getProviders(true).mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: SecurityException) { null } }.maxByOrNull { it.time }
    }

    private fun sos(a: Host): Automation.Result {
        val nums = a.prefs.sosNumbers.split(Regex("[,;\\s]+")).map { it.trim() }.filter { it.length >= 5 }
        if (nums.isEmpty()) return no("No emergency contact saved. Add numbers in Settings → Safety & SOS first.")
        val loc = lastLocation(a)
        val where = if (loc != null) "My location: https://maps.google.com/?q=${loc.latitude},${loc.longitude}" else "My location is unavailable right now."
        val body = "🚨 SOS! I need help. $where (sent by Munawar AI)"
        a.countdown("🚨 SOS alert", "To: ${nums.joinToString(", ")}\n\n$body") {
            for (n in nums) Automation.sendSms(a, n, body)
        }
        return ok("SOS ready for ${nums.size} contact(s); sending ${if (a.prefs.confirmSeconds > 0) "in ${a.prefs.confirmSeconds}s unless you cancel" else "now"}.")
    }

    // =============== calls ===============
    private fun answerCall(a: Host): Automation.Result {
        if (!Perms.has(a, Manifest.permission.ANSWER_PHONE_CALLS)) {
            a.requestPerm(Manifest.permission.ANSWER_PHONE_CALLS) { g -> a.toast(if (g) "Allowed. Say the command again." else "Permission denied.") }
            return no("I need the “Phone calls” permission to answer. Allow it, then say it again.")
        }
        return try {
            (a.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).acceptRingingCall()
            ok("Answer request sent (Android can't confirm if no call was ringing).")
        } catch (e: SecurityException) { no("Android blocked answering.") } catch (e: Exception) { no("Couldn't answer: ${e.javaClass.simpleName}") }
    }

    private fun endCall(a: Host): Automation.Result {
        if (!Perms.has(a, Manifest.permission.ANSWER_PHONE_CALLS)) {
            a.requestPerm(Manifest.permission.ANSWER_PHONE_CALLS) { g -> a.toast(if (g) "Allowed. Say the command again." else "Permission denied.") }
            return no("I need the “Phone calls” permission to end a call. Allow it, then say it again.")
        }
        return try {
            val done = (a.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).endCall()
            if (done) ok("Call ended.") else no("There was no active or ringing call to end.")
        } catch (e: SecurityException) { no("Android blocked ending the call.") } catch (e: Exception) { no("Couldn't end the call: ${e.javaClass.simpleName}") }
    }

    private fun lookup(a: Host, q: String): Automation.Result {
        if (!Perms.has(a, Manifest.permission.READ_CONTACTS)) {
            a.requestPerm(Manifest.permission.READ_CONTACTS) { g -> a.toast(if (g) "Contacts allowed. Say it again." else "Contacts permission denied.") }
            return no("I need Contacts access. Allow it, then ask again.")
        }
        val out = ArrayList<String>()
        try {
            a.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$q%"), ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )?.use { c -> while (c.moveToNext() && out.size < 5) out.add("${c.getString(0)}: ${c.getString(1)}") }
        } catch (e: Exception) { return no("Couldn't read contacts.") }
        return if (out.isEmpty()) no("No contact matching \"$q\".") else ok(out.distinct().joinToString("\n"))
    }

    private fun missedCalls(a: Host): Automation.Result {
        if (!Perms.has(a, Manifest.permission.READ_CALL_LOG)) {
            a.requestPerm(Manifest.permission.READ_CALL_LOG) { g -> a.toast(if (g) "Allowed. Ask again." else "Call-log permission denied.") }
            return no("I need the call-log permission to read missed calls. Allow it, then ask again.")
        }
        val out = ArrayList<String>()
        try {
            a.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                "${CallLog.Calls.TYPE} = ?", arrayOf(CallLog.Calls.MISSED_TYPE.toString()), "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                val f = SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault())
                while (c.moveToNext() && out.size < 6) out.add("• ${c.getString(0) ?: c.getString(1)} at ${f.format(Date(c.getLong(2)))}")
            }
        } catch (e: Exception) { return no("Couldn't read the call log.") }
        return if (out.isEmpty()) ok("No missed calls. 🎉") else ok("Missed calls:\n" + out.joinToString("\n"))
    }

    // =============== OTP ===============
    private fun readOtp(a: Host): Automation.Result {
        if (!a.prefs.otpReadAloud) return no("OTP reading is switched off in Settings → Safety & SOS.")
        if (!Perms.notifAccess(a)) return no("Turn on Notification access first: Control tab → Notification access.")
        val o = NotifListener.latestOtp() ?: return no("I don't see a recent OTP notification.")
        return ok("Latest code (from ${o.first}): ${o.second}")
    }

    // =============== parking / modes ===============
    private fun saveParking(a: Host): Automation.Result {
        if (!Perms.hasLocation(a)) {
            a.requestAnyPerm(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { a.toast(if (Perms.hasLocation(a)) "Location allowed. Say it again." else "Location denied.") }
            return no("I need Location permission to save your parking spot. Allow it, then say it again.")
        }
        val loc = lastLocation(a) ?: return no("No location fix yet. Open Google Maps once so the phone gets a fix, then try again.")
        a.prefs.parkLat = loc.latitude.toFloat(); a.prefs.parkLng = loc.longitude.toFloat(); a.prefs.parkTime = System.currentTimeMillis()
        return ok("🅿 Parking saved (±${loc.accuracy.toInt()} m). Say “find my parking” later.")
    }

    private fun findParking(a: Host): Automation.Result {
        if (a.prefs.parkLat > 900f) return no("No parking spot saved yet. Say “save parking” where you park.")
        val lat = a.prefs.parkLat; val lng = a.prefs.parkLng
        val at = SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()).format(Date(a.prefs.parkTime))
        return open(a, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(My parking)")), "Parking saved on $at. Maps opened: tap Directions.")
    }

    private suspend fun setMode(a: Host, raw: String): Automation.Result {
        val mode = if (raw == "gaming") "game" else raw
        val vol = when (mode) { "driving" -> 80; "sleep" -> 10; "work" -> 30; "game" -> 90; else -> -1 }
        a.prefs.activeMode = if (mode == "normal") "none" else mode
        if (vol >= 0) Automation.tryHandle(a, "volume set to $vol")
        val txt = when (mode) {
            "driving" -> "🚗 Driving mode ON: volume 80%, short hands-free replies. Say “navigate to …” for maps."
            "sleep" -> "🌙 Sleep mode ON: volume 10%, soft short replies."
            "work" -> "💼 Work mode ON: volume 30%, concise professional replies."
            "game" -> "🎮 Game mode ON: volume 90%. Use the Game Coach tool in Tools for tips."
            else -> "Back to normal mode."
        }
        return ok(txt + (if (mode != "normal") " (Android doesn't let apps change Do-Not-Disturb silently.)" else ""))
    }
}

/** Autonomous multi-step missions. Gemini plans the steps; Munawar runs them one by one and you can pause or cancel. */
object Missions {
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var cancelled = false
    @Volatile private var goal = ""
    @Volatile private var step = 0
    @Volatile private var total = 0

    fun status(): String = if (!running) "No mission is running." else "Mission “$goal”: step $step of $total${if (paused) " (paused)" else ""}."

    fun start(a: Host, g: String): String {
        if (running) return "A mission is already running. Say “cancel mission” first."
        running = true; paused = false; cancelled = false; goal = g; step = 0; total = 0
        Core.scope.launch {
            try {
                val steps = Commands.plan(a, g)
                total = steps.size
                if (steps.isEmpty()) { a.toast("Mission: couldn't plan that."); return@launch }
                a.toast("Mission started: ${steps.size} steps")
                for ((i, s) in steps.withIndex()) {
                    while (paused && !cancelled) delay(500)
                    if (cancelled) break
                    step = i + 1
                    val r = try { Commands.run(Hosts.get(), s, false) } catch (e: Exception) { null }
                    AssistantBus.sub = "🎯 Mission ${i + 1}/${steps.size}: ${r?.message ?: "not understood: $s"}"
                    AssistantBus.post()
                    if (r == null || !r.ok) { a.toast("Mission stopped at step ${i + 1}: ${r?.message ?: s}"); break }
                    delay(2500)
                }
                if (!cancelled) a.toast("Mission finished")
            } catch (e: Exception) {
                a.toast("Mission failed: ${e.message}")
            } finally { running = false; paused = false }
        }
        return "Mission started: “$g”. I'll plan the steps and run them. Say “pause mission” or “cancel mission” any time."
    }

    fun pause(): String { if (!running) return "No mission is running."; paused = true; return "Mission paused." }
    fun resume(): String { if (!running) return "No mission is running."; paused = false; return "Mission resumed." }
    fun cancel(): String { if (!running) return "No mission is running."; cancelled = true; paused = false; return "Mission cancelled after the current step." }
}
