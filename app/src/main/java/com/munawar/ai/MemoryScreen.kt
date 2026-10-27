package com.munawar.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.View
import kotlinx.coroutines.launch

class MemoryScreen(private val a: MainActivity) : Screen {
    private val list = a.vbox()
    override val view: View = build()

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(24)) }
        root.addView(a.label("Memory", 22f, Theme.CYAN, true))
        root.addView(a.label("Only things you approve are saved. Stored on this phone only, separate from your API keys.", 13f, Theme.MUTED))
        val r1 = a.hbox()
        r1.addView(a.button("＋ Add") { edit(null) }, a.lp(0, WRAP, 1f, 3))
        r1.addView(a.button("Summarize chat", Theme.CYAN) { summarize() }, a.lp(0, WRAP, 1f, 3))
        root.addView(r1)
        val r2 = a.hbox()
        r2.addView(a.button("🔐 Backup", Theme.CARD) { askPass("Backup passphrase") { p -> backup(p) } }, a.lp(0, WRAP, 1f, 3))
        r2.addView(a.button("♻ Restore", Theme.CARD) { askPass("Backup passphrase") { p -> restore(p) } }, a.lp(0, WRAP, 1f, 3))
        root.addView(r2)
        root.addView(a.label("Backups are encrypted with your passphrase (AES-256). If you forget it, the backup can't be opened.", 11f, Theme.MUTED))
        root.addView(a.button("Clear all memory", Theme.RED) {
            AlertDialog.Builder(a).setTitle("Delete all memories?").setMessage("This cannot be undone.")
                .setPositiveButton("Delete") { _, _ -> a.memory.clear(); refresh() }.setNegativeButton("Cancel", null).show()
        }, a.lp(MATCH, WRAP, m = 3))
        root.addView(list)
        refresh()
        return a.scroll(root)
    }

    override fun onShow() = refresh()

    private fun refresh() {
        list.removeAllViews()
        val items = a.memory.all().reversed()
        if (items.isEmpty()) list.addView(a.label("Nothing saved yet.", 14f, Theme.MUTED))
        items.forEach { m ->
            val c = a.card()
            c.addView(a.label(m.text))
            val r = a.hbox()
            r.addView(a.button("Edit", Theme.CARD) { edit(m) }, a.lp(0, WRAP, 1f, 2))
            r.addView(a.button("Delete", Theme.CARD) { a.memory.delete(m.id); refresh() }, a.lp(0, WRAP, 1f, 2))
            c.addView(r)
            list.addView(c, a.lp(MATCH, WRAP, m = 4))
        }
    }

    private fun edit(m: Memory?, prefill: String = "") {
        val f = a.field("Memory text", multi = true)
        f.setText(m?.text ?: prefill)
        AlertDialog.Builder(a).setTitle(if (m == null) "Add memory" else "Edit memory").setView(f)
            .setPositiveButton("Save") { _, _ ->
                val t = f.text.toString().trim()
                if (t.isNotEmpty()) { if (m == null) a.memory.add(t) else a.memory.update(m.id, t); refresh() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun summarize() {
        val key = a.prefs.apiKey()
        if (key == null) { a.toast("Add your API key in Settings first"); return }
        val turns = a.chatEngine.history.recent(20)
        if (turns.size < 2) { a.toast("Chat a bit first"); return }
        a.toast("Summarizing…")
        a.scope.launch {
            try {
                val s = Gemini.generate(
                    key, a.prefs.model, 0.3f,
                    "Summarize the useful, lasting facts and preferences about the user from this chat in at most 3 short bullet lines. Exclude passwords, ID numbers and other sensitive data. Plain text only.",
                    turns + Turn("user", "Please summarize the above conversation now.")
                )
                edit(null, s)
            } catch (e: Exception) { a.toast(e.message ?: "Failed") }
        }
    }

    // ---------- encrypted backup ----------
    private fun askPass(title: String, then: (CharArray) -> Unit) {
        val f = a.field("Passphrase", password = true)
        AlertDialog.Builder(a).setTitle(title).setView(f)
            .setPositiveButton("OK") { _, _ ->
                val p = f.text.toString().toCharArray()
                if (p.size < 6) a.toast("Use at least 6 characters.") else then(p)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun backup(pass: CharArray) {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "munawar-backup.mnw")
        a.startForResult(i) { code, data ->
            val uri = data?.data
            if (code == Activity.RESULT_OK && uri != null) {
                try {
                    val enc = Backup.encrypt(pass, a.memory.exportJson().toByteArray())
                    a.contentResolver.openOutputStream(uri)?.use { it.write(enc) }
                    a.toast("✔ Backup saved (${a.memory.all().size} memories, encrypted)")
                } catch (e: Exception) { a.toast("Backup failed: ${e.javaClass.simpleName}") }
            }
        }
    }

    private fun restore(pass: CharArray) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        a.startForResult(i) { code, data ->
            val uri = data?.data
            if (code == Activity.RESULT_OK && uri != null) {
                try {
                    val bytes = a.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val plain = if (bytes == null) null else Backup.decrypt(pass, bytes)
                    if (plain == null) a.toast("Wrong passphrase, or not a Munawar backup.")
                    else { val n = a.memory.importJson(String(plain)); refresh(); a.toast("✔ Restored $n new memories") }
                } catch (e: Exception) { a.toast("Restore failed: ${e.javaClass.simpleName}") }
            }
        }
    }
}
