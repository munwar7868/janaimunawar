package com.munawar.ai

import android.app.AlertDialog
import android.view.View

class AboutScreen(private val a: MainActivity) : Screen {
    override val view: View = build()

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(28)) }
        root.addView(a.label("About & Privacy", 22f, Theme.CYAN, true))
        root.addView(a.label("Munawar AI v${Updater.currentVersion(a)}\nCreated by ${Persona.CREATOR}\n📞 ${Persona.DEV_PHONE}\nAn AI assistant: not a human, and it can make mistakes.", 13f, Theme.MUTED))

        root.addView(a.section("Your data"))
        root.addView(a.label(
            "• API keys (Gemini, ElevenLabs, Home Assistant) are encrypted with the Android Keystore.\n" +
                "• Chats and memories are private files on this phone; Android backup is disabled.\n" +
                "• Text, photos and files you send go to Google Gemini. Voice replies via ElevenLabs send the reply text there. Speech-to-text uses Android's speech service (may use Google servers).\n" +
                "• Microphone is used only in calls, voice typing, and \"Hey Munawar\" when you turn it on (a notification shows while it is on).\n" +
                "• Accessibility and Notification access are off until you enable them in Android Settings; they never record or save content.\n" +
                "• Never embed an API key in an APK: anyone can unzip it. Keys are typed by you, here.", 13f, Theme.MUTED))
        root.addView(a.button("Open Control Center (permissions)", Theme.CYAN) { a.show("Control") }, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Delete ALL app data", Theme.RED) {
            AlertDialog.Builder(a).setTitle("Delete everything?").setMessage("Removes API keys, settings, memories, chat history and usage counters, and stops background services.")
                .setPositiveButton("Delete") { _, _ ->
                    AssistantService.send(a, AssistantService.A_QUIT); FloatingService.stop(a)
                    a.memory.clear(); a.chatEngine.history.clear(); a.codeEngine.history.clear(); a.callEngine.history.clear()
                    a.prefs.wipeAll(); Stats.reset(); a.refreshWakeUi()
                    a.toast("All data deleted")
                }.setNegativeButton("Cancel", null).show()
        }, a.lp(MATCH, WRAP, m = 3))

        root.addView(a.section("What works"))
        root.addView(a.label(
            "Tap-the-orb Gemini Live conversation (continuous, hands-free, with fallback) • Gemini understands any phrasing/language and runs phone actions through a tool • open any installed app (WhatsApp, YouTube, Facebook…) • play YouTube songs • WhatsApp/SMS/calls/email by name or number • alarms, timers, in-app reminders • music keys, flashlight, volume • maps, nearby, location, websites • notes & tasks, daily briefing • photo/OCR/PDF tools, translate, study, writing, planner • accessibility tap/type/scroll/screenshot/read/explain screen • notification reading • Home Assistant smart home • memory with encrypted backup • floating orb (size in Settings) • “Hey Munawar” wake word • GitHub updates • usage status • SOS with location • answer/reject calls • missed calls • OTP on request • reply to notifications • parking saver • smart modes • missions • automation triggers & habit learning • sees your screen & camera (one-shot or live) • records video • YouTube skip ad / like / comment • brightness, rotation, Do-Not-Disturb, Quick-Settings toggles, power menu • Files & photos • connectors (GitHub, Telegram, Tavily, Groq/OpenAI/Claude) • website builder, deep research, game coach, phone health.", 13f, Theme.TEXT))
        root.addView(a.section("Known limits"))
        root.addView(a.label(
            "• Wake word uses battery, needs internet on most phones, and may beep.\n• Android blocks starting apps from the background unless 'Display over other apps' is allowed.\n• Android never lets apps self-update silently or send calls/SMS without your confirmation here.\n• On-device (offline) AI is not included; offline mode answers time, date and battery only. Screen vision sends screenshots to Google Gemini only while Vision is switched on or when you ask Munawar to look.", 13f, Theme.MUTED))
        return a.scroll(root)
    }
}
