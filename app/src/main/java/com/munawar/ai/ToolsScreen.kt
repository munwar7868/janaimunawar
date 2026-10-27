package com.munawar.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.view.View
import android.widget.HorizontalScrollView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Image understanding, OCR, document/PDF analysis, translation, study, writing, planner, email drafts and voice notes. */
class ToolsScreen(private val a: MainActivity) : Screen {
    private data class Tool(val id: String, val label: String, val hint: String, val prompt: String, val needsFile: Boolean = false, val web: Boolean = false)

    private val tools = listOf(
        Tool("describe", "🖼 Describe image", "Attach or take a photo", "Describe the attached image in detail, then list notable objects, any text, and anything unusual. If the user asked a question, answer it.", true),
        Tool("ocr", "🔤 Extract text", "Attach an image or PDF (OCR)", "Extract ALL text from the attached image or document exactly as written, preserving line breaks. Output only the text.", true),
        Tool("summarize", "📄 Summarize file", "Attach a PDF or text file", "Summarize the attached document: 5-8 key points, then a 2-line TL;DR. If the user asked something specific, focus on it.", true),
        Tool("docqa", "❓ Ask about file", "Attach a file and type your question", "Answer the user's question using only the attached file. Say so if the file doesn't contain the answer.", true),
        Tool("translate", "🌐 Translate", "Type or dictate the text", "Translate the user's text. If it is Hindi/Hinglish translate to English; otherwise translate to Hindi (Devanagari) and also give Hinglish. Add a short pronunciation note."),
        Tool("tutor", "🗣 Language tutor", "Say what you want to practice", "You are a friendly language tutor. Teach the requested phrase/topic with 3 example sentences (with translations), a tiny quiz of 3 questions, and answers at the end."),
        Tool("study", "📚 Study helper", "Type a topic or question", "Explain simply with one real-world example, then give 3 quiz questions with answers at the end."),
        Tool("write", "✍ Writing helper", "Paste your text or describe what to write", "Improve or draft the user's writing. Keep their meaning. Give the final version first, then 3 short tips."),
        Tool("plan", "🗓 Daily planner", "List today's tasks and fixed times", "Create a realistic hour-by-hour plan for today from the user's tasks. Include breaks and a short list of top 3 priorities."),
        Tool("website", "🌐 Website builder", "Describe the website (business, style, pages)", "You are an expert web designer. Build a COMPLETE, beautiful, responsive, modern single-file website (one HTML file with inline CSS and a little JavaScript, no external libraries or images, use CSS gradients/emoji/SVG instead). Output ONLY the full HTML document inside one ```html code block, nothing else."),
        Tool("research", "🔎 Deep research", "Topic to research on the web", "", false, true),
        Tool("gamecoach", "🎮 Game coach", "Game + what you need help with (attach a screenshot if you like)", "You are a real-time gaming coach. Give concrete, actionable tips for the game/situation described (or shown in the attached screenshot): 5 short numbered tips, then one pro trick. Be honest if you do not know the game.", false, true),
        Tool("phonehealth", "🩺 Phone health", "Leave empty to scan this phone, or describe a problem", "You are a phone-health assistant. Using the real device stats given, diagnose: what is fine, what needs attention, and 3 practical fixes. Do not invent data."),
        Tool("email", "✉ Email draft", "Describe the email (who, what, tone)", "Write an email draft. The FIRST line must be 'Subject: ...'. Then a blank line and the body. Nothing else."),
        Tool("voicenote", "🎙 Voice note", "Tap 🎤 to dictate, then Run", "Clean up this voice-note transcript and give: Summary (3 bullets), Action items, and any dates/reminders mentioned.")
    )

    private var tool = tools[0]
    private var att: Attachment? = null
    private var busy = false
    private var lastResult = ""
    private val toolRow = a.hbox()
    private val hint = a.label("", 12f, Theme.MUTED)
    private val attLabel = a.label("No file attached", 12f, Theme.MUTED)
    private val input = a.field("Type, paste or dictate…", multi = true)
    private val out = a.label("", 15f).apply { setTextIsSelectable(true) }
    private val actions = a.hbox()
    private val img = ImageView(a).apply { adjustViewBounds = true; visibility = View.GONE }
    private var lastImage: ByteArray? = null
    private val saveImgBtn = a.button("💾 Save image to gallery", Theme.CARD) { saveImage() }.apply { visibility = View.GONE }
    private val emailBtn = a.button("✉ Open in email", Theme.CARD) { openEmail() }
    private val htmlBtn = a.button("💾 Save website (.html) & open", Theme.CYAN) { saveHtml() }.apply { visibility = View.GONE }
    override val view: View = build()

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(24)) }
        root.addView(a.label("Tools", 22f, Theme.CYAN, true))
        root.addView(a.label("Photos, PDFs and text files are sent to Google Gemini for analysis. Camera and file pickers are Android's own, so no camera/storage permission is needed.", 12f, Theme.MUTED))

        root.addView(a.section("1. Attach (optional for some tools)"))
        val r1 = a.hbox()
        r1.addView(a.button("📷 Photo", Theme.PINK) { takePhoto() }, a.lp(0, WRAP, 1f, 2))
        r1.addView(a.button("🖼 Image") { pick(arrayOf("image/*")) }, a.lp(0, WRAP, 1f, 2))
        r1.addView(a.button("📄 PDF/Text", Theme.CYAN) { pick(arrayOf("application/pdf", "text/*")) }, a.lp(0, WRAP, 1f, 2))
        root.addView(r1)
        val r2 = a.hbox()
        r2.addView(attLabel, a.lp(0, WRAP, 1f, 2))
        r2.addView(a.button("Remove", Theme.CARD) { att = null; attLabel.text = "No file attached" }, a.lp(WRAP, WRAP, m = 2))
        root.addView(r2)

        root.addView(a.section("2. Pick a tool"))
        root.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(toolRow) })
        root.addView(hint)
        refreshTools()

        root.addView(a.section("3. Your text / question"))
        val r3 = a.hbox()
        r3.addView(input, a.lp(0, WRAP, 1f, 2))
        r3.addView(a.button("🎤", Theme.CARD) { dictate() }, a.lp(a.dp(56), WRAP, m = 2))
        root.addView(r3)
        root.addView(a.button("▶ Run", Theme.PURPLE) { run() }, a.lp(MATCH, WRAP, m = 4))

        root.addView(a.section("Result"))
        val card = a.card()
        card.addView(out)
        root.addView(card, a.lp(MATCH, WRAP))
        actions.addView(a.button("🔊 Speak", Theme.CARD) { speak() }, a.lp(0, WRAP, 1f, 2))
        actions.addView(a.button("Copy", Theme.CARD) { copy() }, a.lp(0, WRAP, 1f, 2))
        actions.addView(a.button("🧠 Save", Theme.CARD) { saveMemory() }, a.lp(0, WRAP, 1f, 2))
        root.addView(img, a.lp(MATCH, WRAP, m = 4))
        root.addView(saveImgBtn, a.lp(MATCH, WRAP, m = 2))
        root.addView(actions)
        root.addView(emailBtn, a.lp(MATCH, WRAP, m = 2))
        root.addView(htmlBtn, a.lp(MATCH, WRAP, m = 2))
        emailBtn.visibility = View.GONE
        return a.scroll(root)
    }

    private fun refreshTools() {
        toolRow.removeAllViews()
        tools.forEach { t ->
            toolRow.addView(a.button(t.label, if (t == tool) Theme.PURPLE else Theme.CARD) {
                tool = t; refreshTools()
            }, a.lp(WRAP, WRAP, m = 2))
        }
        hint.text = tool.hint + if (tool.needsFile) "  (file required)" else ""
    }

    // ---------- attachments ----------
    private fun load(uri: Uri) {
        attLabel.text = "Loading…"
        a.scope.launch {
            val at = try { withContext(Dispatchers.IO) { Media.load(a, uri) } } catch (e: Exception) { null }
            if (at == null) {
                attLabel.text = "No file attached"
                a.toast("Unsupported or too large. Use images, PDF or text files up to 15 MB.")
            } else {
                att = at
                attLabel.text = "📎 " + at.name.ifEmpty { at.mime }
            }
        }
    }

    private fun pick(types: Array<String>) {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_MIME_TYPES, types)
        a.startForResult(i) { code, data -> if (code == Activity.RESULT_OK) data?.data?.let { load(it) } }
    }

    private fun takePhoto() {
        if (!Perms.has(a, android.Manifest.permission.CAMERA)) {
            a.requestPerm(android.Manifest.permission.CAMERA) { g -> if (g) takePhoto() else a.toast("Camera permission is needed to take a photo.") }
            return
        }
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "munawar_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MunawarAI")
        }
        val uri = a.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        if (uri == null) { a.toast("Couldn't prepare a photo file."); return }
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("", uri)
        a.startForResult(i) { code, _ ->
            if (code == Activity.RESULT_OK) load(uri)
            else try { a.contentResolver.delete(uri, null, null) } catch (e: Exception) {}
        }
    }

    private fun dictate() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, a.prefs.sttLocale())
        AssistantService.uiPause(true)
        a.startForResult(i) { code, data ->
            AssistantService.uiPause(false)
            if (code == Activity.RESULT_OK) data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                input.setText((input.text.toString() + " " + it).trim())
            }
        }
    }

    // ---------- run ----------
    private fun run() {
        if (busy) return
        val key = a.prefs.apiKey()
        if (key == null) { a.toast("Add your Gemini API key in Settings first."); return }
        if (tool.needsFile && att == null) { a.toast("Attach a photo or file first."); return }
        var text = input.text.toString().trim()
        if (tool.id == "phonehealth" && text.isEmpty()) text = Myra.deviceReport(a)
        if (!tool.needsFile && text.isEmpty()) { a.toast("Type or dictate something first."); return }
        busy = true
        out.text = "Working…"
        emailBtn.visibility = View.GONE; htmlBtn.visibility = View.GONE
        img.visibility = View.GONE; saveImgBtn.visibility = View.GONE
        a.orbState(OrbView.S.THINKING)
        if (tool.id == "image") {
            a.scope.launch {
                try {
                    val (bytes, cap) = Gemini.generateImage(key, a.prefs.imageModel, text)
                    lastImage = bytes
                    img.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    img.visibility = View.VISIBLE; saveImgBtn.visibility = View.VISIBLE
                    lastResult = cap; out.text = cap.ifBlank { "✔ Image ready" }
                } catch (e: Exception) {
                    out.text = "⚠ " + (e.message ?: "Image generation failed") + "\n(Settings → Image model: use 'Load models' and pick one with 'image' in the name.)"
                } finally { busy = false; a.orbState(OrbView.S.IDLE) }
            }
            return
        }
        val lang = when (a.prefs.language) { "english" -> "Reply in English." else -> "Reply in Hinglish (Roman Hindi) unless the content needs another language." }
        val sys = "You are the tools engine of Munawar AI. ${tool.prompt}\n$lang\nDo not add [[REMEMBER]] tags."
        a.scope.launch {
            try {
                val r = if (tool.id == "research") Connectors.research(a.prefs, text)
                else Gemini.generate(key, a.prefs.model, minOf(a.prefs.temperature, 0.7f), sys, listOf(Turn("user", text.ifEmpty { "(see attachment)" })), tool.web, att)
                lastResult = if (tool.id == "website") r else r.replace("**", "")
                out.text = if (tool.id == "website") "✔ Website generated (${lastResult.length / 1024} KB). Tap “Save website” to save it to Downloads and open it.\n\n" + lastResult.take(1500) else lastResult
                if (tool.id == "email") emailBtn.visibility = View.VISIBLE
                if (tool.id == "website") htmlBtn.visibility = View.VISIBLE
            } catch (e: Exception) {
                out.text = "⚠ " + (e.message ?: "Unknown error")
            } finally {
                busy = false; a.orbState(OrbView.S.IDLE)
            }
        }
    }

    /** Preselect a tool (used by the Features screen). */
    fun select(id: String) {
        tools.firstOrNull { it.id == id }?.let { tool = it; refreshTools() }
    }

    private fun saveHtml() {
        val raw = lastResult
        val fence = Regex("```(?:html)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.get(1)
        val html = (fence ?: raw).let { h -> val i = h.indexOf("<!DOCTYPE", ignoreCase = true).let { x -> if (x >= 0) x else h.indexOf("<html", ignoreCase = true) }; if (i > 0) h.substring(i) else h }.trim()
        if (html.length < 40) { a.toast("Nothing to save yet."); return }
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "munawar_site_${System.currentTimeMillis()}.html")
                put(MediaStore.Downloads.MIME_TYPE, "text/html")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/MunawarAI")
            }
            val uri = a.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw Exception("no uri")
            a.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }
            a.toast("✔ Saved to Downloads/MunawarAI")
            try { a.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/html").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } catch (e: Exception) { a.toast("Saved. Open it from your Files app.") }
        } catch (e: Exception) { a.toast("Couldn't save the file.") }
    }

    /** Called from a voice/text command: "generate image of …". */
    fun generate(prompt: String) {
        a.toast("Image generation was removed from Munawar AI.")
    }

    private fun saveImage() {
        val b = lastImage ?: return
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "munawar_ai_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MunawarAI")
            }
            val uri = a.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: throw Exception("no uri")
            a.contentResolver.openOutputStream(uri)?.use { it.write(b) }
            a.toast("✔ Saved to Pictures/MunawarAI")
        } catch (e: Exception) { a.toast("Couldn't save the image") }
    }

    private fun speak() {
        if (lastResult.isBlank()) return
        a.orbState(OrbView.S.SPEAKING)
        a.speaker.speak(lastResult) { a.orbState(OrbView.S.IDLE) }
    }

    private fun copy() {
        if (lastResult.isBlank()) return
        (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("result", lastResult))
        a.toast("Copied")
    }

    private fun saveMemory() {
        if (lastResult.isBlank()) return
        val f = a.field("Memory text", multi = true)
        f.setText(lastResult.take(600))
        AlertDialog.Builder(a).setTitle("Save to memory").setView(f)
            .setPositiveButton("Save") { _, _ -> val t = f.text.toString().trim(); if (t.isNotEmpty()) { a.memory.add(t); a.toast("Saved") } }
            .setNegativeButton("Cancel", null).show()
    }

    private fun openEmail() {
        val lines = lastResult.lines()
        val subject = lines.firstOrNull { it.startsWith("Subject:", true) }?.substringAfter(":")?.trim() ?: "Message"
        val body = lines.filterNot { it.startsWith("Subject:", true) }.joinToString("\n").trim()
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)
        try { a.startActivity(i) } catch (e: Exception) { a.toast("No email app found.") }
    }

    override fun onHide() { a.speaker.stop() }
}
