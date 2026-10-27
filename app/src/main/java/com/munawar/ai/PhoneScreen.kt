package com.munawar.ai

import android.app.AlertDialog
import android.view.View
import kotlinx.coroutines.launch

class PhoneScreen(private val a: MainActivity) : Screen {
    private val input = a.field("Type a phone command…")
    private val log = a.vbox()
    private var busy = false
    override val view: View = build()

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(24)) }
        root.addView(a.label("Phone Automation", 22f, Theme.CYAN, true))
        root.addView(a.label("Standard Android intents work out of the box. Android or the target app has the final say; call, SMS, alarm, WhatsApp and email always ask you to confirm. Tap/type/scroll/read-screen need Accessibility (Control tab). Chain steps with \"then\".", 13f, Theme.MUTED))
        root.addView(a.section("Run a command"))
        val row = a.hbox()
        row.addView(input, a.lp(0, WRAP, 1f, 2))
        row.addView(a.button("Run") { run(input.text.toString().trim()) }, a.lp(WRAP, WRAP, m = 2))
        root.addView(row)
        root.addView(a.button("🤖 Plan a task with AI", Theme.CYAN) { planDialog() }, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.section("Examples (tap to fill)"))
        Automation.examples.forEach { ex ->
            root.addView(a.button(ex, Theme.CARD) { input.setText(ex) }, a.lp(MATCH, WRAP, m = 2))
        }
        root.addView(a.section("Results"))
        root.addView(log)
        return a.scroll(root)
    }

    private fun add(line: String, ok: Boolean) {
        log.addView(a.label(line, 13f, if (ok) Theme.TEXT else Theme.PINK).apply { setPadding(0, a.dp(6), 0, a.dp(6)); setTextIsSelectable(true) }, 0)
    }

    private fun run(t: String) {
        if (t.isEmpty() || busy) return
        busy = true
        a.scope.launch {
            try {
                val r = Commands.run(a.host, t)
                add("› $t\n" + (if (r == null) "⚠ Not recognised as a phone command. Try Chat for general questions." else (if (r.ok) "✔ " else "⚠ ") + r.message), r?.ok == true)
            } finally { busy = false }
            input.setText("")
        }
    }

    private fun planDialog() {
        val f = a.field("Goal, e.g. play lofi on youtube and set a timer for 20 minutes", multi = true)
        AlertDialog.Builder(a).setTitle("Plan with AI").setView(f)
            .setPositiveButton("Plan") { _, _ ->
                val goal = f.text.toString().trim()
                if (goal.isEmpty()) return@setPositiveButton
                a.toast("Planning…")
                a.scope.launch {
                    try {
                        val steps = Commands.plan(a.host, goal)
                        if (steps.isEmpty()) { a.toast("No steps produced."); return@launch }
                        AlertDialog.Builder(a).setTitle("Run these steps?")
                            .setMessage(steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"))
                            .setPositiveButton("Run") { _, _ -> run(steps.joinToString(" then ")) }
                            .setNegativeButton("Cancel", null).show()
                    } catch (e: Exception) { a.toast(e.message ?: "Planning failed") }
                }
            }.setNegativeButton("Cancel", null).show()
    }
}
