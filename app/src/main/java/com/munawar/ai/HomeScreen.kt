package com.munawar.ai

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Home = the talking screen. It is only a remote control for AssistantService, which keeps the Gemini conversation alive
 * in the background (minimise or close the app and it keeps listening).
 */
class HomeScreen(private val a: MainActivity) : Screen {
    private val orb = OrbView(a).also { a.orbs.add(it); it.lite = a.prefs.liteMode }
    private val hello = a.label(greeting(), 13f, Theme.MUTED)
    private val moodTv = a.label("", 18f, Theme.CYAN, true).apply { gravity = Gravity.CENTER }
    private val status = a.label(AssistantBus.status, 18f, Theme.TEXT, true).apply { gravity = Gravity.CENTER }
    private val timer = a.label("", 13f, Theme.CYAN).apply { gravity = Gravity.CENTER }
    private val sub = a.label("", 14f, Theme.MUTED).apply { gravity = Gravity.CENTER }
    private val h = Handler(Looper.getMainLooper())
    private val controls = a.hbox()
    private val muteBtn: Button = a.button("🎙 Mute", Theme.CARD) { AssistantService.send(a, AssistantService.A_MUTE) }
    private val intBtn: Button = a.button("✋ Interrupt", Theme.CARD) { AssistantService.send(a, AssistantService.A_INTERRUPT) }
    private val endBtn: Button = a.button("■ End", Theme.RED) { AssistantService.send(a, AssistantService.A_STOP_SESSION) }
    private val tilt = Tilt(a)
    private val visionRow = a.hbox()
    private val feedList = a.vbox()
    private val feedCard = a.card().apply { visibility = View.GONE }
    private val habitCard = a.label("", 13f, Theme.CYAN).apply { gravity = Gravity.CENTER; visibility = View.GONE }
    private val listener: () -> Unit = { render() }
    private var ticking = false

    private fun greeting(): String {
        val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when { hr < 5 -> "Late night 🌌"; hr < 12 -> "Good morning ☀"; hr < 17 -> "Good afternoon 🌤"; hr < 21 -> "Good evening 🌆"; else -> "Good night 🌙" }
        val n = try { Core.prefs.userName } catch (e: Exception) { "" }
        return if (n.isNotBlank()) "$part, $n" else part
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!AssistantBus.sessionOn) { ticking = false; timer.text = ""; return }
            val s = (System.currentTimeMillis() - AssistantBus.startMs) / 1000
            timer.text = "● LIVE  %02d:%02d".format(s / 60, s % 60)
            h.postDelayed(this, 1000)
        }
    }

    override val view: View = build()

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(16), a.dp(8), a.dp(16), a.dp(28)); gravity = Gravity.CENTER_HORIZONTAL }

        val top = a.hbox()
        val titles = a.vbox()
        titles.addView(hello)
        titles.addView(RainbowText(a, "MUNAWAR AI", 26f))
        top.addView(titles, a.lp(0, WRAP, 1f))
        top.addView(moodTv, a.lp(WRAP, WRAP))
        root.addView(top, a.lp(MATCH, WRAP, m = 2))

        orb.setOnClickListener { if (AssistantBus.sessionOn) AssistantService.send(a, AssistantService.A_INTERRUPT) else a.startCall() }
        val maxH = (a.resources.displayMetrics.heightPixels * 0.38f).toInt()
        root.addView(orb, a.lp(MATCH, minOf(a.dp(a.prefs.homeOrbDp), maxH)))
        tilt.add(top, 0.3f).add(orb, 1.0f)

        val card = a.card()
        card.addView(status, a.lp(MATCH, WRAP))
        card.addView(timer, a.lp(MATCH, WRAP))
        card.addView(sub, a.lp(MATCH, WRAP, m = 2))
        root.addView(card, a.lp(MATCH, WRAP, m = 4))

        controls.addView(muteBtn, a.lp(0, WRAP, 1f, 3))
        controls.addView(intBtn, a.lp(0, WRAP, 1f, 3))
        controls.addView(endBtn, a.lp(0, WRAP, 1f, 3))
        controls.visibility = View.GONE
        root.addView(controls)

        val ask = a.hbox()
        val sugg = listOf(
            "📞 Who called?" to "missed calls", "💬 Messages" to "read notifications", "🖥 My screen" to "look at my screen what is on it",
            "▶ YouTube" to "open youtube", "🔋 Battery" to "battery info", "🩺 Fix yourself" to "fix yourself", "🔒 Lock" to "lock"
        )
        for ((label, cmd) in sugg) ask.addView(a.chip(label, false) {
            a.scope.launch { val r = Commands.run(a.host, cmd, false); if (r != null) { Feed.add((if (r.ok) "✔ " else "⚠ ") + r.message.take(90)); a.toast(r.message.take(160)) } }
        }, a.lp(WRAP, WRAP, m = 3))
        root.addView(android.widget.HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(ask) }, a.lp(MATCH, WRAP, m = 2))

        feedCard.addView(a.label("⚡ Live feed", 14f, Theme.CYAN, true))
        feedCard.addView(feedList)
        root.addView(feedCard, a.lp(MATCH, WRAP, m = 4))

        root.addView(a.label("👁  What should I watch while we talk?", 12f, Theme.MUTED), a.lp(MATCH, WRAP, m = 3))
        root.addView(visionRow, a.lp(MATCH, WRAP, m = 2))
        renderVision()

        root.addView(a.label("Tap the orb to start. I keep listening until you press End.", 12f, Theme.MUTED).apply { gravity = Gravity.CENTER }, a.lp(MATCH, WRAP, m = 6))
        root.addView(habitCard, a.lp(MATCH, WRAP, m = 4))
        habitCard.setOnClickListener { Habits.suggestion()?.let { c -> a.scope.launch { Commands.run(a.host, c) } } }
        root.addView(a.label("Created by ${Persona.CREATOR}  •  ${Persona.DEV_PHONE}", 11f, Theme.MUTED).apply {
            gravity = Gravity.CENTER; setPadding(0, a.dp(14), 0, 0)
            setOnClickListener { try { a.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + Persona.DEV_PHONE))) } catch (e: Exception) {} }
        })
        return a.scroll(root)
    }

    private fun setVision(mode: String) {
        val needCam = mode == "camera" || mode == "camera_front"
        if (needCam && !Perms.has(a, android.Manifest.permission.CAMERA)) {
            a.requestPerm(android.Manifest.permission.CAMERA) { g -> a.toast(if (g) "Camera allowed. Tap again." else "Camera permission denied.") }
            return
        }
        if (mode == "screen" && A11y.svc() == null) { a.toast(A11y.NEEDS); return }
        a.prefs.visionMode = mode
        if (AssistantBus.sessionOn) AssistantService.send(a, AssistantService.A_VISION)
        renderVision()
    }

    private fun renderVision() {
        visionRow.removeAllViews()
        val cur = a.prefs.visionMode
        fun add(label: String, mode: String) = visionRow.addView(a.chip(label, cur == mode) { setVision(mode) }, a.lp(0, WRAP, 1f, 2))
        add("Off", "off"); add("🖥 Screen", "screen"); add("📷 Back", "camera"); add("🤳 Front", "camera_front")
    }

    private fun render() {
        if (status.text != AssistantBus.status) status.text = AssistantBus.status
        val subTxt = AssistantBus.sub
        if (sub.text != subTxt) sub.text = subTxt
        sub.visibility = if (subTxt.isBlank()) View.GONE else View.VISIBLE
        controls.visibility = if (AssistantBus.sessionOn) View.VISIBLE else View.GONE
        muteBtn.text = if (AssistantBus.muted) "🎙 Unmute" else "🎙 Mute"
        val fresh = AssistantBus.mood != 0 && (System.currentTimeMillis() - AssistantBus.moodAt) < 9000L
        moodTv.text = if (fresh) Emotion.label(AssistantBus.mood) + " " + Emotion.name(AssistantBus.mood) else ""
        feedList.removeAllViews()
        for (line in Feed.items.take(5)) feedList.addView(a.label("• $line", 13f, Theme.TEXT).apply { setPadding(0, a.dp(2), 0, a.dp(2)) })
        feedCard.visibility = if (Feed.items.isEmpty()) View.GONE else View.VISIBLE
        if (AssistantBus.sessionOn && !ticking) { ticking = true; h.post(tick) }
    }

    fun pauseAnim() { tilt.stop() }

    override fun onShow() {
        if (!AssistantBus.listeners.contains(listener)) AssistantBus.listeners.add(listener)
        hello.text = greeting()
        render(); renderVision()
        tilt.strength = a.prefs.tiltPct / 100f * 1.6f
        if (!a.prefs.liteMode && a.prefs.tiltOn && a.prefs.tiltPct > 0) tilt.start()
        val hint = Habits.suggestion()
        habitCard.visibility = if (hint != null) View.VISIBLE else View.GONE
        if (hint != null) habitCard.text = "💡 You usually run “$hint” around now. Tap to do it."
    }

    override fun onHide() { AssistantBus.listeners.remove(listener); pauseAnim() }
}
