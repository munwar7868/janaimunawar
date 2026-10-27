package com.munawar.ai

/** "Fix yourself": checks everything Munawar needs, repairs what it can and opens the Android screen for the rest. */
object Doctor {
    suspend fun run(a: Host): Automation.Result {
        val p = a.prefs
        val sb = StringBuilder("🩺 Self-check\n")
        val problems = ArrayList<String>()
        fun line(ok: Boolean, text: String) { sb.append(if (ok) "✔ " else "✖ ").append(text).append('\n'); if (!ok) problems.add(text) }
        line(p.hasApiKey(), "Gemini key")
        for (perm in Perms.allRuntime()) line(Perms.has(a, perm), "Permission " + perm.substringAfterLast('.'))
        line(Perms.overlay(a), "Display over other apps")
        line(Perms.notifAccess(a), "Notification access")
        line(A11y.svc() != null, "Accessibility (screen control and vision)")
        line(Admin.active(a), "Device admin (instant lock)")
        line(Perms.batteryIgnored(a), "Battery unrestricted (keeps me alive)")
        line(Perms.writeSettings(a), "System settings (brightness, rotation)")
        line(Perms.dndAccess(a), "Do-Not-Disturb access")
        line(AssistantService.inst != null, "Background service")

        val fixed = StringBuilder()
        if (!p.model.startsWith("gemini")) { p.model = "gemini-3.5-flash"; fixed.append("model reset to Gemini 3.5 Flash; ") }
        if (p.wakeEnabled && AssistantService.inst == null) { AssistantService.send(a, AssistantService.A_WAKE_ON); fixed.append("restarted the background service; ") }
        val next = Perms.nextSpecial(a)
        if (next != null) {
            try {
                a.startActivity(next.second)
                fixed.append("opened “${next.first}” (I will tap Allow for you if Accessibility is on); ")
                Guardian.assistGrant(a, next.first)
            } catch (e: Exception) {}
        }
        val missingRuntime = Perms.allRuntime().filter { !Perms.has(a, it) }
        if (missingRuntime.isNotEmpty()) {
            Guardian.expectPermission()
            a.requestAnyPerm(missingRuntime.toTypedArray()) { }
            fixed.append("asked for the missing permissions; ")
        }
        if (fixed.isNotEmpty()) sb.append("\nFixed: ").append(fixed)
        sb.append(if (problems.isEmpty()) "\nEverything is perfect. 🎉" else "\n${problems.size} thing(s) still need your tap above.")
        Feed.add(if (problems.isEmpty()) "🩺 Self-check: all good" else "🩺 Self-check: ${problems.size} issue(s)")
        return Automation.Result(problems.isEmpty(), sb.toString())
    }
}
