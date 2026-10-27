package com.munawar.ai

import android.app.AlertDialog
import android.view.View
import android.widget.Switch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsScreen(private val a: MainActivity) : Screen {
    private val keyField = a.field("Paste Gemini API key", password = true)
    private val keyStatus = a.label("", 13f, Theme.MUTED)
    private val modelField = a.field("Model, e.g. gemini-2.5-flash")
    private val conn = a.label("Connection: not tested", 13f, Theme.MUTED)
    private val liveField = a.field("Live model")
    private val tempLabel = a.label("", 13f, Theme.MUTED)
    private val pitchLabel = a.label("", 13f, Theme.MUTED)
    private val rateLabel = a.label("", 13f, Theme.MUTED)
    private val elKey = a.field("ElevenLabs API key", password = true)
    private val elVoice = a.field("ElevenLabs Voice ID")
    private val elModel = a.field("ElevenLabs model (eleven_multilingual_v2)")
    private val elStatus = a.label("", 13f, Theme.MUTED)
    private val haUrl = a.field("Home Assistant URL, e.g. http://192.168.1.10:8123")
    private val haToken = a.field("Long-lived access token", password = true)
    private val haDevices = a.field("One per line:  bedroom light = light.bedroom", multi = true)
    private val haStatus = a.label("", 13f, Theme.MUTED)
    private val ccField = a.field("Default country code, e.g. 91")
    private val imgField = a.field("Image model, e.g. gemini-2.5-flash-image")
    private val repo = a.field("GitHub repo for updates: owner/name")
    override val view: View = build()

    private fun sw(text: String, on: Boolean, f: (Boolean) -> Unit) = Switch(a).apply {
        this.text = text; setTextColor(Theme.TEXT); isChecked = on; minimumHeight = a.dp(48)
        setOnCheckedChangeListener { _, v -> f(v) }
    }

    private fun build(): View {
        val p = a.prefs
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(28)) }
        root.addView(a.label("Settings", 22f, Theme.CYAN, true))

        // ---- Gemini
        root.addView(a.section("Gemini API key"))
        root.addView(a.label("Get a key at aistudio.google.com. Encrypted with the Android Keystore; never logged or hardcoded.", 12f, Theme.MUTED))
        root.addView(keyField, a.lp(MATCH, WRAP, m = 3))
        root.addView(keyStatus)
        val kr = a.hbox()
        kr.addView(a.button("Save / edit") {
            val k = keyField.text.toString().trim()
            if (k.length < 20) a.toast("That doesn't look like a valid key") else { p.saveApiKey(k); keyField.setText(""); refreshKey() }
        }, a.lp(0, WRAP, 1f, 2))
        kr.addView(a.button("Remove", Theme.RED) { p.removeApiKey(); refreshKey() }, a.lp(0, WRAP, 1f, 2))
        root.addView(kr)
        root.addView(a.button("Test connection", Theme.CYAN) { test() }, a.lp(MATCH, WRAP, m = 2))
        root.addView(conn)

        root.addView(a.section("Chat model (default: Gemini 3.5 Flash)"))
        modelField.setText(p.model)
        root.addView(modelField, a.lp(MATCH, WRAP, m = 3))
        val mr = a.hbox()
        mr.addView(a.button("Save model") { p.model = modelField.text.toString(); a.toast("Model: ${p.model}") }, a.lp(0, WRAP, 1f, 2))
        mr.addView(a.button("Load models", Theme.CYAN) { loadModels("generateContent") { m -> p.model = m; modelField.setText(m) } }, a.lp(0, WRAP, 1f, 2))
        root.addView(mr)
        root.addView(a.button("Pick from the model list (3.5 Flash is the default)", Theme.PURPLE) { presetDialog() }, a.lp(MATCH, WRAP, m = 2))
        root.addView(sw("Google Search grounding (live web info)", p.webSearch) { p.webSearch = it })

        // ---- Live
        root.addView(a.section("Gemini Live (real-time voice)"))
        liveField.setText(p.liveModel)
        root.addView(liveField, a.lp(MATCH, WRAP, m = 3))
        val lr = a.hbox()
        lr.addView(a.button("Save Live model") { p.liveModel = liveField.text.toString(); a.toast("Live model: ${p.liveModel}") }, a.lp(0, WRAP, 1f, 2))
        lr.addView(a.button("Load Live models", Theme.CYAN) { loadModels("bidiGenerateContent") { m -> p.liveModel = m; liveField.setText(m) } }, a.lp(0, WRAP, 1f, 2))
        root.addView(lr)
        root.addView(a.label("Live voice", 13f, Theme.MUTED))
        root.addView(a.radio(listOf("Aoede" to "Aoede (female)", "Kore" to "Kore (female)", "Leda" to "Leda (female)", "Zephyr" to "Zephyr (female)", "Puck" to "Puck (male)", "Charon" to "Charon (male)"), p.liveVoice) { p.liveVoice = it })
        root.addView(a.label("If Live can't connect, calls automatically fall back to classic mode and show the reason. Use 'Load Live models' to see what your key supports.", 12f, Theme.MUTED))

        // ---- Personality
        root.addView(a.section("Personality"))
        tempLabel.text = "Creativity (temperature): %.1f".format(p.temperature)
        root.addView(tempLabel)
        root.addView(a.seek(20, (p.temperature * 10).toInt()) { p.temperature = it / 10f; tempLabel.text = "Creativity (temperature): %.1f".format(p.temperature) })
        root.addView(a.label("Mode", 13f, Theme.MUTED))
        root.addView(a.radio(listOf("girlfriend" to "💕 Girlfriend", "friend" to "🤝 Friend", "serious" to "🎯 Serious"), p.mode) { p.mode = it })
        root.addView(a.label("Language", 13f, Theme.MUTED))
        root.addView(a.radio(listOf("hinglish" to "Hinglish (default)", "hindi" to "Hindi", "english" to "English"), p.language) { p.language = it })
        val nameF = a.field("Your name (optional)"); nameF.setText(p.userName)
        val perF = a.field("Extra personality instructions (optional)", multi = true); perF.setText(p.customPersona)
        root.addView(nameF, a.lp(MATCH, WRAP, m = 3))
        root.addView(perF, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save personality text") { p.userName = nameF.text.toString().trim(); p.customPersona = perF.text.toString().trim(); a.toast("Saved") })

        // ---- Voice
        root.addView(a.section("Voice (phone TTS)"))
        root.addView(sw("Speak replies in Chat", p.speak) { p.speak = it })
        pitchLabel.text = "Pitch: %.2f".format(p.pitch); rateLabel.text = "Speed: %.2f".format(p.rate)
        root.addView(pitchLabel)
        root.addView(a.seek(20, ((p.pitch - 0.5f) * 20).toInt()) { p.pitch = 0.5f + it / 20f; pitchLabel.text = "Pitch: %.2f".format(p.pitch) })
        root.addView(rateLabel)
        root.addView(a.seek(20, ((p.rate - 0.5f) * 20).toInt()) { p.rate = 0.5f + it / 20f; rateLabel.text = "Speed: %.2f".format(p.rate) })
        val vr = a.hbox()
        vr.addView(a.button("Pick voice", Theme.CYAN) { pickVoice() }, a.lp(0, WRAP, 1f, 2))
        vr.addView(a.button("Test voice") { a.speaker.speak("Hi, main Munawar AI hoon. Kaisi ho?") }, a.lp(0, WRAP, 1f, 2))
        root.addView(vr)

        // ---- ElevenLabs
        root.addView(a.section("ElevenLabs voice (optional)"))
        root.addView(a.label("Natural voice for replies in Chat/Tools/classic calls (Live uses Gemini's own voice). Key is encrypted. If ElevenLabs fails, the phone voice is used.", 12f, Theme.MUTED))
        root.addView(sw("Use ElevenLabs for spoken replies", p.elevenEnabled) { p.elevenEnabled = it })
        root.addView(elKey, a.lp(MATCH, WRAP, m = 3))
        elVoice.setText(p.elevenVoiceId); elModel.setText(p.elevenModel)
        root.addView(elVoice, a.lp(MATCH, WRAP, m = 3))
        root.addView(elModel, a.lp(MATCH, WRAP, m = 3))
        val er = a.hbox()
        er.addView(a.button("Save") {
            val k = elKey.text.toString().trim()
            if (k.isNotEmpty()) p.saveSecret("eleven", k)
            p.elevenVoiceId = elVoice.text.toString(); p.elevenModel = elModel.text.toString().ifBlank { "eleven_multilingual_v2" }
            elKey.setText(""); refreshEl()
        }, a.lp(0, WRAP, 1f, 2))
        er.addView(a.button("Remove key", Theme.RED) { p.removeSecret("eleven"); refreshEl() }, a.lp(0, WRAP, 1f, 2))
        er.addView(a.button("Test", Theme.CYAN) { testEleven() }, a.lp(0, WRAP, 1f, 2))
        root.addView(er)
        root.addView(elStatus)

        // ---- Smart home
        root.addView(a.section("Smart home (Home Assistant)"))
        root.addView(a.label("Works with a Home Assistant server on your network. Then say “bedroom light on/off” or “bedroom light chalu/band”. The token is encrypted.", 12f, Theme.MUTED))
        haUrl.setText(p.haUrl); haDevices.setText(p.haDevices)
        root.addView(haUrl, a.lp(MATCH, WRAP, m = 3))
        root.addView(haToken, a.lp(MATCH, WRAP, m = 3))
        root.addView(haDevices, a.lp(MATCH, WRAP, m = 3))
        val hr = a.hbox()
        hr.addView(a.button("Save") {
            p.haUrl = haUrl.text.toString(); p.haDevices = haDevices.text.toString()
            val t = haToken.text.toString().trim(); if (t.isNotEmpty()) p.saveSecret("ha", t)
            haToken.setText(""); refreshHa()
        }, a.lp(0, WRAP, 1f, 2))
        hr.addView(a.button("Remove token", Theme.RED) { p.removeSecret("ha"); refreshHa() }, a.lp(0, WRAP, 1f, 2))
        root.addView(hr)
        root.addView(haStatus)

        // ---- Floating orb / messaging / performance
        root.addView(a.section("Floating orb"))
        val szLabel = a.label("Size: ${p.floatSize} dp", 13f, Theme.MUTED)
        root.addView(szLabel)
        root.addView(a.seek(144, p.floatSize - 56) { p.floatSize = 56 + it; szLabel.text = "Size: ${p.floatSize} dp" }.also { sb ->
            sb.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, v: Int, u: Boolean) { if (u) { p.floatSize = 56 + v; szLabel.text = "Size: ${p.floatSize} dp" } }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) { FloatingService.refresh(a) }
            })
        })
        root.addView(a.label("Show/hide the floating orb in ☰ → Control Center.", 12f, Theme.MUTED))

        root.addView(a.section("Messaging & calls"))
        val csLabel = a.label("Cancel window: ${if (p.confirmSeconds == 0) "off (act instantly)" else "${p.confirmSeconds} s"}", 13f, Theme.MUTED)
        root.addView(csLabel)
        root.addView(a.seek(15, p.confirmSeconds) { p.confirmSeconds = it; csLabel.text = "Cancel window: ${if (it == 0) "off (act instantly)" else "$it s"}" })
        root.addView(a.label("Before Munawar calls or sends an SMS/WhatsApp it shows the number and message with a Cancel button. If you don't cancel within this time it goes ahead. 0 = no pause (instant).", 12f, Theme.MUTED))
        ccField.setText(p.countryCode)
        root.addView(ccField, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save country code") { p.countryCode = ccField.text.toString(); a.toast("Saved: +${p.countryCode}") })

        // ---- Companion & autopilot
        root.addView(a.section("🤝 Companion & autopilot"))
        root.addView(a.label("Munawar tells you about calls and messages by itself, starts a chat now and then, watches the screen while you talk, and looks after the phone.", 12f, Theme.MUTED))
        root.addView(a.label("Announce calls and messages", 13f, Theme.MUTED))
        root.addView(a.radio(listOf("always" to "Always (speaks aloud even when we are not talking)", "session" to "Only while we are in a conversation", "off" to "Never"), p.announceMode) { p.announceMode = it })
        fun chatText() = if (p.chatEveryMin == 0) "Start a chat by itself: never" else "Start a chat by itself after ${p.chatEveryMin} min of quiet"
        val chatL = a.label(chatText(), 13f, Theme.MUTED)
        root.addView(chatL)
        root.addView(a.seek(240, p.chatEveryMin) { p.chatEveryMin = it; chatL.text = chatText() })
        root.addView(sw("Skip YouTube ads automatically", p.autoSkipAds) { p.autoSkipAds = it })
        root.addView(sw("Tap “Allow” on Android permission pop-ups for me", p.autoGrant) { p.autoGrant = it })
        root.addView(sw("Keep myself alive (restart the listener if Android kills it)", p.keepAlive) { p.keepAlive = it; if (it) Watchdog.schedule(a) })
        val mF = a.field("Morning routine time, e.g. 07:30 (blank = off)"); mF.setText(p.routineMorning)
        val nF = a.field("Night routine time, e.g. 22:30 (blank = off)"); nF.setText(p.routineNight)
        root.addView(mF, a.lp(MATCH, WRAP, m = 3)); root.addView(nF, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save routines") {
            fun setRoutine(id: Long, kind: String, t: String) {
                Triggers.remove(a, id)
                if (Regex("^\\d{1,2}:\\d{2}$").matches(t)) Triggers.add(a, Trigger(id, "time", t, "run", "routine $kind", true))
            }
            p.routineMorning = mF.text.toString(); p.routineNight = nF.text.toString()
            setRoutine(7001L, "morning", p.routineMorning); setRoutine(7002L, "night", p.routineNight)
            a.toast("Routines saved")
        })
        root.addView(a.button("🩺 Run self-check and fix now", Theme.CYAN) { a.scope.launch { val r = Doctor.run(a.host); a.toast(r.message.take(400)) } }, a.lp(MATCH, WRAP, m = 3))

        // ---- Sound & wake word
        root.addView(a.section("🔊 Sound & wake word"))
        root.addView(sw("Loud speaker: raise the media volume while Munawar talks", p.loudSpeaker) { p.loudSpeaker = it })
        val gainL = a.label("Voice loudness boost: ${p.voiceGainPct} %", 13f, Theme.MUTED)
        root.addView(gainL)
        root.addView(a.seek(200, p.voiceGainPct - 100) { p.voiceGainPct = 100 + it; gainL.text = "Voice loudness boost: ${p.voiceGainPct} %" })
        root.addView(sw("Let me interrupt Munawar by voice while it speaks (may echo on the loud speaker)", p.voiceInterrupt) { p.voiceInterrupt = it })
        root.addView(sw("Silence the wake-word “ding” sound", p.wakeBeepFix) { p.wakeBeepFix = it; if (!it) Hush.off(a) })

        // ---- Vision
        root.addView(a.section("👁 Vision (screen & camera)"))
        root.addView(a.label("What Munawar watches live while you talk. Screen needs Accessibility; camera needs the Camera permission. You can always say “look at my screen” or “look at camera” instead.", 12f, Theme.MUTED))
        root.addView(a.radio(listOf("off" to "Off (look only when I ask)", "screen" to "Watch my screen", "camera" to "Back camera", "camera_front" to "Front camera"), p.visionMode) {
            p.visionMode = it
            if (AssistantBus.sessionOn) AssistantService.send(a, AssistantService.A_VISION)
        })

        // ---- Features on/off
        root.addView(a.section("🧩 Features on/off"))
        root.addView(a.label("Switch any group of commands off. A switched-off group refuses its commands.", 12f, Theme.MUTED))
        for ((id, label) in Features.groups) root.addView(sw(label, Features.enabled(id)) { Features.set(id, it) })

        // ---- Appearance (UI 2.0): everything is customizable
        root.addView(a.section("🎨 Appearance (UI 2.0)"))
        root.addView(a.label("Change anything here, then tap “Apply look” to rebuild the screens. Orb, edge-light and 3D options update right away.", 12f, Theme.MUTED))
        fun slider(title: String, unit: String, min: Int, max: Int, cur: Int, set: (Int) -> Unit) {
            val l = a.label("$title: $cur$unit", 13f, Theme.MUTED)
            root.addView(l)
            root.addView(a.seek(max - min, cur - min) { v -> set(min + v); l.text = "$title: ${min + v}$unit" })
        }
        root.addView(a.label("Colour theme", 14f, Theme.CYAN, true))
        root.addView(a.radio(Theme.names, p.uiTheme) { p.uiTheme = it; p.accentHex = ""; p.bgHex = "" })
        val accF = a.field("Custom accent colour, e.g. #FF5722 (blank = theme)"); accF.setText(p.accentHex)
        root.addView(accF, a.lp(MATCH, WRAP, m = 3))
        val bgF = a.field("Custom background colour, e.g. #000000 (blank = theme)"); bgF.setText(p.bgHex)
        root.addView(bgF, a.lp(MATCH, WRAP, m = 3))
        slider("Text size", " %", 70, 150, p.fontPct) { p.fontPct = it }
        slider("Corner roundness", " dp", 4, 32, p.cornerDp) { p.cornerDp = it }
        slider("Card opacity", " %", 40, 100, p.cardAlphaPct) { p.cardAlphaPct = it }
        root.addView(a.button("✔ Apply look", Theme.PURPLE) {
            val ac = accF.text.toString().trim(); val bg = bgF.text.toString().trim()
            val okAc = ac.isEmpty() || runCatching { android.graphics.Color.parseColor(if (ac.startsWith("#")) ac else "#$ac") }.isSuccess
            val okBg = bg.isEmpty() || runCatching { android.graphics.Color.parseColor(if (bg.startsWith("#")) bg else "#$bg") }.isSuccess
            if (!okAc || !okBg) { a.toast("Colour must look like #RRGGBB"); return@button }
            if (ac.isNotEmpty()) p.accentHex = ac
            if (bg.isNotEmpty()) p.bgHex = bg
            Theme.apply(p); a.recreate()
        }, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("↺ Reset appearance to default", Theme.CARD) { p.resetAppearance(); Theme.apply(p); a.recreate() }, a.lp(MATCH, WRAP, m = 2))

        root.addView(a.label("Card style", 14f, Theme.CYAN, true))
        root.addView(a.radio(listOf("glass" to "Glass (default)", "neon" to "Neon glow", "solid" to "Solid", "minimal" to "Minimal"), p.cardStyle) { p.cardStyle = it })
        root.addView(a.label("UI animation", 14f, Theme.CYAN, true))
        root.addView(a.radio(listOf("0" to "Off", "1" to "Smooth (default)", "2" to "Full (bouncy, 3D)"), p.uiAnim.toString()) { p.uiAnim = it.toInt() })
        root.addView(sw("Munawar greets me by itself when a conversation starts", p.autoGreet) { p.autoGreet = it })
        root.addView(a.label("Home & 3D", 14f, Theme.CYAN, true))
        root.addView(sw("Tilt-parallax 3D (cards move when you tilt the phone)", p.tiltOn) { p.tiltOn = it })
        slider("3D tilt strength", " %", 0, 100, p.tiltPct) { p.tiltPct = it }
        slider("Home orb size", " dp", 220, 440, p.homeOrbDp) { p.homeOrbDp = it }
        root.addView(sw("Moving glow in the background (off = clean still background)", p.bgAnim) { p.bgAnim = it })

        root.addView(a.label("Orb details (used when animation is Calm)", 14f, Theme.CYAN, true))
        slider("Orb animation speed", " %", 30, 250, p.orbSpeedPct) { p.orbSpeedPct = it; a.orbs.forEach { o -> o.reloadTheme() } }
        root.addView(sw("Audio bars around the orb", p.orbBars) { p.orbBars = it; a.orbs.forEach { o -> o.reloadTheme() } })
        root.addView(sw("Orbiting sparkles", p.orbSparkles) { p.orbSparkles = it; a.orbs.forEach { o -> o.reloadTheme() } })
        root.addView(sw("3D orbit rings", p.orbRings) { p.orbRings = it; a.orbs.forEach { o -> o.reloadTheme() } })

        root.addView(a.label("Screen-edge colours & glow", 14f, Theme.CYAN, true))
        root.addView(a.radio(listOf("orb" to "Same as the orb style", "rainbow" to "Rainbow", "accent" to "My accent colours"), p.edgeColors) { p.edgeColors = it; EdgeLight.refreshPrefs() })
        slider("Edge glow strength", " %", 30, 200, p.edgeGlowPct) { p.edgeGlowPct = it; EdgeLight.refreshPrefs() }

        root.addView(a.section("Screen-edge light"))
        root.addView(sw("Colourful light around the screen while Munawar is on (off by default)", p.edgeLight) { p.edgeLight = it; EdgeLight.refreshPrefs() })
        root.addView(sw("Also show it over other apps (needs “Display over other apps”)", p.edgeOverlay) { p.edgeOverlay = it; EdgeLight.refreshPrefs() })
        root.addView(a.label("Animation style", 13f, Theme.MUTED))
        root.addView(a.radio(listOf("run" to "Running light", "pulse" to "Pulse", "comet" to "Comet", "dual" to "Dual colour", "snake" to "Twin snakes", "heartbeat" to "Heartbeat", "wave" to "Wave", "rainbow" to "Fast rainbow", "static" to "Static"), p.edgeStyle) { p.edgeStyle = it; EdgeLight.refreshPrefs() })
        val etLabel = a.label("Thickness: ${p.edgeThickness} dp", 13f, Theme.MUTED)
        root.addView(etLabel)
        root.addView(a.seek(12, p.edgeThickness - 2) { p.edgeThickness = 2 + it; etLabel.text = "Thickness: ${p.edgeThickness} dp"; EdgeLight.refreshPrefs() })
        val esLabel = a.label("Speed: ${p.edgeSpeed} / 4", 13f, Theme.MUTED)
        root.addView(esLabel)
        root.addView(a.seek(3, p.edgeSpeed - 1) { p.edgeSpeed = 1 + it; esLabel.text = "Speed: ${p.edgeSpeed} / 4"; EdgeLight.refreshPrefs() })
        root.addView(a.label("Shows over every app when 'Display over other apps' is allowed (Control Center), otherwise only inside Munawar. Adapts to your phone's screen corners.", 12f, Theme.MUTED))

        root.addView(a.section("Performance"))
        root.addView(sw("Lite animations (smoother on older phones; restart app)", p.liteMode) { p.liteMode = it })

        // ---- Updates / usage

        // ---- Safety & SOS
        root.addView(a.section("Safety & SOS"))
        root.addView(a.label("“sos” sends your location by SMS to these numbers (after the cancel window). Separate numbers with commas, include the country code.", 12f, Theme.MUTED))
        val sosF = a.field("Emergency numbers, e.g. +923001234567, +923331234567"); sosF.setText(p.sosNumbers)
        root.addView(sosF, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save emergency contacts") { p.sosNumbers = sosF.text.toString().trim(); a.toast("Saved") })
        root.addView(sw("Allow “read otp” (an OTP is spoken only when you ask)", p.otpReadAloud) { p.otpReadAloud = it })
        root.addView(a.label("Voice passphrase lock: when set, calls, messages, SOS, OTP, PC and delete commands work only after you say “unlock <phrase>”. Leave blank to turn off.", 12f, Theme.MUTED))
        val phF = a.field("Secret phrase, e.g. blue falcon"); phF.setText(p.secPhrase)
        root.addView(phF, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save passphrase") { p.secPhrase = phF.text.toString(); a.toast(if (p.secPhrase.isEmpty()) "Voice lock off" else "Voice lock on") })
        val unl = a.label("Stay unlocked for: ${p.secUnlockMin} min", 13f, Theme.MUTED)
        root.addView(unl)
        root.addView(a.seek(119, p.secUnlockMin - 1) { p.secUnlockMin = 1 + it; unl.text = "Stay unlocked for: ${p.secUnlockMin} min" })

        // ---- Orb design
        root.addView(a.section("Orb design (${OrbDesigns.total()} styles)"))
        val designLbl = a.label("Design #${OrbDesigns.number(p.orbPal, p.orbForm)} of ${OrbDesigns.total()}", 14f, Theme.TEXT, true)
        fun reloadOrbs() { designLbl.text = "Design #${OrbDesigns.number(p.orbPal, p.orbForm)} of ${OrbDesigns.total()}"; a.orbs.forEach { o -> o.reloadTheme() } }
        root.addView(designLbl)
        root.addView(a.label("Colour (10)", 13f, Theme.MUTED))
        root.addView(a.radio(OrbDesigns.paletteNames.mapIndexed { i, n -> i.toString() to n }, p.orbPal.toString()) { p.orbPal = it.toInt(); reloadOrbs(); EdgeLight.refreshPrefs() })
        root.addView(a.label("Form (${OrbDesigns.formNames.size})", 13f, Theme.MUTED))
        root.addView(a.radio(OrbDesigns.formNames.mapIndexed { i, n -> i.toString() to n }, p.orbForm.toString()) { p.orbForm = it.toInt(); reloadOrbs() })
        root.addView(a.label("Animation", 13f, Theme.MUTED))
        root.addView(a.radio(listOf("0" to "Off (still orb, default)", "1" to "Calm", "2" to "Full (bars, rings, sparkles)"), p.orbAnim.toString()) { p.orbAnim = it.toInt(); reloadOrbs() })

        // ---- Connectors
        root.addView(a.section("Connectors (optional)"))
        root.addView(a.label("Keys are typed by you and stored encrypted. They unlock: “github repos”, “telegram …”, “research …” (Tavily), “ask groq/openai/claude …”.", 12f, Theme.MUTED))
        fun secretRow(label: String, name: String) {
            val f = a.field(label, password = true)
            root.addView(f, a.lp(MATCH, WRAP, m = 3))
            val r = a.hbox()
            r.addView(a.button("Save") { val v = f.text.toString().trim(); if (v.isNotEmpty()) { p.saveSecret(name, v); f.setText(""); a.toast("Saved") } }, a.lp(0, WRAP, 1f, 2))
            r.addView(a.button("Remove", Theme.RED) { p.removeSecret(name); a.toast("Removed") }, a.lp(0, WRAP, 1f, 2))
            root.addView(r)
        }
        val ghUser = a.field("GitHub username"); ghUser.setText(p.githubUser)
        root.addView(ghUser, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save GitHub username") { p.githubUser = ghUser.text.toString(); a.toast("Saved") })
        secretRow("GitHub token (optional, for private repos)", "github")
        val tgChat = a.field("Telegram chat id"); tgChat.setText(p.telegramChat)
        root.addView(tgChat, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save Telegram chat id") { p.telegramChat = tgChat.text.toString(); a.toast("Saved") })
        secretRow("Telegram bot token", "telegram")
        secretRow("Tavily API key (deep research)", "tavily")
        secretRow("Groq API key", "groq")
        secretRow("OpenAI API key", "openai")
        secretRow("Claude (Anthropic) API key", "claude")
        val altModelF = a.field("Model for other AIs (blank = default)"); altModelF.setText(p.altModel)
        root.addView(altModelF, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save model name") { p.altModel = altModelF.text.toString(); a.toast("Saved") })

        // ---- Developer
        root.addView(a.section("Developer"))
        root.addView(a.label("Munawar AI v${Updater.currentVersion(a)}\nCreated by ${Persona.CREATOR}\n📞 ${Persona.DEV_PHONE}", 13f, Theme.TEXT))

        root.addView(a.section("Updates"))
        repo.setText(p.updateRepo)
        root.addView(repo, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save repo") { p.updateRepo = repo.text.toString(); a.toast("Saved") })
        root.addView(a.section("Usage"))
        root.addView(a.button("Reset usage counters", Theme.CARD) { Stats.reset(); a.toast("Reset") })

        refreshKey(); refreshEl(); refreshHa()
        group(root)
        return a.scroll(root)
    }

    private fun refreshKey() {
        keyStatus.text = if (a.prefs.hasApiKey()) "✔ A key is saved (hidden). Paste a new one and tap Save to replace it." else "No key saved."
        conn.text = "Connection: not tested"
    }

    private fun refreshEl() {
        val has = a.prefs.secret("eleven") != null
        elStatus.text = (if (has) "✔ ElevenLabs key saved. " else "No ElevenLabs key. ") + (if (a.prefs.elevenVoiceId.isBlank()) "Voice ID missing." else "Voice ID set.")
    }

    private fun refreshHa() {
        val has = a.prefs.secret("ha") != null
        haStatus.text = (if (has) "✔ Token saved. " else "No token. ") + "${SmartHome.devices(a.prefs).size} device(s) mapped."
    }

    private fun test() {
        val k = a.prefs.apiKey()
        if (k == null) { conn.text = "⚠ Save an API key first"; return }
        conn.text = "Testing…"
        a.scope.launch {
            try { Gemini.test(k, a.prefs.model); conn.text = "✔ Connected. Model ${a.prefs.model} responded." }
            catch (e: Exception) { conn.text = "✖ " + (e.message ?: "Failed") }
        }
    }

    private fun testEleven() {
        val k = a.prefs.secret("eleven")
        if (k == null || a.prefs.elevenVoiceId.isBlank()) { elStatus.text = "⚠ Save the key and the Voice ID first."; return }
        elStatus.text = "Testing…"
        a.scope.launch {
            try {
                withContext(Dispatchers.IO) { ElevenLabs.tts(k, a.prefs.elevenVoiceId, a.prefs.elevenModel, "Hello") }
                elStatus.text = "✔ ElevenLabs works. Turn on 'Use ElevenLabs' to hear it."
                if (a.prefs.elevenEnabled) a.speaker.speak("Hi, main Munawar AI hoon.")
            } catch (e: Exception) { elStatus.text = "✖ " + (e.message ?: "Failed") }
        }
    }

    private fun loadModels(method: String, onPick: (String) -> Unit) {
        val k = a.prefs.apiKey()
        if (k == null) { a.toast("Save an API key first"); return }
        a.toast("Loading models…")
        a.scope.launch {
            try {
                val models = Gemini.listModels(k, method)
                if (models.isEmpty()) { a.toast("No models returned for this key"); return@launch }
                AlertDialog.Builder(a).setTitle("Choose model")
                    .setItems(models.toTypedArray()) { _, i -> onPick(models[i]) }.show()
            } catch (e: Exception) { a.toast(e.message ?: "Failed") }
        }
    }

    /** Puts the flat list of sections into 5 collapsible categories. */
    private fun group(root: android.widget.LinearLayout) {
        val order = listOf("🤖  AI & voice", "📱  Phone & apps", "🎨  Appearance & orb", "🛡  Safety & connectors", "⚙  System")
        fun catOf(t: String): Int = when {
            t.contains("Sound") || t.contains("Gemini") || t.contains("model") || t.contains("Personality") || t.contains("Voice") || t.contains("ElevenLabs") || t.contains("Image generation") -> 0
            t.contains("Messaging") || t.contains("Smart home") || t.contains("Floating") || t.contains("Vision") || t.contains("Features on") || t.contains("Companion") -> 1
            t.contains("Appearance") || t.contains("Orb") || t.contains("edge") -> 2
            t.contains("Safety") || t.contains("Connectors") -> 3
            else -> 4
        }
        val kids = (0 until root.childCount).map { root.getChildAt(it) }
        root.removeAllViews()
        val boxes = order.map { a.vbox().apply { visibility = View.GONE; setPadding(a.dp(4), 0, a.dp(4), a.dp(10)) } }
        var cur = -1
        for (k in kids) {
            if (k.tag == "section" && k is android.widget.TextView) cur = catOf(k.text.toString())
            if (cur < 0) root.addView(k) else boxes[cur].addView(k)
        }
        order.forEachIndexed { i, title ->
            val holder = arrayOfNulls<android.widget.Button>(1)
            val b = a.button("▸  $title", Theme.CARD) {
                val open = boxes[i].visibility != View.VISIBLE
                boxes[i].visibility = if (open) View.VISIBLE else View.GONE
                holder[0]?.text = (if (open) "▾  " else "▸  ") + title
            }
            holder[0] = b
            b.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            root.addView(b, a.lp(MATCH, WRAP, m = 3))
            root.addView(boxes[i])
        }
    }

    private fun presetDialog() {
        val presets = Prefs.MODEL_PRESETS
        AlertDialog.Builder(a).setTitle("Choose chat model")
            .setItems(presets.map { it.second }.toTypedArray()) { _, i ->
                a.prefs.model = presets[i].first; modelField.setText(presets[i].first); a.toast("Model: ${a.prefs.model}")
            }.show()
    }

    private fun pickVoice() {
        val v = a.speaker.voiceNames()
        if (v.isEmpty()) { a.toast("No Hindi/English voices found. Install one in Android TTS settings."); return }
        AlertDialog.Builder(a).setTitle("Choose voice")
            .setItems(v.toTypedArray()) { _, i -> a.prefs.voiceName = v[i]; a.speaker.speak("Hi, main Munawar AI hoon.") }.show()
    }
}
