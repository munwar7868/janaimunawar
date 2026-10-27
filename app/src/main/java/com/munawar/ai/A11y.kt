package com.munawar.ai

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Optional accessibility control. Only active when the user enabled it in Android Settings AND the in-app
 * "Allow actions" switch is on. Acts only when a command is given; it does not record or log screen content.
 * Password fields are never read or typed into.
 */
class MunawarAccessibility : AccessibilityService() {
    private val guardH = android.os.Handler(android.os.Looper.getMainLooper())
    private val guardTick = object : Runnable {
        override fun run() {
            try { Guardian.tick(this@MunawarAccessibility) } catch (e: Exception) {}
            guardH.postDelayed(this, 1500)
        }
    }

    override fun onServiceConnected() {
        try { Core.init(this) } catch (e: Exception) {}
        inst = this
        guardH.removeCallbacks(guardTick); guardH.postDelayed(guardTick, 3000)
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        try { Triggers.onForegroundApp(pkg) } catch (x: Exception) {}
    }
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean { guardH.removeCallbacks(guardTick); if (inst === this) inst = null; return super.onUnbind(intent) }
    override fun onDestroy() { guardH.removeCallbacks(guardTick); if (inst === this) inst = null; super.onDestroy() }

    companion object { @Volatile var inst: MunawarAccessibility? = null }

    fun screenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        walk(root, sb)
        return sb.toString().take(6000)
    }

    private fun walk(n: AccessibilityNodeInfo, sb: StringBuilder) {
        if (sb.length > 6000 || !n.isVisibleToUser || n.isPassword) return
        val t = n.text?.toString() ?: n.contentDescription?.toString()
        if (!t.isNullOrBlank()) sb.append(t.trim()).append('\n')
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, sb) }
    }

    /** What is on screen right now: "kind: label" lines (click / edit / text), for the autonomous agent. */
    fun elements(): String {
        val root = rootInActiveWindow ?: return "(no window is readable)"
        val sb = StringBuilder("app=").append(root.packageName).append('\n')
        var count = 0
        fun walk(x: AccessibilityNodeInfo) {
            if (count >= 90 || !x.isVisibleToUser || x.isPassword) return
            val label = (x.text?.toString() ?: x.contentDescription?.toString() ?: "").trim().replace('\n', ' ').take(70)
            val kind = if (x.isEditable) "edit" else if (x.isClickable) "click" else "text"
            if (label.isNotEmpty() || x.isEditable) {
                val r = android.graphics.Rect(); x.getBoundsInScreen(r)
                val dm = resources.displayMetrics
                sb.append(kind).append(": ").append(label.ifEmpty { x.hintText?.toString() ?: "input" })
                    .append(" @(").append("%.2f".format(r.centerX() / dm.widthPixels.toFloat())).append(',').append("%.2f".format(r.centerY() / dm.heightPixels.toFloat())).append(")\n")
                count++
            }
            for (i in 0 until x.childCount) x.getChild(i)?.let { walk(it) }
        }
        walk(root)
        return sb.toString()
    }

    /** True only if the target app accepted the click. */
    fun clickText(q: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var fallback: AccessibilityNodeInfo? = null
        for (n in root.findAccessibilityNodeInfosByText(q)) {
            if (n.isPassword) continue
            var c: AccessibilityNodeInfo? = n
            while (c != null && !c.isClickable) c = c.parent
            if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            if (fallback == null && n.isVisibleToUser) fallback = n
        }
        // The button exists but is not "clickable" for accessibility (custom views, games, video players): tap where it is.
        val f = fallback ?: return false
        return tapNode(f)
    }

    /** Taps the centre of a node with a real finger gesture. */
    fun tapNode(n: AccessibilityNodeInfo): Boolean {
        val r = android.graphics.Rect()
        n.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) return false
        val dm = resources.displayMetrics
        return tapAt(r.centerX() / dm.widthPixels.toFloat(), r.centerY() / dm.heightPixels.toFloat())
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: findEditable(root) ?: return false
        if (f.isPassword) return false
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun findEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findEditable(c)?.let { return it } }
        return null
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val s = findScrollable(root) ?: return false
        return s.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private fun findScrollable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isScrollable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findScrollable(c)?.let { return it } }
        return null
    }

    /** Clicks a button whose text/description equals one of [names] (e.g. WhatsApp "Send"). True if the app accepted the click. */
    fun clickByLabel(names: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (name in names) {
            for (n in root.findAccessibilityNodeInfosByText(name)) {
                val d = (n.contentDescription?.toString() ?: n.text?.toString() ?: "").trim()
                if (!d.equals(name, true) || n.isPassword) continue
                var c: AccessibilityNodeInfo? = n
                while (c != null && !c.isClickable) c = c.parent
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
        }
        return false
    }

    /** On a YouTube results page: taps the first video result. */
    fun clickFirstVideo(): Boolean {
        val root = rootInActiveWindow ?: return false
        val n = findVideo(root) ?: return false
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        return c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private val videoRe = Regex("(?i)(play video|\\d+ (minutes?|seconds?|hours?)|views|मिनट|सेकंड)")

    private fun findVideo(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val d = n.contentDescription?.toString()
        if (d != null && d.length > 20 && n.isVisibleToUser && !d.contains("shorts", true) && videoRe.containsMatchIn(d)) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findVideo(c)?.let { return it } }
        return null
    }

    /** Quick-settings tile helper: toggles the tile to [want] (on/off). Null = tile not found / click refused. */
    fun toggleTile(labels: List<String>, want: Boolean): String? {
        val root = rootInActiveWindow ?: return null
        for (q in labels) {
            for (n in root.findAccessibilityNodeInfosByText(q)) {
                var c: AccessibilityNodeInfo? = n
                while (c != null && !c.isClickable) c = c.parent
                val node = c ?: continue
                val sd: String = if (Build.VERSION.SDK_INT >= 30) (node.stateDescription?.toString() ?: "") else ""
                val d = sd + " " + (node.contentDescription?.toString() ?: "") + " " + (n.text?.toString() ?: "")
                val ld = " " + d.lowercase().replace(Regex("[^a-z ]"), " ") + " "
                val on: Boolean? = if (node.isCheckable) node.isChecked else if (ld.contains(" off ")) false else if (ld.contains(" on ")) true else null
                if (on == want) return "already " + (if (want) "on" else "off")
                return if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) "toggled" else null
            }
        }
        return null
    }

    fun global(action: Int): Boolean = performGlobalAction(action)

    private fun gesture(p: android.graphics.Path, d: Long): Boolean =
        dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(p, 0, d)).build(), null, null)

    /** Finger swipe: up/down/left/right across the screen. */
    fun swipe(dir: String): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat()
        val p = android.graphics.Path()
        when (dir) {
            "up" -> { p.moveTo(w / 2, h * 0.75f); p.lineTo(w / 2, h * 0.25f) }
            "down" -> { p.moveTo(w / 2, h * 0.25f); p.lineTo(w / 2, h * 0.75f) }
            "left" -> { p.moveTo(w * 0.8f, h / 2); p.lineTo(w * 0.2f, h / 2) }
            else -> { p.moveTo(w * 0.2f, h / 2); p.lineTo(w * 0.8f, h / 2) }
        }
        return gesture(p, 300)
    }

    /** Tap at a screen position (fractions 0..1), once, twice, or long-press. */
    fun tapAt(fx: Float, fy: Float, count: Int = 1, hold: Boolean = false): Boolean {
        val dm = resources.displayMetrics
        val x = fx * dm.widthPixels; val y = fy * dm.heightPixels
        val b = android.accessibilityservice.GestureDescription.Builder()
        for (i in 0 until count) {
            val p = android.graphics.Path(); p.moveTo(x, y); p.lineTo(x + 1f, y)
            b.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(p, i * 110L, if (hold) 800L else 60L))
        }
        return dispatchGesture(b.build(), null, null)
    }

    val isLandscape: Boolean get() = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels

    fun screenshot(cb: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) { cb(null); return }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    val hb = r.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                    val sw = bmp?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    cb(sw)
                }
                override fun onFailure(code: Int) { cb(null) }
            })
        } catch (e: Exception) { cb(null) }
    }
}

object A11y {
    @Volatile var allowed = true
    const val NEEDS = "Turn on Accessibility control first: Control tab → Accessibility (and switch 'Allow actions' on)."
    fun svc(): MunawarAccessibility? = if (allowed) MunawarAccessibility.inst else null
}

/** Explains whatever is on screen using a screenshot (Android 11+) or the screen text. */
object ScreenAI {
    suspend fun explain(prefs: Prefs, question: String): Pair<Boolean, String> {
        val key = prefs.apiKey() ?: return Pair(false, "Add your Gemini API key in Settings first.")
        val s = A11y.svc() ?: return Pair(false, A11y.NEEDS)
        var att: Attachment? = null
        if (Build.VERSION.SDK_INT >= 30) {
            val bmp = withTimeoutOrNull(4000) {
                suspendCancellableCoroutine<Bitmap?> { c -> s.screenshot { if (c.isActive) c.resume(it) } }
            }
            if (bmp != null) att = Attachment("image/jpeg", Media.bitmapToB64(bmp))
        }
        var text = ""
        if (att == null) {
            text = s.screenText()
            if (text.isBlank()) return Pair(false, "Couldn't read this screen (some apps block screen access).")
        }
        val prompt = if (att != null) "Explain what is on my phone screen and what I can do here. Request: $question"
        else "This is the text on my phone screen:\n$text\n\nExplain it simply. Request: $question"
        return try {
            Pair(true, Gemini.generate(key, prefs.model, 0.5f, Persona.build(prefs, emptyList(), false, true), listOf(Turn("user", prompt)), false, att))
        } catch (e: GeminiException) { Pair(false, e.message ?: "Failed") }
    }
}
