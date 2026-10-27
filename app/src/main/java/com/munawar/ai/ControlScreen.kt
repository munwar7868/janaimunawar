package com.munawar.ai

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Switch
import kotlinx.coroutines.launch

/** AI Control Center: status, always-listening, floating orb, special Android access, permissions, updates. */
class ControlScreen(private val a: MainActivity) : Screen {
    private val content = a.vbox().apply { setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(28)) }
    override val view: View = a.scroll(content)

    override fun onShow() {
        content.removeAllViews()
        populate()
    }

    private fun tick(ok: Boolean) = if (ok) "✔" else "✖"
    private fun open(i: Intent, fail: String = "Couldn't open that Android screen.") {
        try { a.startActivity(i) } catch (e: Exception) { a.toast(fail) }
    }
    private fun note(t: String) = content.addView(a.label(t, 12f, Theme.MUTED))
    private fun line(t: String, ok: Boolean? = null) =
        content.addView(a.label(t, 14f, if (ok == false) Theme.PINK else Theme.TEXT).apply { setPadding(0, a.dp(3), 0, a.dp(3)) })
    private fun btn(t: String, c: Int = Theme.PURPLE, f: () -> Unit) = content.addView(a.button(t, c, f), a.lp(MATCH, WRAP, m = 2))

    private fun populate() {
        val p = a.prefs
        content.addView(a.label("AI Control Center", 22f, Theme.CYAN, true))
        content.addView(a.button("✅ Allow all permissions", Theme.PURPLE) { a.requestAllPerms { onShow() } }, a.lp(MATCH, WRAP, m = 3))
        content.addView(a.button("➡ Open next special permission", Theme.CYAN) { a.openNextSpecial() }, a.lp(MATCH, WRAP, m = 3))

        // ---- AI status
        content.addView(a.section("AI status"))
        line("${tick(p.hasApiKey())} Gemini key • model ${p.model}", p.hasApiKey())
        line("${tick(p.elevenEnabled && p.secret("eleven") != null)} ElevenLabs voice " + (if (p.elevenEnabled) "on" else "off"))
        note(Stats.summary())
        btn("Test Gemini connection", Theme.CYAN) {
            val k = p.apiKey()
            if (k == null) a.toast("Add your API key in Settings.") else {
                a.toast("Testing…")
                a.scope.launch { try { Gemini.test(k, p.model); a.toast("✔ Gemini connected") } catch (e: Exception) { a.toast("✖ " + e.message) } }
            }
        }
        // ---- Background conversation
        content.addView(a.section("Runs in the background"))
        line("${tick(AssistantService.running)} Assistant service ${if (AssistantService.running) "running" else "stopped"}${if (AssistantBus.sessionOn) " • conversation ON" else ""}")
        note("Tap the orb once: Gemini connects and keeps listening. Minimise the app, open YouTube or close the app from recents: it keeps running (a notification shows with Mute / Stop / Stop all). If Gemini drops, it reconnects by itself. To make the screen-edge light and background app opening work over other apps, allow 'Display over other apps' below.")
        btn("Stop everything", Theme.RED) { AssistantService.send(a, AssistantService.A_QUIT); a.scope.launch { kotlinx.coroutines.delay(500); onShow() } }

        // ---- Always listening
        content.addView(a.section("Always listening: “Hey Munawar”"))
        line("${tick(p.wakeEnabled)} Wake word ${if (p.wakeEnabled) "ON" else "OFF"} • background service ${if (AssistantService.running) "running" else "not running"}")
        line("${tick(Perms.has(a, Manifest.permission.RECORD_AUDIO))} Microphone permission")
        if (Build.VERSION.SDK_INT >= 33) line("${tick(Perms.has(a, Manifest.permission.POST_NOTIFICATIONS))} Notification permission (for the listening notification)")
        btn(if (p.wakeEnabled) "Turn OFF always-listening" else "Turn ON always-listening", if (p.wakeEnabled) Theme.RED else Theme.PURPLE) { a.setWake(!p.wakeEnabled) }
        line("${tick(Perms.batteryIgnored(a))} Battery: ${if (Perms.batteryIgnored(a)) "unrestricted" else "optimized (Android may stop listening)"}", Perms.batteryIgnored(a))
        btn("Set battery to Unrestricted", Theme.CARD) {
            try { a.startActivity(Perms.batteryIntent(a)) } catch (e: Exception) { open(Perms.batteryListIntent()) }
        }
        note("How it works: Android has no free always-on keyword engine, so Munawar restarts the speech recognizer in a loop and watches for “Hey Munawar”. It uses battery, needs internet on most phones, and some phones beep on each restart. A persistent notification (with Stop) is required by Android. Listening pauses during calls and while Munawar speaks. On some brands (Xiaomi, Oppo, Vivo, Samsung) also allow Autostart / “Don't optimise” in the phone's own battery settings.")

        // ---- Floating orb
        content.addView(a.section("Floating orb"))
        line("${tick(Perms.overlay(a))} 'Display over other apps' ${if (Perms.overlay(a)) "allowed" else "not allowed"}", Perms.overlay(a))
        line("${tick(FloatingService.running)} Floating orb ${if (FloatingService.running) "showing" else "hidden"}")
        btn(if (FloatingService.running) "Hide floating orb" else "Show floating orb", if (FloatingService.running) Theme.RED else Theme.PURPLE) { toggleBubble() }
        if (!Perms.overlay(a)) btn("Allow 'Display over other apps'", Theme.CARD) { open(Perms.overlayIntent(a)) }
        note("Tap the orb: talk. Long-press: explain the current screen (needs Accessibility). Drag to move. Allowing overlay also lets wake-word and multi-step commands open apps from the background.")

        // ---- Accessibility
        content.addView(a.section("Accessibility control (tap, type, scroll, read screen)"))
        line("${tick(Perms.a11yEnabled(a))} Service ${if (Perms.a11yEnabled(a)) "enabled in Android" else "not enabled"}", Perms.a11yEnabled(a))
        content.addView(Switch(a).apply {
            text = "Allow Munawar to perform actions"; setTextColor(Theme.TEXT); isChecked = p.a11yAllowed; minimumHeight = a.dp(48)
            setOnCheckedChangeListener { _, v -> p.a11yAllowed = v; A11y.allowed = v }
        })
        btn("Open Accessibility settings", Theme.CARD) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        note("Steps: Accessibility → Installed apps → Munawar AI → turn on. On Android 13+ for apps installed outside the Play Store you may first need: Settings → Apps → Munawar AI → ⋮ (top right) → “Allow restricted settings”. Munawar acts only when you give a command, never types into password fields, and doesn't record the screen. The switch above is a kill-switch.")
        btn("Open app info (restricted settings)", Theme.CARD) { open(Perms.appInfoIntent(a)) }

        // ---- Notification access
        content.addView(a.section("Notification access"))
        line("${tick(Perms.notifAccess(a))} ${if (Perms.notifAccess(a)) "Allowed: say “read notifications”" else "Not allowed"}", Perms.notifAccess(a))
        btn("Open Notification access settings", Theme.CARD) { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        note("Munawar keeps the latest notifications in memory only (never saved) and reads them only when you ask. Same “Allow restricted settings” step may apply.")

        // ---- Permissions
        content.addView(a.section("Permissions"))
        btn("✨ Grant all permissions (one by one)", Theme.PURPLE) { grantAll() }
        permRow("Microphone (calls, wake word)", Manifest.permission.RECORD_AUDIO)
        permRow("Contacts (call/SMS by name)", Manifest.permission.READ_CONTACTS)
        permRow("Phone calls (direct call after you confirm)", Manifest.permission.CALL_PHONE)
        permRow("SMS (send after you confirm)", Manifest.permission.SEND_SMS)
        permRow("Camera (see through the camera, record video)", Manifest.permission.CAMERA)
        permRow("Answer / end calls by voice", Manifest.permission.ANSWER_PHONE_CALLS)
        permRow("Call log (read missed calls)", Manifest.permission.READ_CALL_LOG)
        run {
            val ok = Perms.hasLocation(a)
            line("${tick(ok)} Location (“where am I”)", ok)
            if (!ok) btn("Allow Location", Theme.CARD) { a.requestAnyPerm(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { onShow() } }
        }
        if (Build.VERSION.SDK_INT >= 33) permRow("Notifications", Manifest.permission.POST_NOTIFICATIONS)
        note("Camera is used when Munawar looks through the camera or you ask it to record video. Files and photos use Android's own pickers. Alarms/timers/calendar use the clock and calendar apps.")
        btn("Open all permissions in Android Settings", Theme.CARD) { open(Perms.appInfoIntent(a)) }

        // ---- Updates
        content.addView(a.section("Updates"))
        line("Version ${Updater.currentVersion(a)} • repo: ${p.updateRepo.ifEmpty { "(set in Settings)" }}")
        btn("Check for updates", Theme.CYAN) { checkUpdate() }
        note("Android doesn't allow silent self-updates. Munawar checks your GitHub Releases and opens the APK download for you to install.")
    }

    private fun grantAll() {
        val queue = ArrayList<Array<String>>()
        queue.add(arrayOf(Manifest.permission.RECORD_AUDIO))
        if (Build.VERSION.SDK_INT >= 33) queue.add(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        queue.add(arrayOf(Manifest.permission.READ_CONTACTS))
        queue.add(arrayOf(Manifest.permission.CALL_PHONE))
        queue.add(arrayOf(Manifest.permission.SEND_SMS))
        queue.add(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        fun next() {
            val p = queue.removeFirstOrNull()
            if (p == null) {
                onShow()
                val left = ArrayList<String>()
                if (!Perms.overlay(a)) left.add("Display over other apps")
                if (!Perms.a11yEnabled(a)) left.add("Accessibility")
                if (!Perms.notifAccess(a)) left.add("Notification access")
                if (!Perms.batteryIgnored(a)) left.add("Battery: Unrestricted")
                a.toast(if (left.isEmpty()) "✔ Everything is allowed" else "Still to allow in Android Settings: " + left.joinToString(", "))
                return
            }
            if (p.any { Perms.has(a, it) }) next() else a.requestAnyPerm(p) { next() }
        }
        next()
    }

    private fun permRow(title: String, perm: String) {
        val ok = Perms.has(a, perm)
        line("${tick(ok)} $title", ok)
        if (!ok) btn("Allow", Theme.CARD) { a.requestPerm(perm) { onShow() } }
    }

    private fun toggleBubble() {
        if (FloatingService.running) { FloatingService.stop(a); FloatingService.running = false; onShow(); return }
        if (!Perms.overlay(a)) {
            a.toast("Turn on 'Display over other apps' for Munawar AI, then come back.")
            open(Perms.overlayIntent(a)); return
        }
        val go: () -> Unit = { FloatingService.start(a); a.scope.launch { kotlinx.coroutines.delay(600); onShow() } }
        if (Build.VERSION.SDK_INT >= 33) a.requestPerm(Manifest.permission.POST_NOTIFICATIONS) { go() } else go()
    }

    private fun checkUpdate() {
        a.toast("Checking…")
        a.scope.launch {
            try {
                val r = Updater.latest(a.prefs.updateRepo)
                val cur = Updater.currentVersion(a)
                if (Updater.isNewer(r.tag, cur)) {
                    AlertDialog.Builder(a).setTitle("Update available: ${r.tag}")
                        .setMessage("You have $cur.\n\n${r.notes}")
                        .setPositiveButton("Download") { _, _ -> open(Intent(Intent.ACTION_VIEW, Uri.parse(r.apkUrl ?: r.page))) }
                        .setNegativeButton("Later", null).show()
                } else a.toast("✔ You're up to date ($cur)")
            } catch (e: Exception) { a.toast(e.message ?: "Update check failed") }
        }
    }
}
