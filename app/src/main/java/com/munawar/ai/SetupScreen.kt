package com.munawar.ai

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import kotlinx.coroutines.launch

/** First-launch wizard: 1) Gemini API key, 2) permissions. Shown once. */
class SetupScreen(private val a: MainActivity) : Screen {
    private val box = a.vbox().apply { setPadding(a.dp(18), a.dp(18), a.dp(18), a.dp(30)); gravity = Gravity.CENTER_HORIZONTAL }
    private var page = 1
    private var autoAsked = false
    override val view: View = a.scroll(box)

    init { page1() }

    private fun header() {
        val orb = OrbView(a).also { a.orbs.add(it) }
        box.addView(orb, a.lp(MATCH, a.dp(220)))
        box.addView(RainbowText(a, "MUNAWAR AI", 26f).apply { gravity = Gravity.CENTER })
        box.addView(a.label("Created by ${Persona.CREATOR}", 12f, Theme.MUTED).apply { gravity = Gravity.CENTER })
    }

    private fun page1() {
        page = 1
        box.removeAllViews(); header()
        box.addView(a.section("Step 1 of 2 — Gemini API key"))
        box.addView(a.label("Munawar's brain is Google Gemini. Get a free key from Google AI Studio, paste it below. It is encrypted on this phone and never shown or logged.", 13f, Theme.MUTED))
        box.addView(a.label("Default model: Gemini 3.5 Flash (preselected). Other models (3.6, 3.7, 3.8, Pro, Flash-Lite…) are in Settings → Chat model.", 13f, Theme.CYAN).apply { setPadding(0, a.dp(6), 0, 0) })
        box.addView(a.button("🌐 Open Google AI Studio (get key)", Theme.CYAN) {
            try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) } catch (e: Exception) { a.toast("No browser found") }
        }, a.lp(MATCH, WRAP, m = 4))
        val key = a.field("Paste Gemini API key", password = true)
        box.addView(key, a.lp(MATCH, WRAP, m = 4))
        val st = a.label("", 13f, Theme.MUTED)
        box.addView(st)
        box.addView(a.button("Save & test  →", Theme.PURPLE) {
            val k = key.text.toString().trim()
            if (k.length < 20) { st.text = "⚠ That doesn't look like a valid key."; return@button }
            a.prefs.saveApiKey(k); a.prefs.model = "gemini-3.5-flash"; key.setText(""); st.text = "Connecting to Gemini 3.5 Flash…"
            a.scope.launch {
                try { Gemini.test(k, a.prefs.model); st.text = "✔ Connected!"; page2() }
                catch (e: Exception) {
                    val msg = e.message ?: "Failed"
                    if (msg.contains("Model not found")) {
                        // 3.5 Flash isn't offered to this key: quietly pick the newest Flash model it supports
                        try {
                            val alt = Gemini.listModels(k).filter { it.contains("flash") && !it.contains("lite") && !it.contains("image") && !it.contains("live") }.sortedDescending().firstOrNull()
                            if (alt != null) { Gemini.test(k, alt); a.prefs.model = alt; st.text = "✔ Connected using $alt"; page2(); return@launch }
                        } catch (x: Exception) {}
                    }
                    st.text = "✖ $msg\nFix the key and try again, or continue and set it later in Settings."
                }
            }
        }, a.lp(MATCH, WRAP, m = 4))
        box.addView(a.button("Skip for now", Theme.CARD) { page2() }, a.lp(MATCH, WRAP, m = 4))
    }

    private fun tick(ok: Boolean) = if (ok) "✔" else "○"

    private fun perm(title: String, perm: String) {
        val ok = Perms.has(a, perm)
        val r = a.hbox()
        r.addView(a.label("${tick(ok)} $title", 14f, if (ok) Theme.TEXT else Theme.MUTED), a.lp(0, WRAP, 1f))
        if (!ok) r.addView(a.button("Allow", Theme.CARD) { a.requestPerm(perm) { page2() } }, a.lp(WRAP, WRAP, m = 2))
        box.addView(r)
    }

    private fun special(title: String, ok: Boolean, intent: () -> Intent) {
        val r = a.hbox()
        r.addView(a.label("${tick(ok)} $title", 14f, if (ok) Theme.TEXT else Theme.MUTED), a.lp(0, WRAP, 1f))
        if (!ok) r.addView(a.button("Open", Theme.CARD) {
            try { a.startActivity(intent()) } catch (e: Exception) { a.toast("Couldn't open that screen") }
        }, a.lp(WRAP, WRAP, m = 2))
        box.addView(r)
    }

    private fun page2() {
        page = 2
        box.removeAllViews(); header()
        box.addView(a.section("Step 2 of 2 — Permissions"))
        box.addView(a.label("Allow what you want Munawar to do. You can change everything later in ☰ → Control center.", 13f, Theme.MUTED))
        box.addView(a.button("✅ Allow all permissions", Theme.PURPLE) { a.requestAllPerms { page2() } }, a.lp(MATCH, WRAP, m = 4))
        if (!autoAsked) { autoAsked = true; box.post { a.requestAllPerms { page2() } } }
        perm("Microphone — talk to Munawar", Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perm("Notifications — reminders & listening notice", Manifest.permission.POST_NOTIFICATIONS)
        perm("Contacts — call / message by name", Manifest.permission.READ_CONTACTS)
        perm("Phone — place calls", Manifest.permission.CALL_PHONE)
        perm("SMS — send text messages", Manifest.permission.SEND_SMS)
        run {
            val ok = Perms.hasLocation(a)
            val r = a.hbox()
            r.addView(a.label("${tick(ok)} Location — “where am I”, nearby", 14f, if (ok) Theme.TEXT else Theme.MUTED), a.lp(0, WRAP, 1f))
            if (!ok) r.addView(a.button("Allow", Theme.CARD) { a.requestAnyPerm(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { page2() } }, a.lp(WRAP, WRAP, m = 2))
            box.addView(r)
        }
        box.addView(a.section("Special access (opens Android settings)"))
        special("Display over other apps — floating orb, background app opening", Perms.overlay(a)) { Perms.overlayIntent(a) }
        special("Accessibility — tap WhatsApp Send, play YouTube, click/scroll", Perms.a11yEnabled(a)) { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        special("Battery unrestricted — keeps “Hey Munawar” alive", Perms.batteryIgnored(a)) { Perms.batteryIntent(a) }
        special("Notification access — read & reply to notifications, OTP", Perms.notifAccess(a)) { Perms.notifAccessIntent() }
        special("Device admin — instant screen lock by voice", Admin.active(a)) { Admin.intent(a) }
        special("Modify system settings — brightness, auto-rotate", Perms.writeSettings(a)) { Perms.writeSettingsIntent(a) }
        special("Do Not Disturb access — silent / DND commands", Perms.dndAccess(a)) { Perms.dndIntent() }
        box.addView(a.button("➡ Open next special permission", Theme.CYAN) { a.openNextSpecial() }, a.lp(MATCH, WRAP, m = 4))
        box.addView(a.label("Tip: on Android 13+ installed-from-APK apps, open ☰ → Control Center → “Open app info” → ⋮ → “Allow restricted settings” before enabling Accessibility.", 11f, Theme.MUTED))
        box.addView(a.button("Finish ✓  Start using Munawar", Theme.PURPLE) { a.finishSetup() }, a.lp(MATCH, WRAP, m = 6))
    }

    override fun onShow() { if (page == 2) page2() }
}
