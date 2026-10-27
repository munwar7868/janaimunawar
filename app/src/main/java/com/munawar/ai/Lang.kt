package com.munawar.ai

/** Turns Hindi (Devanagari) speech-recognizer output and short forms ("yt", "fb") into plain Roman command words. */
object Lang {
    private val dev = linkedMapOf(
        // verbs
        "खोल दो" to "open", "खोल दे" to "open", "खोलिए" to "open", "खोलना" to "open", "खोलो" to "open", "खोल" to "open", "ओपन" to "open",
        "चला दो" to "play", "चलाओ" to "play", "चला" to "play", "बजाओ" to "play", "बजा" to "play", "प्ले" to "play", "सुनाओ" to "play", "सुना" to "play",
        "भेजो" to "send", "भेज दो" to "send", "भेज" to "send", "लिखो" to "likho", "बोलो" to "saying", "कहो" to "saying",
        "फिर" to "phir", "उसके बाद" to "uske baad", "और फिर" to "aur phir",
        // apps
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वाट्सएप" to "whatsapp", "वाट्सऐप" to "whatsapp", "व्हाट्स एप" to "whatsapp", "वॉट्सऐप" to "whatsapp", "वॉट्सएप" to "whatsapp",
        "यूट्यूब" to "youtube", "यू ट्यूब" to "youtube", "यूटयूब" to "youtube", "फेसबुक" to "facebook", "इंस्टाग्राम" to "instagram", "इंस्टा" to "instagram",
        "टेलीग्राम" to "telegram", "क्रोम" to "chrome", "कैमरा" to "camera", "गैलरी" to "gallery", "सेटिंग्स" to "settings", "सेटिंग" to "settings",
        "जीमेल" to "gmail", "मैप्स" to "maps", "मैप" to "maps", "स्नैपचैट" to "snapchat", "ट्विटर" to "twitter", "नेटफ्लिक्स" to "netflix",
        "स्पॉटिफाई" to "spotify", "कैलकुलेटर" to "calculator", "फोनपे" to "phonepe", "पेटीएम" to "paytm", "अमेज़न" to "amazon", "अमेजन" to "amazon", "फ्लिपकार्ट" to "flipkart",
        "प्ले स्टोर" to "play store", "गूगल" to "google", "ब्राउज़र" to "browser", "ब्राउजर" to "browser", "घड़ी" to "clock",
        // things
        "अलार्म" to "alarm", "टाइमर" to "timer", "कॉल" to "call", "एसएमएस" to "sms", "मैसेज" to "message", "ईमेल" to "email",
        "गाने" to "song", "गाना" to "song", "गीत" to "song", "सॉन्ग" to "song", "संगीत" to "music", "म्यूजिक" to "music", "वीडियो" to "video",
        "मिनट" to "minutes", "घंटे" to "hours", "घंटा" to "hours", "बजे" to "baje", "वाईफाई" to "wifi", "वाई-फाई" to "wifi", "ब्लूटूथ" to "bluetooth",
        "टॉर्च" to "flashlight", "फ्लैशलाइट" to "flashlight", "वॉल्यूम" to "volume", "आवाज़" to "volume", "आवाज" to "volume",
        "बढ़ाओ" to "up", "घटाओ" to "down", "म्यूट" to "mute", "बंद" to "off", "चालू" to "on", "ऑन" to "on", "ऑफ" to "off",
        "वापस जाओ" to "go back", "पीछे जाओ" to "go back", "होम जाओ" to "go home", "नोटिफिकेशन" to "notification", "पढ़ो" to "read", "स्क्रीन" to "screen",
        "नोट" to "note", "टास्क" to "task", "याद दिलाओ" to "remind me", "रिमाइंडर" to "reminder", "स्क्रॉल" to "scroll", "नीचे" to "down", "ऊपर" to "up",
        "रुको" to "stop", "रोको" to "pause", "अगला" to "next", "पिछला" to "previous"
    )
    private val devKeys = dev.keys.sortedByDescending { it.length }

    private val roman = listOf(
        Regex("(?i)\\byt\\b") to "youtube", Regex("(?i)\\bfb\\b") to "facebook", Regex("(?i)\\binsta\\b") to "instagram",
        Regex("(?i)\\b(?:whats ?app|whatsap|watsapp|whatsup|whats up app|wa)\\b") to "whatsapp",
        Regex("(?i)\\b(?:you tube|yutube|utube)\\b") to "youtube", Regex("(?i)\\bgmail\\b") to "gmail"
    )

    fun normalize(s: String): String {
        var t = s.trim()
        for (k in devKeys) if (t.contains(k)) t = t.replace(k, " ${dev[k]} ")
        for ((re, v) in roman) t = re.replace(t, v)
        t = t.replace("।", " ").replace(Regex("[?!]+$"), "")
        return t.replace(Regex("\\s+"), " ").trim()
    }
}
