package com.munawar.ai

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Phone commands (English / Hindi / Hinglish) through standard Android intents, plus Accessibility follow-ups
 * (tap WhatsApp "Send", tap first YouTube result) when the user enabled Accessibility.
 * We only report what Android confirmed; asynchronous follow-ups report through toasts.
 */
object Automation {
    data class Result(val ok: Boolean, val message: String)

    val examples = listOf(
        "open whatsapp", "yt kholo", "play arijit singh songs on youtube", "whatsapp mom saying hello",
        "sms 9876543210 saying call me", "call mom", "set alarm 7:30 am", "timer 10 minutes",
        "remind me in 20 minutes to drink water", "pause music", "next song", "flashlight on", "volume up",
        "navigate to India Gate", "restaurants near me", "where am i", "open website google.com",
        "add task buy milk", "show tasks", "daily briefing", "skip ad", "comment nice video", "look at screen",
        "open youtube then play lofi", "click Search", "scroll down", "go back", "take screenshot",
        "read screen", "explain screen", "read notifications", "bedroom light on", "who made you"
    )

    private val appPkgs = linkedMapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "youtube" to listOf("com.google.android.youtube"),
        "youtube music" to listOf("com.google.android.apps.youtube.music"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "messenger" to listOf("com.facebook.orca"),
        "instagram" to listOf("com.instagram.android", "com.instagram.lite"),
        "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web"),
        "chrome" to listOf("com.android.chrome"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "play store" to listOf("com.android.vending"),
        "snapchat" to listOf("com.snapchat.android"),
        "twitter" to listOf("com.twitter.android"),
        "netflix" to listOf("com.netflix.mediaclient"),
        "spotify" to listOf("com.spotify.music"),
        "amazon" to listOf("in.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"),
        "flipkart" to listOf("com.flipkart.android"),
        "paytm" to listOf("net.one97.paytm"),
        "phonepe" to listOf("com.phonepe.app"),
        "gpay" to listOf("com.google.android.apps.nbu.paisa.user"),
        "truecaller" to listOf("com.truecaller"),
        "linkedin" to listOf("com.linkedin.android"),
        "zoom" to listOf("us.zoom.videomeetings"),
        "meet" to listOf("com.google.android.apps.meetings"),
        "photos" to listOf("com.google.android.apps.photos"),
        "drive" to listOf("com.google.android.apps.docs"),
        "calendar" to listOf("com.google.android.calendar"),
        "canva" to listOf("com.canva.editor"),
        "github" to listOf("com.github.android"),
        "files" to listOf("com.google.android.apps.nbu.files", "com.android.documentsui", "com.google.android.documentsui"),
        "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock")
    )

    private val nameAliases = listOf(
        listOf("mom", "mummy", "mommy", "maa", "mother", "amma", "ammi", "mumma"),
        listOf("dad", "papa", "father", "abbu", "pappa", "daddy"),
        listOf("bhai", "brother", "bhaiya"), listOf("didi", "sister", "behen"), listOf("wife", "biwi"), listOf("husband", "pati")
    )

    private val openWords = setOf("open", "launch", "start", "kholo", "khol", "kholna", "khulao")
    private val openStop = setOf(
        "open", "launch", "start", "kholo", "khol", "kholna", "khulao", "do", "karo", "kar", "kro", "the", "app", "please",
        "plz", "mujhe", "mera", "meri", "ko", "ek", "zara", "munawar", "hey", "hi", "ai", "jaldi", "and", "aur", "pe", "par", "me"
    )
    private val ytStop = setOf(
        "play", "youtube", "on", "pe", "par", "me", "mein", "chalao", "chala", "bajao", "baja", "song", "songs", "gana", "gaana",
        "gaane", "search", "kar", "karo", "do", "dena", "de", "music", "video", "ka", "ke", "ki", "ek", "please", "plz", "mujhe",
        "suna", "suno", "sunao", "the", "a", "munawar", "hey", "in", "from", "open"
    )
    private val numRe = Regex("""\+?\d[\d\s-]{5,}\d""")
    private val emailRe = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")
    private val msgRe = Regex("(?i)^(?:send\\s+)?(?:an?\\s+)?(?:sms|message|msg|text)\\s+(?:to\\s+)?(.+?)\\s+(?:saying|that|likho|ki|ke|:)\\s*(.*)$")
    private val msgRe2 = Regex("(?i)^(?:send\\s+)?(?:sms|message|msg|text)\\s+(?:to\\s+)?(\\+?\\d[\\d\\s-]{5,}\\d|\\S+)\\s*(.*)$")
    private val waRe = Regex("(?i)^(?:send\\s+)?(?:a\\s+)?whatsapp(?:\\s+message)?\\s+(?:to\\s+)?(.+?)\\s+(?:saying|that|likho|ki|ke|:)\\s*(.+)$")
    private val waRe2 = Regex("(?i)^(?:send\\s+)?(?:a\\s+)?whatsapp(?:\\s+message)?\\s+(?:saying|likho|ki|that|:)\\s*(.+)$")
    private var torchOn = false

    fun tryHandle(a: Host, raw: String): Result? {
        val t = Lang.normalize(raw)
        if (t.isEmpty()) return null
        val l = t.lowercase()
        val words = l.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        val first = words.firstOrNull() ?: return null

        // ---- alarm / timer
        if (l.contains("alarm")) {
            val m = Regex("""(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?""").find(l)
            if (m != null) {
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                if (m.groupValues[3] == "pm" && h < 12) h += 12
                if (m.groupValues[3] == "am" && h == 12) h = 0
                if (h in 0..23 && min in 0..59) {
                    val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h)
                        .putExtra(AlarmClock.EXTRA_MINUTES, min).putExtra(AlarmClock.EXTRA_MESSAGE, "Munawar AI")
                    return launch(a, i, "Alarm app opened with %02d:%02d filled in. Confirm it there.".format(h, min))
                }
            }
        }
        if (l.contains("timer")) {
            val m = Regex("""(\d+)\s*(second|sec|minute|min|minat|hour|ghante|ghanta)""").find(l)
            if (m != null) {
                val n = m.groupValues[1].toInt()
                val secs = when {
                    m.groupValues[2].startsWith("sec") -> n
                    m.groupValues[2].startsWith("min") -> n * 60
                    else -> n * 3600
                }
                val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, secs).putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                return launch(a, i, "Timer app opened for $secs seconds. Confirm it there.")
            }
        }

        // ---- reminders (in-app notification)
        if (l.contains("remind") || l.contains("yaad dila") || l.startsWith("reminder")) {
            reminder(a, t, l)?.let { return it }
        }

        // ---- messaging & calling
        if (first == "call" || first == "dial") {
            val target = t.substring(first.length).trim().removePrefix("to ").trim()
            if (target.isEmpty()) return Result(false, "Tell me who to call, e.g. 'call mom' or 'call 9876543210'.")
            val (num, err) = resolveNumber(a, target)
            if (err != null) return err
            return doCall(a, target, num!!)
        }
        waRe.find(t)?.let { return whatsapp(a, it.groupValues[1].trim(), it.groupValues[2].trim()) }
        waRe2.find(t)?.let { return whatsapp(a, "", it.groupValues[1].trim()) }
        if (first in setOf("sms", "message", "msg", "text", "send") && !l.contains("whatsapp") && !l.contains("email") && !l.contains("mail")) {
            val m = msgRe.find(t) ?: msgRe2.find(t)
            if (m != null) {
                val target = m.groupValues[1].trim()
                val body = m.groupValues[2].trim()
                val explicit = first == "sms" || first == "send"
                if (explicit || numRe.containsMatchIn(target)) {
                    val (num, err) = resolveNumber(a, target)
                    if (err != null) return err
                    return sms(a, target, num!!, body)
                }
            }
        }
        if (Regex("(?i)^(?:send\\s+)?(?:an?\\s+)?(?:email|mail)\\b").containsMatchIn(t)) {
            val addr = emailRe.find(t)?.value ?: ""
            val body = Regex("(?i)\\b(?:saying|that|likho|ki|:)\\b[:\\s]*(.+)$").find(t)?.groupValues?.get(1)?.trim() ?: ""
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$addr"))
                .putExtra(Intent.EXTRA_SUBJECT, body.split(" ").take(6).joinToString(" ").ifEmpty { "Message" })
                .putExtra(Intent.EXTRA_TEXT, body)
            return launch(a, i, "Email app opened with a draft. Review it and press send yourself.")
        }
        if (l.contains("whatsapp") && words.any { it in setOf("message", "msg", "likho", "likh", "send", "bhejo", "bhej") }) {
            val body = Regex("(?i)\\b(?:saying|likho|likh|bolo|that|ki)\\b[:\\s]+(.+)$").find(t)?.groupValues?.get(1) ?: t.substringAfter(":", "").trim()
            val num = numRe.find(t)?.value?.filter { it.isDigit() } ?: ""
            return whatsapp(a, num, body)
        }

        if (words.size > 14) return null // long sentences are conversation, not commands

        // ---- media keys (music already playing)
        mediaCommand(a, l)?.let { return it }

        // ---- device controls
        if (l.contains("flashlight") || l.contains("torch")) return flashlight(a, l, words)
        if (l.contains("volume")) return volume(a, l, words)
        if (l.contains("wifi") && (l.contains(" on") || l.contains(" off") || l.contains("settings") || words.any { it in openWords })) {
            val i = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIFI_SETTINGS)
            return launch(a, i, "Android doesn't let apps switch Wi-Fi directly, so I opened the Wi-Fi panel. Tap the toggle.")
        }
        if (l.contains("bluetooth") && (l.contains(" on") || l.contains(" off") || l.contains("settings") || words.any { it in openWords })) {
            return launch(a, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Android doesn't let apps switch Bluetooth directly, so I opened Bluetooth settings.")
        }

        // ---- calendar
        if (l.contains("calendar event") || l.contains("add event") || l.startsWith("remind")) {
            val title = t.replace(Regex("(?i)remind me to|remind me|add event|calendar event"), "").trim().ifEmpty { "Reminder" }
            val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, title)
            return launch(a, i, "Calendar opened with \"$title\". Set the time and save it there.")
        }

        // ---- location / maps
        if (l.contains("my location") || l.contains("where am i") || l.contains("meri location") || l.contains("main kahan") || l.contains("mai kahan")) return myLocation(a)
        if (l.contains("near me") || l.contains("nearby") || l.contains("paas mein") || l.contains("paas me")) {
            val q = t.replace(Regex("(?i)near me|nearby|paas mein|paas me|find|show|search|dhundo|please"), "").trim().ifEmpty { "places" }
            return launch(a, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))), "Maps opened: \"$q\" around you.")
        }
        if (l.startsWith("navigate") || l.contains("directions to") || l.startsWith("map ") || l.contains("route to")) {
            val q = t.replace(Regex("(?i)navigate to|navigate|directions to|route to|map|please|karo|kar do"), "").trim()
            return launch(a, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))), "Maps opened for \"$q\".")
        }
        if (l.contains("take a photo") || l.contains("take photo") || l.contains("take a picture") || l.contains("selfie")) {
            return launch(a, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Camera opened. To have me analyse a photo use Tools → Photo.")
        }

        // ---- website
        Regex("(?i)(?:open|go to|visit)\\s+(?:website\\s+|site\\s+)?((?:https?://)?[\\w-]+(?:\\.[\\w-]+)+(?:/\\S*)?)").find(t)?.let { m ->
            val u = m.groupValues[1].let { if (it.startsWith("http")) it else "https://$it" }
            return launch(a, Intent(Intent.ACTION_VIEW, Uri.parse(u)), "Opened $u")
        }

        // ---- settings shortcuts / apps
        if (words.any { it in openWords } || l.endsWith("settings")) {
            when {
                l.contains("setting") -> return launch(a, Intent(Settings.ACTION_SETTINGS), "Android Settings opened.")
                l.contains("browser") -> return launch(a, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER), "Browser opened.")
            }
        }
        if (words.any { it in openWords }) {
            val name = words.filter { it !in openStop }.joinToString(" ")
            if (name.isNotEmpty()) return openApp(a, name)
        }
        if (words.size == 1 && (appPkgs.containsKey(first) || first == "camera" || first == "settings")) return openApp(a, first)

        // ---- YouTube / songs
        val wantsMusic = words.any { it in setOf("youtube", "song", "songs", "gana", "gaana", "gaane", "bajao", "music") } ||
            (first == "play" && !l.contains("play store"))
        if (wantsMusic) {
            val q = words.filter { it !in ytStop }.joinToString(" ")
            if (q.isEmpty()) return openApp(a, "youtube")
            return playYoutube(a, q)
        }

        // ---- Google search
        if (first == "google" || first == "search") {
            val q = t.replace(Regex("(?i)^(google|search)( for)?"), "").replace(Regex("(?i)search|karo|kar do"), "").trim()
            if (q.isNotEmpty()) return launch(a, Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q), "Web search opened for \"$q\".")
        }
        return null
    }

    // ================= apps =================
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    private fun score(label: String, q: String): Int {
        val l = norm(label); val n = norm(q)
        if (n.isEmpty() || l.isEmpty()) return 0
        return when {
            l == n -> 100
            l.startsWith(n) -> 80
            n.length >= 3 && l.contains(n) -> 70
            l.length >= 3 && n.contains(l) -> 60
            n.length >= 4 && lev(l, n) <= maxOf(1, n.length / 4) -> 50
            else -> 0
        }
    }

    @Suppress("DEPRECATION")
    private fun findApp(a: Context, name: String): Pair<String, Intent>? {
        val pm = a.packageManager
        val q = name.lowercase().trim()
        // 1) well-known package names (works even with unusual labels)
        for ((key, pkgs) in appPkgs) {
            if (norm(key) == norm(q) || (norm(q).length >= 4 && lev(norm(key), norm(q)) <= 1)) {
                for (p in pkgs) pm.getLaunchIntentForPackage(p)?.let { return Pair(key.replaceFirstChar { c -> c.uppercase() }, it) }
            }
        }
        // 2) fuzzy match on installed launcher apps
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val best = apps.map { it to score(it.loadLabel(pm).toString(), q) }.filter { it.second > 0 }
            .maxWithOrNull(compareBy<Pair<android.content.pm.ResolveInfo, Int>> { it.second }.thenBy { -it.first.loadLabel(pm).length }) ?: return null
        val intent = pm.getLaunchIntentForPackage(best.first.activityInfo.packageName) ?: return null
        return Pair(best.first.loadLabel(pm).toString(), intent)
    }

    private fun isInstalled(a: Context, pkg: String) = try { a.packageManager.getLaunchIntentForPackage(pkg) != null } catch (e: Exception) { false }

    private fun openApp(a: Context, name: String): Result {
        if (norm(name) == "camera") {
            val app = findApp(a, "camera")
            return if (app != null) launch(a, app.second, "Launch request sent: ${app.first}.")
            else launch(a, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Camera opened.")
        }
        val app = findApp(a, name)
        if (app != null) return launch(a, app.second, "Launch request sent: ${app.first}.")
        val known = appPkgs.keys.firstOrNull { norm(it) == norm(name) }
        return Result(false, if (known != null) "$known isn't installed on this phone." else "I couldn't find an installed app called \"$name\".")
    }

    // ================= YouTube =================
    private fun playYoutube(a: Host, q: String): Result {
        val ytm = "com.google.android.apps.youtube.music"
        val svc = A11y.svc()
        if (svc == null && isInstalled(a, ytm)) {
            val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(ytm)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*").putExtra(SearchManager.QUERY, q)
            try { a.startActivity(i); return Result(true, "Asked YouTube Music to play \"$q\" (Android can't confirm playback).") } catch (e: Exception) {}
        }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q) + "&sp=EgIQAQ%253D%253D"))
        if (isInstalled(a, "com.google.android.youtube")) i.setPackage("com.google.android.youtube")
        try { a.startActivity(i) } catch (e: Exception) { return Result(false, "No app can open YouTube.") }
        if (svc == null) return Result(true, "YouTube search opened for \"$q\". Tap a video (turn on Accessibility in Control and I'll tap the first result for you).")
        a.scope.launch {
            delay(3500)
            var ok = false
            for (n in 0 until 8) {
                val s = A11y.svc() ?: break
                if (s.clickFirstVideo()) { ok = true; break }
                delay(1200)
            }
            a.toast(if (ok) "▶ Playing the first result for \"$q\"" else "Search opened, but I couldn't pick a video. Tap one.")
        }
        return Result(true, "Searching YouTube for \"$q\" and tapping the first result…")
    }

    // ================= WhatsApp / SMS / call =================
    private fun waNumber(a: Host, raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (raw.trim().startsWith("+")) return d
        if (d.startsWith("00")) d = d.substring(2) else if (d.length == 11 && d.startsWith("0")) d = a.prefs.countryCode + d.substring(1)
        else if (d.length == 10) d = a.prefs.countryCode + d
        return d
    }

    private fun secsNote(a: Host) = if (a.prefs.confirmSeconds > 0) "in ${a.prefs.confirmSeconds}s unless you cancel" else "now"

    private fun whatsapp(a: Host, target: String, body: String): Result {
        var num: String? = null
        if (target.isNotBlank()) {
            val (n, err) = resolveNumber(a, target)
            if (err != null) return err
            num = waNumber(a, n!!)
        }
        val go = { openWhatsApp(a, num, body) }
        if (num == null || body.isBlank()) { go(); return Result(true, "WhatsApp opened with your text. Pick the chat and press send.") }
        a.countdown("WhatsApp → $target", "+$num\n\n$body", go)
        return Result(true, "Showing the number and message; sending ${secsNote(a)}.")
    }

    private fun openWhatsApp(a: Host, num: String?, body: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${num ?: ""}?text=" + Uri.encode(body)))
        when {
            isInstalled(a, "com.whatsapp") -> i.setPackage("com.whatsapp")
            isInstalled(a, "com.whatsapp.w4b") -> i.setPackage("com.whatsapp.w4b")
        }
        try { a.startActivity(i) } catch (e: Exception) { a.toast("Couldn't open WhatsApp."); return }
        if (num == null || body.isBlank()) return
        if (A11y.svc() == null) { a.toast("WhatsApp opened with your message. Press Send (enable Accessibility in Control and I'll press it)."); return }
        a.scope.launch {
            delay(2600)
            var ok = false
            for (n in 0 until 8) {
                val s = A11y.svc() ?: break
                if (s.clickByLabel(listOf("Send", "भेजें", "Enviar"))) { ok = true; break }
                delay(900)
            }
            a.toast(if (ok) "✔ WhatsApp: Send tapped" else "Couldn't find WhatsApp's Send button. Please press it.")
        }
    }

    private fun sms(a: Host, who: String, number: String, body: String): Result {
        if (body.isEmpty()) return Result(false, "What should the message say?")
        a.countdown("SMS → $who", "$number\n\n$body") { sendSms(a, number, body) }
        return Result(true, "Showing the number and message; sending ${secsNote(a)}.")
    }

    @Suppress("DEPRECATION")
    internal fun sendSms(a: Host, number: String, body: String) {
        a.requestPerm(Manifest.permission.SEND_SMS) { ok ->
            if (!ok) { a.toast("SMS permission denied. Allow it in Control → Permissions."); return@requestPerm }
            val action = "com.munawar.ai.SMS_SENT"
            val rx = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    val code = resultCode
                    try { a.unregisterReceiver(this) } catch (e: Exception) {}
                    a.toast(if (code == Activity.RESULT_OK) "✔ SMS sent (Android confirmed)" else "✖ SMS failed (code $code)")
                }
            }
            if (Build.VERSION.SDK_INT >= 33) a.registerReceiver(rx, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED) else a.registerReceiver(rx, IntentFilter(action))
            val pi = PendingIntent.getBroadcast(a, 0, Intent(action).setPackage(a.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            try {
                val sm = if (Build.VERSION.SDK_INT >= 31) a.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
                val parts = sm.divideMessage(body)
                if (parts.size > 1) sm.sendMultipartTextMessage(number, null, parts, ArrayList(List(parts.size) { pi }), null)
                else sm.sendTextMessage(number, null, body, pi, null)
            } catch (e: Exception) {
                try { a.unregisterReceiver(rx) } catch (x: Exception) {}
                a.toast("✖ SMS could not be sent: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun doCall(a: Host, who: String, number: String): Result {
        a.countdown("Calling $who", number) {
            a.requestPerm(Manifest.permission.CALL_PHONE) { ok ->
                val i = if (ok) Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")) else Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                try { a.startActivity(i); if (!ok) a.toast("Call permission missing: dialer opened instead") } catch (e: Exception) { a.toast("Couldn't start the call") }
            }
        }
        return Result(true, "Showing the number; calling ${secsNote(a)}.")
    }

    private fun resolveNumber(a: Host, target: String): Pair<String?, Result?> {
        val digits = numRe.find(target)?.value?.replace(Regex("[\\s-]"), "")
        if (digits != null) return Pair(digits, null)
        if (!Perms.has(a, Manifest.permission.READ_CONTACTS)) {
            a.requestPerm(Manifest.permission.READ_CONTACTS) { ok -> a.toast(if (ok) "Contacts allowed. Say the command again." else "Contacts permission denied.") }
            return Pair(null, Result(false, "I need Contacts access to find \"$target\". Allow it, then repeat the command."))
        }
        val n = findContact(a, target) ?: return Pair(null, Result(false, "No contact named \"$target\" found."))
        return Pair(n, null)
    }

    private fun findContact(a: Context, name: String): String? {
        val low = name.lowercase().trim()
        val candidates = nameAliases.firstOrNull { low in it } ?: listOf(name.trim())
        for (c in candidates) {
            val n = try {
                a.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$c%"), ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )?.use { if (it.moveToFirst()) it.getString(0)?.replace(Regex("[\\s-]"), "") else null }
            } catch (e: Exception) { null }
            if (n != null) return n
        }
        return null
    }

    // ================= reminders / media / device =================
    private fun reminder(a: Host, t: String, l: String): Result? {
        var whenMs = 0L
        var strip = ""
        val rel = Regex("(\\d+)\\s*(second|sec|minute|min|minat|hour|hr|ghante|ghanta|day)s?").find(l)
        val abs = Regex("\\bat\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b").find(l)
        if (rel != null && (l.contains(" in ") || l.contains("baad") || l.contains("mein") || l.contains(" me "))) {
            val n = rel.groupValues[1].toLong()
            val ms = when {
                rel.groupValues[2].startsWith("sec") -> n * 1000
                rel.groupValues[2].startsWith("min") -> n * 60_000
                rel.groupValues[2].startsWith("day") -> n * 86_400_000
                else -> n * 3_600_000
            }
            whenMs = System.currentTimeMillis() + ms; strip = rel.value
        } else if (abs != null) {
            var h = abs.groupValues[1].toInt(); val m = abs.groupValues[2].ifEmpty { "0" }.toInt()
            if (abs.groupValues[3] == "pm" && h < 12) h += 12
            if (abs.groupValues[3] == "am" && h == 12) h = 0
            if (h > 23 || m > 59) return null
            val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }
            if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
            whenMs = c.timeInMillis; strip = abs.value
        } else return null
        val text = t.replace(Regex("(?i)remind me|reminder|yaad dilana|yaad dila|\\bto\\b|\\bin\\b|\\bat\\b|baad|mein|\\bme\\b|\\bki\\b|\\bko\\b")
            .let { it }, " ").replace(Regex(Regex.escape(strip), RegexOption.IGNORE_CASE), " ").replace(Regex("\\s+"), " ").trim().ifEmpty { "Reminder" }
        if (Build.VERSION.SDK_INT >= 33 && !Perms.has(a, Manifest.permission.POST_NOTIFICATIONS)) a.requestPerm(Manifest.permission.POST_NOTIFICATIONS) {}
        Reminders.schedule(a, whenMs, text)
        val at = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(whenMs))
        return Result(true, "Reminder scheduled for $at: \"$text\" (you'll get a notification; Android may delay it slightly in battery saver).")
    }

    private fun mediaKey(a: Context, code: Int) {
        val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun mediaCommand(a: Context, l: String): Result? {
        val pause = Regex("^(?:please )?(?:pause|stop)(?: the)?(?: music| song| video| track| playback)?$|^(?:music|song|video) (?:pause|stop)$")
        val play = Regex("^(?:please )?(?:play|resume|continue)(?: the)?(?: music| song| video| track| playback)?$|^(?:music|song) (?:play|resume)$")
        return when {
            pause.matches(l) -> { mediaKey(a, KeyEvent.KEYCODE_MEDIA_PAUSE); Result(true, "Sent pause to the active media app (Android can't confirm).") }
            play.matches(l) -> { mediaKey(a, KeyEvent.KEYCODE_MEDIA_PLAY); Result(true, "Sent play to the active media app (Android can't confirm).") }
            Regex("\\bnext (song|track|video)\\b|\\bagla (gana|song)\\b|^next$").containsMatchIn(l) -> { mediaKey(a, KeyEvent.KEYCODE_MEDIA_NEXT); Result(true, "Skipped to next track (request sent).") }
            Regex("\\b(previous|last) (song|track|video)\\b|\\bpichla (gana|song)\\b|^previous$").containsMatchIn(l) -> { mediaKey(a, KeyEvent.KEYCODE_MEDIA_PREVIOUS); Result(true, "Went to previous track (request sent).") }
            else -> null
        }
    }

    private fun flashlight(a: Context, l: String, words: List<String>): Result {
        return try {
            val cm = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                ?: return Result(false, "This phone has no flashlight.")
            val on = when {
                words.contains("off") -> false
                words.contains("on") -> true
                else -> !torchOn
            }
            cm.setTorchMode(id, on)
            torchOn = on
            Result(true, "Flashlight ${if (on) "on" else "off"}.")
        } catch (e: Exception) { Result(false, "Couldn't control the flashlight (another app may be using the camera).") }
    }

    private fun volume(a: Context, l: String, words: List<String>): Result {
        val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val pct = Regex("(\\d{1,3})\\s*%?").find(l)?.groupValues?.get(1)?.toIntOrNull()
        when {
            pct != null && (l.contains("set") || l.contains("to")) -> am.setStreamVolume(AudioManager.STREAM_MUSIC, (pct.coerceIn(0, 100) * max / 100), AudioManager.FLAG_SHOW_UI)
            words.contains("max") || words.contains("full") -> am.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
            words.contains("unmute") -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            words.contains("mute") -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            words.any { it == "down" || it == "lower" || it == "decrease" || it == "kam" } -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            else -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        }
        return Result(true, "Volume is now ${am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max}%.")
    }

    @Suppress("MissingPermission")
    private fun myLocation(a: Host): Result {
        if (!Perms.hasLocation(a)) {
            a.requestAnyPerm(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { _ -> a.toast(if (Perms.hasLocation(a)) "Location allowed. Say the command again." else "Location permission denied.") }
            return Result(false, "I need Location permission. Allow it, then ask again.")
        }
        val lm = a.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = lm.getProviders(true).mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: SecurityException) { null } }.maxByOrNull { it.time }
            ?: return Result(false, "No location fix yet. Turn on Location and open Maps once, then try again.")
        val lat = "%.5f".format(best.latitude); val lng = "%.5f".format(best.longitude)
        return launch(a, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(You)")), "You are near $lat, $lng (±${best.accuracy.toInt()} m). Maps opened.")
    }

    private fun launch(a: Context, i: Intent, okMsg: String): Result = try {
        (a as? Host)?.launchNote = null
        a.startActivity(i)
        Result(true, (a as? Host)?.launchNote ?: okMsg)
    } catch (e: ActivityNotFoundException) {
        Result(false, "No app on this phone can handle that request.")
    } catch (e: SecurityException) {
        Result(false, "Android blocked that action (permission missing).")
    }
}
