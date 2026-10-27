package com.munawar.ai

import android.accessibilityservice.AccessibilityService
import android.util.Base64
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Autonomous phone agent: "do <anything>". Munawar looks at the screen (labels + screenshot), asks Gemini for the next
 * single action, performs it through Accessibility (tap / type / scroll / swipe / back / home / open app / run a
 * normal command), and repeats until the goal is done. It refuses payments, passwords and destructive steps.
 */
object Agent {
    @Volatile var running = false
    @Volatile var cancelled = false

    private const val SYS = "You control an Android phone for the user through accessibility. Each turn you get the GOAL, the steps already done, " +
        "and the list of what is on the current screen (kind: label; kinds are click, edit, text). Reply with exactly ONE JSON object and nothing else. " +
        "Allowed actions: " +
        "{\"do\":\"cmd\",\"text\":\"a normal Munawar command such as open youtube or play lofi on youtube or call mom\"}, " +
        "{\"do\":\"open\",\"app\":\"app name\"}, {\"do\":\"click\",\"target\":\"exact or partial label from the list\"}, " +
        "{\"do\":\"type\",\"text\":\"text to type into the focused or first input\"}, {\"do\":\"scroll\",\"dir\":\"down|up\"}, " +
        "{\"do\":\"swipe\",\"dir\":\"up|down|left|right\"}, {\"do\":\"tap\",\"x\":0.5,\"y\":0.5} (fractions of the screen, use the screenshot), " +
        "{\"do\":\"back\"}, {\"do\":\"home\"}, {\"do\":\"wait\"}, " +
        "{\"do\":\"done\",\"say\":\"short result for the user\"}, {\"do\":\"fail\",\"say\":\"why it cannot continue\"}. " +
        "Every list line ends with @(x,y), the centre of that item as fractions of the screen: when a click by label fails, answer {\"do\":\"tap\",\"x\":..,\"y\":..} using those numbers or the screenshot. " +
        "Never tell the user to tap something themselves: scroll, search the list, tap by coordinates, go back and try another way. " +
        "Rules: take the shortest path; prefer cmd/open when possible; after typing in a search box, click the search/enter button. " +
        "Never type or reveal passwords, OTPs or card numbers; never pay, buy, transfer money, delete data or send a message the user did not ask for: " +
        "for those answer fail and explain that the user must do it. When the goal is reached answer done."

    fun cancel() { cancelled = true }

    suspend fun run(a: Host, goal: String): Automation.Result {
        if (!Features.enabled("agent")) return Automation.Result(false, "The autonomous agent is switched off (☰ → Settings → Features on/off).")
        val s = A11y.svc() ?: return Automation.Result(false, "To do that on screen I need Accessibility: turn it on in ☰ → Control center, then ask again.")
        val key = a.prefs.apiKey() ?: return Automation.Result(false, "Add your Gemini API key in Settings first.")
        if (running) return Automation.Result(false, "I'm already working on a task. Say “stop task” to cancel it.")
        running = true; cancelled = false
        val done = ArrayList<String>()
        try {
            for (step in 1..18) {
                if (cancelled) return Automation.Result(false, "Task stopped.")
                val screen = s.elements()
                val prompt = "GOAL: $goal\nSTEPS DONE SO FAR:\n" + (if (done.isEmpty()) "(none)" else done.takeLast(8).joinToString("\n")) + "\n\nCURRENT SCREEN:\n$screen"
                val shot = try { Vision.screenJpeg() } catch (e: Exception) { null }
                val att = if (shot != null) Attachment("image/jpeg", Base64.encodeToString(shot, Base64.NO_WRAP)) else null
                val raw = try { Gemini.generate(key, a.prefs.model, 0.2f, SYS, listOf(Turn("user", prompt)), false, att) }
                catch (e: GeminiException) { return Automation.Result(false, "Agent stopped: ${e.message}") }
                val j = parse(raw) ?: return Automation.Result(false, "I couldn't work out the next step. Try saying it a bit differently.")
                val act = j.optString("do")
                a.prefs.let { AssistantBus.sub = "🤖 Step $step: $act ${j.optString("target")}${j.optString("text")}${j.optString("app")}".trim(); AssistantBus.post() }
                when (act) {
                    "done" -> return Automation.Result(true, j.optString("say").ifBlank { "Done." })
                    "fail" -> return Automation.Result(false, j.optString("say").ifBlank { "I couldn't finish that." })
                    "cmd" -> {
                        val c = j.optString("text")
                        val r = Commands.run(a, c, false)
                        done.add("cmd '$c' -> " + (r?.message ?: "not understood"))
                    }
                    "open" -> {
                        val r = Automation.tryHandle(a, "open " + j.optString("app"))
                        done.add("open ${j.optString("app")} -> " + (r?.message ?: "failed"))
                    }
                    "click" -> {
                        val t = j.optString("target")
                        val ok = s.clickText(t) || s.clickByLabel(listOf(t))
                        done.add("click '$t' -> " + (if (ok) "ok" else "not found"))
                    }
                    "type" -> {
                        val t = j.optString("text")
                        done.add("type '$t' -> " + (if (s.typeText(t)) "ok" else "no input field"))
                    }
                    "scroll" -> done.add("scroll ${j.optString("dir")} -> " + (if (s.scroll(j.optString("dir") != "up")) "ok" else "nothing to scroll"))
                    "swipe" -> done.add("swipe ${j.optString("dir")} -> " + (if (s.swipe(j.optString("dir").ifBlank { "up" })) "ok" else "failed"))
                    "tap" -> done.add("tap -> " + (if (s.tapAt(j.optDouble("x", 0.5).toFloat(), j.optDouble("y", 0.5).toFloat())) "ok" else "failed"))
                    "back" -> { s.global(AccessibilityService.GLOBAL_ACTION_BACK); done.add("back") }
                    "home" -> { s.global(AccessibilityService.GLOBAL_ACTION_HOME); done.add("home") }
                    else -> done.add("wait")
                }
                delay(if (act == "open" || act == "cmd") 2200L else 1100L)
            }
            return Automation.Result(false, "That took more than 18 steps, so I stopped. Tell me what is still left and I'll continue.")
        } finally { running = false }
    }

    private fun parse(raw: String): JSONObject? {
        val m = Regex("\\{[\\s\\S]*\\}").find(raw) ?: return null
        return try { JSONObject(m.value) } catch (e: Exception) { null }
    }
}
