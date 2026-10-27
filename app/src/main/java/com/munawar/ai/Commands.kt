package com.munawar.ai

import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.delay
import org.json.JSONArray

/**
 * Single entry point for every voice/text command. Order: offline answers, multi-step chains, accessibility actions,
 * notifications, smart home, then plain phone intents (Automation).
 */
object Commands {
    private val chain = Regex("(?i)\\s+(?:and then|then|phir|fir|uske baad|aur phir|&)\\s+")

    suspend fun run(a: Host, raw: String, allowOffline: Boolean = true): Automation.Result? {
        Security.gate(raw)?.let { return it }
        Features.blocked(raw)?.let { return it }
        val t = Lang.normalize(raw)
        if (t.isEmpty()) return null
        if (allowOffline) Offline.tryHandle(a, raw.trim())?.let { return Automation.Result(true, it) }
        val steps = t.split(chain).map { it.trim() }.filter { it.isNotEmpty() }
        if (steps.size <= 1) return single(a, t)?.also { if (it.ok) Habits.record(t) }

        val log = StringBuilder()
        var allOk = true
        for ((i, s) in steps.withIndex()) {
            val r = single(a, s)
            if (r == null) {
                if (i == 0) return null // ordinary sentence containing "then"
                log.append("${i + 1}. ✖ Not understood: $s\n"); allOk = false; break
            }
            log.append("${i + 1}. ${if (r.ok) "✔" else "✖"} ${r.message}\n")
            if (!r.ok) { allOk = false; break }
            if (i < steps.size - 1) delay(2500) // let the opened app come to the front
        }
        val note = if (steps.size > 1 && !Perms.overlay(a)) "\n(Tip: allow 'Display over other apps' in Control so later steps can open apps while another app is in front.)" else ""
        return Automation.Result(allOk, "Multi-step:\n" + log.toString().trim() + note)
    }

    private suspend fun single(a: Host, t: String): Automation.Result? {
        val l = t.lowercase().trim()
        val s = A11y.svc()

        fun need(): Automation.Result = Automation.Result(false, A11y.NEEDS)

        Regex("^(?:click|tap|press|dabao|touch)\\s+(?:on\\s+)?(?!(?:the\\s+)?(?:screen\\s+)?(?:center|centre|middle)$)(.+)$").find(l)?.let { m ->
            if (s == null) return need()
            val q = m.groupValues[1].trim()
            val ok = s.clickText(q)
            return Automation.Result(ok, if (ok) "Android accepted a tap on \"$q\"." else "Couldn't find a tappable \"$q\" on this screen.")
        }
        Regex("(?i)^(?:type|enter text)\\s+(.+)$").find(t)?.let { m ->
            if (s == null) return need()
            val ok = s.typeText(m.groupValues[1])
            return Automation.Result(ok, if (ok) "Text entered." else "No text field found (or it is a password field).")
        }
        if (l.contains("scroll")) {
            if (s == null) return need()
            val fwd = !(l.contains("up") || l.contains("upar") || l.contains("upper") || l.contains("peeche"))
            val ok = s.scroll(fwd)
            return Automation.Result(ok, if (ok) "Scrolled ${if (fwd) "down" else "up"}." else "Nothing scrollable found.")
        }
        if (l == "back" || l.contains("go back") || l.contains("back jao") || l.contains("wapas jao")) {
            if (s == null) return need()
            return Automation.Result(s.global(AccessibilityService.GLOBAL_ACTION_BACK), "Back pressed.")
        }
        if (l == "home" || l.contains("go home") || l.contains("home jao") || l.contains("home screen")) {
            if (s == null) return need()
            return Automation.Result(s.global(AccessibilityService.GLOBAL_ACTION_HOME), "Went to the home screen.")
        }
        if (l.contains("recent apps") || l == "recents") {
            if (s == null) return need()
            return Automation.Result(s.global(AccessibilityService.GLOBAL_ACTION_RECENTS), "Recent apps opened.")
        }
        if (l.contains("notification panel") || l.contains("notification shade")) {
            if (s == null) return need()
            return Automation.Result(s.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS), "Notification panel opened.")
        }
        if (l.contains("explain screen") || l.contains("analyze screen") || l.contains("analyse screen") || l.contains("screen samjhao") ||
            l.contains("what is on my screen") || l.contains("what's on my screen") || l.contains("screen explain")
        ) {
            val (ok, msg) = ScreenAI.explain(a.prefs, t)
            return Automation.Result(ok, msg)
        }
        if (l.contains("read screen") || l.contains("screen padho") || l.contains("screen par kya")) {
            if (s == null) return need()
            val txt = s.screenText()
            return if (txt.isBlank()) Automation.Result(false, "Nothing readable on this screen.")
            else Automation.Result(true, txt.take(900))
        }
        if (l.contains("notification") && !l.contains("setting") &&
            (l.contains("read") || l.contains("padh") || l.contains("batao") || l.contains("check") || l.contains("kya aaya"))
        ) {
            if (!Perms.notifAccess(a)) return Automation.Result(false, "Turn on Notification access: Control tab → Notification access.")
            val list = NotifListener.recent(8)
            return if (list.isEmpty()) Automation.Result(true, "No recent notifications.")
            else Automation.Result(true, "Recent notifications:\n" + list.joinToString("\n") { "• $it" })
        }
        if (l.contains("take screenshot") || l.contains("screenshot le")) {
            if (s == null) return need()
            return Automation.Result(s.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT), "Screenshot requested (saved by Android).")
        }
        if (l.contains("lock screen") || l.contains("lock phone")) {
            if (Admin.lockNow(a)) return Automation.Result(true, "Screen locked.")
            if (s == null) return need()
            return Automation.Result(s.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN), "Screen locked.")
        }
        Regex("(?i)^(?:add|create|make)\\s+(?:a\\s+)?(?:new\\s+)?(?:task|todo|to-do)\\b[:\\s]*(.+)$|^(?:task|todo)[:\\s]+(.+)$").find(t)?.let { m ->
            val txt = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
            if (txt.isNotEmpty()) { a.notes.add("task", txt); return Automation.Result(true, "Task added: \"$txt\".") }
        }
        Regex("(?i)^(?:add|take|save|write|make)\\s+(?:a\\s+)?(?:new\\s+)?note\\b[:\\s]*(.+)$|^note[:\\s]+(.+)$").find(t)?.let { m ->
            val txt = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
            if (txt.isNotEmpty()) { a.notes.add("note", txt); return Automation.Result(true, "Note saved: \"$txt\".") }
        }
        Regex("(?i)^(?:done|complete|finish|finished)\\s+task\\s+(\\d+)$").find(t)?.let { m ->
            val n = m.groupValues[1].toInt()
            val task = a.notes.all("task").getOrNull(n - 1) ?: return Automation.Result(false, "No task number $n.")
            a.notes.toggle(task.id)
            return Automation.Result(true, "Marked task $n as ${if (task.done) "done" else "not done"}.")
        }
        if (l == "show tasks" || l == "my tasks" || l == "task list" || l == "todo list" || l == "show todo" || l == "list tasks") {
            val tl = a.notes.all("task")
            return if (tl.isEmpty()) Automation.Result(true, "No tasks yet.")
            else Automation.Result(true, "Tasks:\n" + tl.mapIndexed { i, n -> "${i + 1}. ${if (n.done) "✔" else "○"} ${n.text}" }.joinToString("\n"))
        }
        if (l == "show notes" || l == "my notes" || l == "list notes") {
            val nl = a.notes.all("note")
            return if (nl.isEmpty()) Automation.Result(true, "No notes yet.") else Automation.Result(true, "Notes:\n" + nl.joinToString("\n") { "• ${it.text}" })
        }
        if (l.contains("daily briefing") || l == "briefing" || l.contains("my day") || l.contains("aaj ka briefing")) return Automation.Result(true, briefing(a))
        Myra.handle(a, t)?.let { return it }
        SmartHome.handle(a.prefs, l)?.let { return it }
        return Automation.tryHandle(a, t)
    }

    private fun briefing(a: Host): String {
        val sb = StringBuilder()
        sb.append("📅 ").append(java.text.SimpleDateFormat("EEEE, d MMMM • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())).append('\n')
        val bm = a.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
        sb.append("🔋 Battery ").append(bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)).append("%\n")
        val tasks = a.notes.pendingTasks()
        sb.append(if (tasks.isEmpty()) "✅ No pending tasks\n" else "📝 Pending tasks:\n" + tasks.take(5).joinToString("\n") { "  • ${it.text}" } + "\n")
        if (Perms.notifAccess(a)) sb.append("🔔 Recent notifications: ").append(NotifListener.recent(30).size).append('\n')
        return sb.toString().trim()
    }

    /** Asks Gemini to turn a goal into supported commands, one per line. The user reviews them before anything runs. */
    suspend fun plan(a: Host, goal: String): List<String> {
        val key = a.prefs.apiKey() ?: throw GeminiException("Add your Gemini API key in Settings first.")
        val sys = "You convert a phone task into a short list of commands for an Android assistant. Output ONLY a JSON array of strings, no prose. " +
            "Each string must be ONE command from these forms: open <app>; youtube <song>; set alarm H:MM am/pm; timer N minutes; call <name or number>; " +
            "sms <name or number> saying <text>; whatsapp message saying <text>; email <address> saying <text>; navigate to <place>; <thing> near me; " +
            "remind me to <task>; wifi settings; google search <query>; click <visible text>; type <text>; scroll down; scroll up; go back; go home; read screen. " +
            "At most 6 steps. Never include purchases or payments."
        val raw = Gemini.generate(key, a.prefs.model, 0.2f, sys, listOf(Turn("user", goal)))
        val json = raw.substringAfter("[", "").substringBeforeLast("]", "")
        if (json.isEmpty()) throw GeminiException("Couldn't plan that. Try rephrasing.")
        val arr = JSONArray("[$json]")
        return (0 until arr.length()).map { arr.getString(it).trim() }.filter { it.isNotEmpty() }.take(6)
    }
}
