package com.munawar.ai

import android.app.AlertDialog
import android.view.View
import android.widget.Switch

/** Automation triggers + habit learning. */
class TriggersScreen(private val a: MainActivity) : Screen {
    private val list = a.vbox()
    private val habit = a.label("", 13f, Theme.MUTED)
    override val view: View = build()

    private val kinds = listOf(
        Triple("Time (every day)", "time", "HH:MM, 24-hour e.g. 07:30"),
        Triple("Charger plugged in", "charging:on", ""),
        Triple("Charger unplugged", "charging:off", ""),
        Triple("Battery drops to …%", "battery", "Percent, e.g. 20"),
        Triple("Headphones plugged in", "headphones:on", ""),
        Triple("Headphones unplugged", "headphones:off", ""),
        Triple("Screen turns on", "screen:on", ""),
        Triple("Screen turns off", "screen:off", ""),
        Triple("Wi-Fi connects", "wifi:on", ""),
        Triple("Wi-Fi disconnects", "wifi:off", ""),
        Triple("An app is opened", "app", "App name or package, e.g. youtube"),
        Triple("An app is closed", "appclose", "App name or package, e.g. youtube")
    )

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(28)) }
        root.addView(a.label("Automation & Triggers", 22f, Theme.CYAN, true))
        root.addView(a.label("Time and charger triggers work even when Munawar is closed. Screen, headphones, battery %, Wi-Fi and app triggers work while Munawar's background listening is on or the app is open (app triggers also need Accessibility). “Start a conversation” only works with the app open: Android blocks starting the microphone from the background.", 12f, Theme.MUTED))
        root.addView(a.button("＋ Add trigger", Theme.PURPLE) { pickKind() }, a.lp(MATCH, WRAP, m = 3))
        root.addView(list)
        root.addView(a.section("Habit learning"))
        root.addView(Switch(a).apply {
            text = "Learn my habits (on this phone only)"; setTextColor(Theme.TEXT); isChecked = a.prefs.habitLearning; minimumHeight = a.dp(48)
            setOnCheckedChangeListener { _, v -> a.prefs.habitLearning = v }
        })
        root.addView(habit)
        root.addView(a.button("Forget learned habits", Theme.CARD) { Habits.clear(); refresh(); a.toast("Habits cleared") }, a.lp(MATCH, WRAP, m = 2))
        return a.scroll(root)
    }

    private fun refresh() {
        list.removeAllViews()
        val all = Triggers.all()
        if (all.isEmpty()) list.addView(a.label("No triggers yet.", 13f, Theme.MUTED))
        for (t in all) {
            val card = a.card()
            card.addView(a.label(Triggers.describe(t), 14f))
            val row = a.hbox()
            row.addView(Switch(a).apply {
                text = "On"; setTextColor(Theme.TEXT); isChecked = t.enabled
                setOnCheckedChangeListener { _, _ -> Triggers.toggle(a, t.id) }
            }, a.lp(0, WRAP, 1f))
            row.addView(a.button("Delete", Theme.RED) { Triggers.remove(a, t.id); refresh() }, a.lp(WRAP, WRAP, m = 2))
            card.addView(row)
            list.addView(card, a.lp(MATCH, WRAP, m = 4))
        }
        val s = Habits.suggestion()
        habit.text = if (s != null) "💡 You usually run “$s” around this time." else "Nothing learned for this hour yet. Keep using commands and Munawar will notice patterns."
    }

    private fun pickKind() {
        AlertDialog.Builder(a).setTitle("When…").setItems(kinds.map { it.first }.toTypedArray()) { _, i ->
            val k = kinds[i]
            val parts = k.second.split(":")
            if (k.third.isEmpty()) pickAction(parts[0], parts.getOrElse(1) { "" })
            else {
                val f = a.field(k.third)
                AlertDialog.Builder(a).setTitle(k.first).setView(f)
                    .setPositiveButton("Next") { _, _ ->
                        val v = f.text.toString().trim()
                        if (v.isEmpty()) a.toast("Enter a value") else pickAction(parts[0], v)
                    }.setNegativeButton("Cancel", null).show()
            }
        }.show()
    }

    private fun pickAction(type: String, arg: String) {
        if (type == "time" && !Regex("^\\d{1,2}:\\d{2}$").matches(arg)) { a.toast("Use the format HH:MM, e.g. 07:30"); return }
        if (type == "battery" && arg.toIntOrNull() == null) { a.toast("Enter a number like 20"); return }
        AlertDialog.Builder(a).setTitle("Then…").setItems(arrayOf("Speak a text", "Run a command", "Start a conversation")) { _, i ->
            when (i) {
                2 -> save(type, arg, "talk", "")
                else -> {
                    val f = a.field(if (i == 0) "What should I say?" else "Command, e.g. open youtube")
                    AlertDialog.Builder(a).setTitle(if (i == 0) "Speak" else "Run").setView(f)
                        .setPositiveButton("Save") { _, _ ->
                            val v = f.text.toString().trim()
                            if (v.isEmpty()) a.toast("Enter something") else save(type, arg, if (i == 0) "speak" else "run", v)
                        }.setNegativeButton("Cancel", null).show()
                }
            }
        }.show()
    }

    private fun save(type: String, arg: String, action: String, payload: String) {
        Triggers.add(a, Trigger(System.currentTimeMillis(), type, arg, action, payload, true))
        refresh()
        a.toast("Trigger saved")
    }

    override fun onShow() { refresh() }
}
