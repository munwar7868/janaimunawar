package com.munawar.ai

import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import kotlinx.coroutines.launch

/** The feature hub (MYRA-style): every category with tap-to-fill example commands, plus shortcuts to Tools, Files and Triggers. */
class FeaturesScreen(private val a: MainActivity) : Screen {
    private val input: EditText = a.field("Type or tap a command below…")
    private val log = a.vbox()
    private var busy = false
    override val view: View = build()

    private fun chip(row: LinearLayout, text: String, cmd: String = text) {
        row.addView(a.button(text, Theme.CARD) { input.setText(cmd); input.setSelection(input.text.length) }, a.lp(WRAP, WRAP, m = 2))
    }

    private fun cat(root: LinearLayout, title: String, vararg cmds: String) {
        val card = a.card()
        card.addView(a.label(title, 16f, Theme.CYAN, true))
        val row = a.hbox()
        for (c in cmds) chip(row, c)
        card.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        root.addView(card, a.lp(MATCH, WRAP, m = 5))
    }

    private fun toolRow(root: LinearLayout) {
        val card = a.card()
        card.addView(a.label("✨ Smart / generative tools", 16f, Theme.CYAN, true))
        val row = a.hbox()
        fun t(label: String, id: String) = row.addView(a.button(label, Theme.PURPLE) { a.openTool(id) }, a.lp(WRAP, WRAP, m = 2))
        t("🌐 Website builder", "website"); t("🔎 Deep research", "research")
        t("🎮 Game coach", "gamecoach"); t("🩺 Phone health", "phonehealth")
        card.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        root.addView(card, a.lp(MATCH, WRAP, m = 5))
    }

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(28)) }
        root.addView(a.label("Features", 22f, Theme.CYAN, true))
        root.addView(a.label("Everything Munawar can do. Tap a chip to fill the command, edit it, press Run. You can also just say it. Android or the target app always has the final say, and risky actions show a Cancel window first.", 12f, Theme.MUTED))
        val row = a.hbox()
        row.addView(input, a.lp(0, WRAP, 1f, 2))
        row.addView(a.button("Run", Theme.PURPLE) { run(input.text.toString().trim()) }, a.lp(WRAP, WRAP, m = 2))
        root.addView(row)
        root.addView(log)

        cat(root, "1  Communication", "whatsapp mom saying hello", "sms 9876543210 saying call me", "email name@mail.com saying hello", "sos")
        cat(root, "2  Calls", "call mom", "answer call", "reject call", "lookup contact mom", "missed calls")
        cat(root, "3  Media", "play arijit singh on youtube", "pause music", "next song", "previous song", "volume set to 50")
        cat(root, "4  Device", "set alarm 7:30 am", "timer 10 minutes", "flashlight on", "battery info", "storage info", "lock", "copy hello world")
        cat(root, "📲 Phone control", "brightness 50", "brightness max", "auto rotate off", "do not disturb on", "vibrate mode", "wifi off", "bluetooth on", "hotspot on", "airplane mode on", "quick settings", "power menu", "volume set to 30")
        cat(root, "5  Files & photos", "open files", "open gallery", "take a photo", "delete photos", "share files", "zip files")
        cat(root, "👁 Vision & camera", "look at my screen what is this", "look at camera what is this", "look at front camera", "watch my screen", "watch camera", "vision off", "record video", "stop recording", "selfie")
        cat(root, "▶ YouTube & app control", "open youtube", "skip ad", "like", "subscribe", "comments", "comment nice video", "forward 20 seconds", "rewind 10 seconds", "next video", "fullscreen", "swipe up", "double tap right", "long press")
        val fcard = a.card()
        fcard.addView(a.label("📁 Files, zip, share & photo cleaner", 16f, Theme.CYAN, true))
        fcard.addView(a.button("Open Files & photos", Theme.PURPLE) { a.show("Files") }, a.lp(MATCH, WRAP, m = 3))
        root.addView(fcard, a.lp(MATCH, WRAP, m = 5))
        toolRow(root)
        cat(root, "🎯 Missions (multi-step, autonomous)", "start mission play lofi on youtube then set timer 20 minutes", "pause mission", "resume mission", "cancel mission", "mission status")
        cat(root, "🔔 Notifications", "read notifications", "reply to whatsapp saying ok", "read otp", "missed calls", "clear notifications")
        cat(root, "🗺 Maps & My World", "navigate to India Gate", "traffic", "where am i", "save parking", "find my parking", "restaurants near me", "driving mode", "sleep mode", "work mode", "game mode", "normal mode")
        cat(root, "🔍 Search, browser & apps", "google search weather today", "open website wikipedia.org", "open instagram", "open settings")
        cat(root, "📱 Screen automation (needs Accessibility)", "read screen", "explain screen", "click Search", "type hello", "scroll down", "go back", "go home", "take screenshot")
        cat(root, "🔌 Connectors", "github repos", "telegram hello from Munawar", "research latest AI news", "ask groq what is kotlin")

        val auto = a.card()
        auto.addView(a.label("⚡ Automation & triggers", 16f, Theme.CYAN, true))
        auto.addView(a.label("Run things on a time, when charging, when headphones connect, when the screen turns on/off, on Wi-Fi, or when you open an app. Munawar can also learn your habits and suggest things before you ask.", 12f, Theme.MUTED))
        auto.addView(a.button("Open triggers", Theme.PURPLE) { a.show("Triggers") }, a.lp(MATCH, WRAP, m = 3))
        root.addView(auto, a.lp(MATCH, WRAP, m = 5))

        val safe = a.card()
        safe.addView(a.label("🛡 Safety rules", 16f, Theme.CYAN, true))
        safe.addView(a.label(
            "• OTP & password privacy: OTP codes are hidden in notification reads and only spoken when you ask “read otp”.\n" +
                "• Call reject guard: a call is rejected only when you clearly say reject / decline / cut / end call.\n" +
                "• Lock command: “lock” locks instantly (needs Accessibility).\n" +
                "• Traffic honesty: Munawar never invents traffic data; it opens Google Maps with the live traffic layer.\n" +
                "• Voice passphrase lock (Settings → Safety): risky commands need “unlock <phrase>”.\n" +
                "• Offline maps download: coming soon (as in the MYRA plan).", 12f, Theme.TEXT))
        root.addView(safe, a.lp(MATCH, WRAP, m = 5))
        return a.scroll(root)
    }

    private fun run(t: String) {
        if (t.isEmpty() || busy) return
        busy = true
        a.scope.launch {
            try {
                val r = Commands.run(a.host, t)
                val line = "› $t\n" + (if (r == null) "⚠ Not recognised as a phone command. Try Chat for general questions." else (if (r.ok) "✔ " else "⚠ ") + r.message)
                log.addView(a.label(line, 13f, if (r?.ok == true) Theme.TEXT else Theme.PINK).apply { setPadding(0, a.dp(6), 0, a.dp(6)); setTextIsSelectable(true) }, 0)
            } finally { busy = false }
        }
    }
}
