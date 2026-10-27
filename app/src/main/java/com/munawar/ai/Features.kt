package com.munawar.ai

/** Every feature group has its own on/off switch (Settings → Features on/off). A switched-off group refuses its commands. */
object Features {
    val groups = listOf(
        "comms" to "📞 Calls, SMS, WhatsApp, email, SOS",
        "media" to "🎵 Music, media keys, volume",
        "device" to "🔧 Device (alarm, torch, brightness, Wi-Fi, lock…)",
        "files" to "📁 Files & photos",
        "vision" to "👁 Screen & camera vision, video recording",
        "apps" to "▶ App control (YouTube skip ad / like / comment, taps, swipes)",
        "notifs" to "🔔 Notifications, OTP, missed calls",
        "maps" to "🗺 Maps, parking, smart modes",
        "missions" to "🎯 Missions",
        "connectors" to "🔌 Connectors (GitHub, Telegram, research)",
        "agent" to "🤖 Autonomous agent (do anything on screen)"
    )

    private val rules = listOf(
        "comms" to Regex("^(?:call|dial|phone|sms|text|message|whatsapp|email|mail|sos|emergency|answer|reject|decline|hang ?up|end call|lookup|send|telegram)\\b"),
        "media" to Regex("^(?:play|pause|resume|next song|previous song|skip song|volume|mute|unmute|music)\\b"),
        "device" to Regex("^(?:set alarm|alarm|timer|flashlight|torch|battery|storage|brightness|auto[- ]?rotate|do not disturb|dnd|vibrate|wifi|wi-fi|bluetooth|hotspot|airplane|nfc|location|mobile data|turn (?:on|off)|quick settings|power menu|lock|copy|clipboard)\\b"),
        "files" to Regex("^(?:open files|file manager|gallery|delete photos|zip|share files|delete files)\\b"),
        "vision" to Regex("^(?:look at|see |watch |watching|what do you see|what's on my screen|whats on my screen|record video|start recording|stop recording|take (?:a )?(?:photo|picture|selfie|video)|selfie|vision|stop watching)\\b"),
        "apps" to Regex("^(?:skip (?:the )?ads?|like|dislike|subscribe|comment|write comment|post comment|comments|next video|previous video|full ?screen|seek|forward|rewind|swipe|double tap|long press|tap|click|type)\\b"),
        "notifs" to Regex("^(?:read (?:my )?notifications|read otp|otp|clear notifications|reply|missed calls)\\b"),
        "maps" to Regex("^(?:navigate|directions|save parking|find my parking|traffic|where am i|nearby|restaurants|driving mode|sleep mode|work mode|game mode)\\b|\\bnear me\\b"),
        "missions" to Regex("^(?:start mission|pause mission|resume mission|cancel mission|mission)\\b"),
        "connectors" to Regex("^(?:github|telegram|research|deep research|ask (?:groq|openai|chatgpt|claude))\\b"),
        "agent" to Regex("^(?:do(?! not)|agent|autopilot|task:)\\b")
    )

    fun enabled(id: String): Boolean = try { !Core.prefs.offGroups.split(",").contains(id) } catch (e: Exception) { true }

    fun set(id: String, on: Boolean) {
        val cur = Core.prefs.offGroups.split(",").filter { it.isNotBlank() }.toMutableSet()
        if (on) cur.remove(id) else cur.add(id)
        Core.prefs.offGroups = cur.joinToString(",")
    }

    fun blocked(raw: String): Automation.Result? {
        val l = raw.lowercase().trim().trimEnd('.', '!', '?')
        for ((id, re) in rules) {
            if (re.containsMatchIn(l) && !enabled(id)) {
                val label = groups.firstOrNull { it.first == id }?.second ?: id
                return Automation.Result(false, "$label is switched off. Turn it on in ☰ → Settings → Features on/off.")
            }
        }
        return null
    }
}
