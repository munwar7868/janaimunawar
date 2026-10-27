package com.munawar.ai

import android.view.View

class NotesScreen(private val a: MainActivity) : Screen {
    private val input = a.field("Write a task or a note…", multi = true)
    private val list = a.vbox()
    override val view: View = build()

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(24)) }
        root.addView(RainbowText(a, "Notes & Tasks", 22f))
        root.addView(a.label("Also by voice: “add task buy milk”, “add note …”, “show tasks”, “done task 2”.", 12f, Theme.MUTED))
        root.addView(input, a.lp(MATCH, WRAP, m = 3))
        val r = a.hbox()
        r.addView(a.button("＋ Task") { add("task") }, a.lp(0, WRAP, 1f, 2))
        r.addView(a.button("＋ Note", Theme.CYAN) { add("note") }, a.lp(0, WRAP, 1f, 2))
        r.addView(a.button("Clear done", Theme.CARD) { a.notes.clearDone(); refresh() }, a.lp(0, WRAP, 1f, 2))
        root.addView(r)
        root.addView(list)
        refresh()
        return a.scroll(root)
    }

    private fun add(kind: String) {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        a.notes.add(kind, t); input.setText(""); refresh()
    }

    override fun onShow() = refresh()

    private fun section(title: String, kind: String) {
        list.addView(a.section(title))
        val items = a.notes.all(kind).reversed()
        if (items.isEmpty()) list.addView(a.label("Nothing here yet.", 13f, Theme.MUTED))
        items.forEach { n ->
            val c = a.card()
            val row = a.hbox()
            val txt = a.label((if (kind == "task") (if (n.done) "✔ " else "○ ") else "• ") + n.text, 15f, if (n.done) Theme.MUTED else Theme.TEXT)
            row.addView(txt, a.lp(0, WRAP, 1f))
            if (kind == "task") row.addView(a.button(if (n.done) "Undo" else "Done", Theme.CARD) { a.notes.toggle(n.id); refresh() }, a.lp(WRAP, WRAP, m = 2))
            row.addView(a.button("✕", Theme.CARD) { a.notes.delete(n.id); refresh() }, a.lp(WRAP, WRAP, m = 2))
            c.addView(row)
            list.addView(c, a.lp(MATCH, WRAP, m = 3))
        }
    }

    private fun refresh() {
        list.removeAllViews()
        section("Tasks", "task")
        section("Notes", "note")
    }
}
