package com.munawar.ai

/**
 * Optional voice passphrase lock (Settings → Safety). When a phrase is set, risky commands (calls, messages, SOS, OTP, PC, delete…)
 * are refused until you say “unlock <phrase>”. It stays unlocked for the chosen number of minutes, or until “lock voice”.
 * This is a spoken password, not a voice-print.
 */
object Security {
    @Volatile private var unlockedUntil = 0L
    private val sensitive = Regex("^(?:call|dial|sms|text|whatsapp|email|mail|send|sos|emergency|shutdown|delete|read otp|otp|reply|telegram|answer|reject|hang ?up|end call|copy|save parking|start mission|mode|activate)\\b")

    fun gate(raw: String): Automation.Result? {
        val ph = try { Core.prefs.secPhrase } catch (e: Exception) { "" }
        if (ph.isEmpty()) return null
        val l = raw.lowercase().trim().trimEnd('.', '!', '?')
        val m = Regex("^unlock\\s+(.+)$").find(l)
        if (m != null) {
            return if (m.groupValues[1].trim() == ph) {
                unlockedUntil = System.currentTimeMillis() + Core.prefs.secUnlockMin * 60_000L
                Automation.Result(true, "🔓 Unlocked for ${Core.prefs.secUnlockMin} minutes.")
            } else Automation.Result(false, "Wrong phrase.")
        }
        if (l == "lock voice" || l == "relock") { unlockedUntil = 0L; return Automation.Result(true, "🔒 Voice lock is back on.") }
        if (System.currentTimeMillis() < unlockedUntil) return null
        if (sensitive.containsMatchIn(l)) return Automation.Result(false, "🔒 Locked. Say “unlock <your phrase>” first.")
        return null
    }
}
