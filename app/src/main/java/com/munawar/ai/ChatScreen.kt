package com.munawar.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlinx.coroutines.launch

/** Used for both "Chat" (coding=false) and "Code" (coding=true). */
class ChatScreen(private val a: MainActivity, private val coding: Boolean) : Screen {
    private val engine = if (coding) a.codeEngine else a.chatEngine
    private val list = a.vbox().apply { setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8)) }
    private val scroll = ScrollView(a).apply { addView(list) }
    private val input = a.field(if (coding) "Ask a coding question or paste an error…" else "Type a message or command…", multi = true)
    private var busy = false
    override val view: View = build()

    private fun build(): View {
        val root = a.vbox()
        val head = a.hbox().apply { setPadding(a.dp(12), a.dp(6), a.dp(12), a.dp(2)) }
        head.addView(a.label(if (coding) "Coding Assistant" else "Chat", 20f, Theme.CYAN, true), a.lp(0, WRAP, 1f))
        if (!coding) {
            val sp = a.button(if (a.prefs.speak) "🔊 On" else "🔇 Off", Theme.CARD) {}
            sp.setOnClickListener {
                a.prefs.speak = !a.prefs.speak
                sp.text = if (a.prefs.speak) "🔊 On" else "🔇 Off"
                if (!a.prefs.speak) a.speaker.stop()
            }
            head.addView(sp, a.lp(WRAP, WRAP, m = 2))
        }
        head.addView(a.button("Clear", Theme.CARD) { engine.history.clear(); list.removeAllViews() }, a.lp(WRAP, WRAP, m = 2))
        root.addView(head)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val row = a.hbox().apply { setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(8)) }
        row.addView(a.button("🎤", Theme.CARD) { voiceType() }, a.lp(a.dp(56), WRAP, m = 2))
        row.addView(input, a.lp(0, WRAP, 1f, 2))
        row.addView(a.button("Send", Theme.PURPLE) { send() }, a.lp(WRAP, WRAP, m = 2))
        root.addView(row)

        if (engine.history.all().isEmpty()) {
            list.addView(info(
                if (coding) "Ask me to write, explain or debug code. I give full files with paths. I can't compile or test code myself - I'll only say so if you paste real output."
                else "Say hi! You can also type phone commands like \"open youtube\", \"call mom\" or \"set alarm 7:30 am\"."
            ))
        } else {
            engine.history.all().takeLast(30).forEach { list.addView(bubble(it.text, it.role == "user")) }
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
        return root
    }

    /** Called by wake word / floating orb / other screens. */
    fun submit(text: String) { input.setText(text); send() }

    private fun voiceType() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, a.prefs.sttLocale())
        AssistantService.uiPause(true)
        a.startForResult(i) { code, data ->
            AssistantService.uiPause(false)
            if (code == Activity.RESULT_OK) {
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                    input.setText((input.text.toString() + " " + it).trim()); input.setSelection(input.text.length)
                }
            }
        }
    }

    private fun send() {
        val t = input.text.toString().trim()
        if (t.isEmpty() || busy) return
        input.setText("")
        list.addView(bubble(t, true)); down()
        busy = true
        val thinking = info("Thinking…")
        list.addView(thinking); down()
        a.orbState(OrbView.S.THINKING)
        a.scope.launch {
            try {
                if (!coding) {
                    val r = Commands.run(a.host, t)
                    if (r != null) {
                        list.removeView(thinking)
                        if (r.message.length > 160) list.addView(bubble(r.message, false)) else list.addView(info((if (r.ok) "⚙ " else "⚠ ") + r.message))
                        if (r.ok && r.message.length > 80) a.say(r.message) else a.orbState(OrbView.S.IDLE)
                        return@launch
                    }
                }
                val r = engine.reply(t)
                list.removeView(thinking)
                list.addView(bubble(r.text, false))
                if (!coding) a.say(r.text) else a.orbState(OrbView.S.IDLE)
                r.memory?.let { askMemory(it) }
            } catch (e: Exception) {
                list.removeView(thinking)
                list.addView(info("⚠ " + (e.message ?: "Unknown error")))
                a.orbState(OrbView.S.IDLE)
            } finally {
                busy = false; down()
            }
        }
    }

    private fun askMemory(text: String) {
        AlertDialog.Builder(a).setTitle("Remember this?")
            .setMessage("$text\n\nOnly save things you're comfortable storing on this phone. Don't save passwords or ID numbers.")
            .setPositiveButton("Save") { _, _ -> a.memory.add(text); a.toast("Saved to Memory") }
            .setNegativeButton("No", null).show()
    }

    private fun down() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

    private fun info(text: String) = a.label(text, 13f, Theme.MUTED).apply { setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6)) }

    private fun bubble(text: String, user: Boolean): View {
        val box = a.vbox().apply {
            background = roundBg(if (user) Theme.PURPLE_DARK else Theme.CARD, a.dp(16).toFloat())
            setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8))
        }
        if (user) box.addView(a.label(text).apply { setTextIsSelectable(true) }) else render(box, text)
        val p = a.lp(WRAP, WRAP, m = 4)
        p.gravity = if (user) Gravity.END else Gravity.START
        box.layoutParams = p
        return box
    }

    private fun render(box: LinearLayout, text: String) {
        val re = Regex("```([\\w+#.-]*)\\n?([\\s\\S]*?)```")
        var last = 0
        fun plain(s: String) {
            if (s.isNotBlank()) box.addView(a.label(s.trim().replace("**", "")).apply { setTextIsSelectable(true) })
        }
        for (m in re.findAll(text)) {
            plain(text.substring(last, m.range.first))
            val lang = m.groupValues[1].ifEmpty { "code" }
            val code = m.groupValues[2].trimEnd()
            val card = a.vbox().apply { background = roundBg(Theme.BG, a.dp(10).toFloat(), Theme.PURPLE_DARK); setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6)) }
            val top = a.hbox()
            top.addView(a.label(lang, 12f, Theme.CYAN), a.lp(0, WRAP, 1f))
            top.addView(a.button("Copy", Theme.CARD) {
                (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code))
                a.toast("Code copied")
            })
            card.addView(top)
            val hs = HorizontalScrollView(a)
            hs.addView(a.label(code, 12f, Theme.TEXT).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) })
            card.addView(hs)
            box.addView(card, a.lp(MATCH, WRAP, m = 4))
            last = m.range.last + 1
        }
        plain(text.substring(last))
    }

    override fun onHide() { a.speaker.stop(); a.orbState(OrbView.S.IDLE) }
}
