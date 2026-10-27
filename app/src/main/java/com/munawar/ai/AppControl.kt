package com.munawar.ai

import android.Manifest
import android.content.Intent
import android.provider.MediaStore
import kotlinx.coroutines.delay

/**
 * Eyes + hands: look at the screen / camera, record video, and control other apps (YouTube skip ad / like / subscribe /
 * comment / seek, swipes, taps). Works through the Accessibility service, so turn it on in ☰ → Control center.
 */
object AppControl {
    private fun ok(m: String) = Automation.Result(true, m)
    private fun no(m: String) = Automation.Result(false, m)

    private const val AD = "(?:ads?|add|advertisements?|एड|ऐड|विज्ञापन)"
    private const val SKIP = "(?:skip|स्किप|छोड़ें|छोड़ो)"
    private const val DO = "(?:\\s+(?:karo|kar do|kardo|करो|कर दो))?"

    private fun anyClick(s: MunawarAccessibility, names: List<String>): Boolean = names.any { s.clickText(it) }

    /** Removes filler such as "please", "youtube pe", "karo" so short commands match. */
    private fun clean(x: String): String = x
        .replace(Regex("^(?:please |plz |hey munawar |munawar )+"), "")
        .replace(Regex("^(?:youtube|yt|video)\\s+(?:pe|par|me|mein|mai|ko)\\s+"), "")
        .replace(Regex("\\s+(?:karo|kar do|kardo|kar de|please|plz|now|abhi|करो|कर दो)$"), "")
        .trim()

    suspend fun handle(a: Host, l0: String, t: String): Automation.Result? {
        val l = clean(l0)
        // ---------- autonomous agent ----------
        if (Regex("^(?:stop|cancel|abort) (?:the |my )?(?:task|agent|autopilot)$").matches(l)) { Agent.cancel(); return ok("Stopping the task after this step.") }
        Regex("^(?:do|agent|autopilot|task:)\\s+(.+)$").find(l)?.let {
            val goal = Regex("(?i)^(?:do|agent|autopilot|task:)\\s+(.+)$").find(t.trim())?.groupValues?.get(1) ?: it.groupValues[1]
            val r = Agent.run(a, goal)
            return Automation.Result(r.ok, r.message)
        }

        // ---------- eyes: screen ----------
        Regex("^(?:please )?(?:look at|see|check|describe|explain|analy[sz]e|dekho|dekh)\\s+(?:at\\s+)?(?:my\\s+|the\\s+|this\\s+)?(?:phone\\s+)?screen\\b\\s*(.*)$").find(l)?.let { return lookScreen(a, it.groupValues[1]) }
        Regex("^what(?:'s| is| is there) on (?:my |the )?(?:phone )?screen\\b.*$").find(l)?.let { return lookScreen(a, t) }
        Regex("^(?:meri )?screen (?:dekho|dekh)\\b\\s*(.*)$").find(l)?.let { return lookScreen(a, it.groupValues[1]) }

        // ---------- eyes: camera ----------
        Regex("^(?:please )?(?:look at|see|check|describe|dekho|dekh)\\s+(?:through |with |using |via )?(?:my |the )?(front |selfie |back |rear )?camera\\b\\s*(.*)$").find(l)?.let {
            return lookCamera(a, it.groupValues[1].contains("front") || it.groupValues[1].contains("selfie"), it.groupValues[2])
        }
        Regex("^look at (?:me|my face)\\b\\s*(.*)$").find(l)?.let { return lookCamera(a, true, it.groupValues[1]) }
        if (Regex("^what do you see\\b.*$|^what(?:'s| is) in front of (?:me|you|the camera)\\b.*$|^camera (?:dekho|dekh)\\b.*$").matches(l)) return lookCamera(a, false, t)

        // ---------- continuous vision ----------
        Regex("^(?:start |keep )?(?:watching|watch|seeing)\\s+(?:my\\s+)?(screen|front camera|selfie camera|back camera|camera)$").find(l)?.let {
            val w = it.groupValues[1]
            return setVision(a, if (w == "screen") "screen" else if (w.startsWith("front") || w.startsWith("selfie")) "camera_front" else "camera")
        }
        if (Regex("^(?:live )?vision off$|^stop (?:watching|seeing|looking)(?: my)?(?: screen| camera)?$").matches(l)) return setVision(a, "off")
        if (Regex("^(?:live )?vision on$").matches(l)) return setVision(a, "screen")

        // ---------- camera / video ----------
        if (Regex("^(?:open camera and |please )?(?:record|start recording|shoot|take)\\s+(?:a\\s+)?video$|^video (?:record|banao|bana)\\b.*$|^start (?:video )?recording$").matches(l)) return recordVideo(a)
        if (Regex("^stop (?:video )?recording$|^recording (?:band|stop)\\b.*$").matches(l)) {
            val s = A11y.svc() ?: return no(A11y.NEEDS)
            return if (anyClick(s, listOf("Stop recording", "Stop video", "Stop", "Shutter", "Done"))) ok("Recording stopped.") else no("I couldn't find the stop button. Tap it yourself.")
        }
        if (Regex("^(?:take (?:a )?)?selfie$").matches(l)) return selfie(a)

        // ---------- YouTube & app buttons ----------
        if (l.split(" ").size <= 8 && Regex("(?:$SKIP\\s+(?:the\\s+|this\\s+|is\\s+)?$AD|$AD\\s+(?:ko\\s+|is\\s+)?$SKIP)").containsMatchIn(l)) {
            val s = A11y.svc() ?: return no(A11y.NEEDS)
            return if (anyClick(s, listOf("Skip ads", "Skip ad", "Skip Ads", "Skip video", "Skip", "विज्ञापन छोड़ें"))) ok("Ad skipped.")
            else no("No Skip button yet. YouTube lets you skip only after about 5 seconds; try again in a moment.")
        }
        if (Regex("^(?:like|like karo|like kar do|लाइक(?: करो)?)(?: (?:this |the )?(?:video|it))?$").matches(l)) {
            val s = A11y.svc() ?: return no(A11y.NEEDS)
            return if (anyClick(s, listOf("like this video", "Like"))) ok("Liked.") else no("Couldn't find the Like button on this screen.")
        }
        if (Regex("^dislike(?: (?:this |the )?(?:video|it))?$").matches(l)) {
            val s = A11y.svc() ?: return no(A11y.NEEDS)
            return if (anyClick(s, listOf("dislike this video", "Dislike"))) ok("Disliked.") else no("Couldn't find the Dislike button.")
        }
        if (Regex("^(?:subscribe|subscribe karo|सब्सक्राइब(?: करो)?)(?: (?:to )?(?:this )?(?:channel))?$").matches(l)) {
            val s = A11y.svc() ?: return no(A11y.NEEDS)
            return if (anyClick(s, listOf("Subscribe to", "Subscribe"))) ok("Subscribe tapped.") else no("Couldn't find the Subscribe button (maybe you are already subscribed).")
        }
        if (Regex("^(?:open |show |see )?comments?$").matches(l)) {
            val s = A11y.svc() ?: return no(A11y.NEEDS)
            return if (anyClick(s, listOf("Comments", "Comment"))) ok("Comments opened.") else no("Couldn't find the Comments button.")
        }
        Regex("^(?:write |post |add |type )?comment\\s*[:\\-]?\\s+(.+)$|^comment (?:karo|kar do)\\s+(.+)$|^(?:कमेंट|कमैंट)(?: करो)?\\s+(.+)$").find(l)?.let { m ->
            val low = (1..3).map { m.groupValues[it] }.firstOrNull { it.isNotBlank() } ?: return null
            val txt = Regex("(?i)(?:comment|कमेंट|कमैंट)(?:\\s+(?:karo|kar do|करो))?\\s*[:\\-]?\\s+(.+)$").find(t)?.groupValues?.get(1)?.trim() ?: low
            return comment(txt)
        }
        if (Regex("^next video$|^agla video$").matches(l)) return click(listOf("Next video", "Next"), "Next video.")
        if (Regex("^(?:previous|last|pichla) video$").matches(l)) return click(listOf("Previous video", "Previous"), "Previous video.")
        if (Regex("^(?:enter )?full ?screen$").matches(l)) return click(listOf("Enter fullscreen", "Full screen", "Fullscreen"), "Fullscreen.")
        if (Regex("^exit full ?screen$").matches(l)) return click(listOf("Exit fullscreen", "Exit full screen"), "Exited fullscreen.")

        // ---------- seek by double tap (10 s each) ----------
        Regex("^(?:seek |skip |jump |fast )?(?:forward|ahead)(?:\\s+(\\d+)\\s*(?:seconds?|secs?|s))?$").find(l)?.let { return seek(true, it.groupValues[1]) }
        Regex("^(?:seek|jump|skip)\\s*(?:back|backward)(?:\\s+(\\d+)\\s*(?:seconds?|secs?|s))?$|^rewind(?:\\s+(\\d+)\\s*(?:seconds?|secs?|s))?$").find(l)?.let {
            return seek(false, it.groupValues[1].ifEmpty { it.groupValues[2] })
        }

        // ---------- finger gestures ----------
        Regex("^swipe\\s+(up|down|left|right)$").find(l)?.let {
            val s = A11y.svc() ?: return no(A11y.NEEDS)
            return Automation.Result(s.swipe(it.groupValues[1]), "Swiped ${it.groupValues[1]}.")
        }
        if (Regex("^(?:tap|click|press)\\s+(?:on\\s+)?(?:the\\s+)?(?:screen\\s+)?(?:center|centre|middle)$").matches(l)) {
            val s = A11y.svc() ?: return no(A11y.NEEDS)
            return Automation.Result(s.tapAt(0.5f, 0.5f), "Tapped the centre.")
        }
        Regex("^double[- ]?tap(?:\\s+(left|right|center|centre))?$").find(l)?.let {
            val s = A11y.svc() ?: return no(A11y.NEEDS)
            val fx = when (it.groupValues[1]) { "left" -> 0.2f; "right" -> 0.8f; else -> 0.5f }
            return Automation.Result(s.tapAt(fx, if (s.isLandscape) 0.5f else 0.28f, 2), "Double-tapped.")
        }
        if (Regex("^long[- ]?press(?:\\s+(?:the\\s+)?(?:screen|center|centre))?$").matches(l)) {
            val s = A11y.svc() ?: return no(A11y.NEEDS)
            return Automation.Result(s.tapAt(0.5f, 0.5f, 1, true), "Long-pressed the centre.")
        }
        return null
    }

    private fun click(names: List<String>, msg: String): Automation.Result {
        val s = A11y.svc() ?: return no(A11y.NEEDS)
        return if (anyClick(s, names)) ok(msg) else no("Couldn't find that button on this screen.")
    }

    private fun seek(forward: Boolean, secs: String): Automation.Result {
        val s = A11y.svc() ?: return no(A11y.NEEDS)
        val n = ((secs.toIntOrNull() ?: 10) / 10).coerceIn(1, 12)
        val fx = if (forward) 0.82f else 0.18f
        val fy = if (s.isLandscape) 0.5f else 0.28f
        // YouTube: double-tap = +/-10 s, and every extra tap adds another 10 s
        val done = s.tapAt(fx, fy, 1 + n)
        return Automation.Result(done, (if (forward) "Forward " else "Back ") + "${n * 10} s.")
    }

    private suspend fun comment(text: String): Automation.Result {
        val s = A11y.svc() ?: return no(A11y.NEEDS)
        if (!anyClick(s, listOf("Add a comment", "Add comment"))) {
            if (!anyClick(s, listOf("Comments"))) return no("Couldn't find the Comments button. Open a YouTube video first.")
            delay(1500)
            if (!anyClick(s, listOf("Add a comment", "Add comment"))) return no("Comments opened, but I couldn't find the “Add a comment” box. Scroll a little and say it again.")
        }
        delay(1000)
        if (!s.typeText(text)) return no("Couldn't type into the comment box.")
        delay(700)
        return if (anyClick(s, listOf("Send comment", "Send", "Post", "Comment"))) ok("Comment posted: “$text”") else no("Typed the comment but couldn't find Send. Tap Send yourself.")
    }

    // ---------------- eyes ----------------
    private suspend fun lookScreen(a: Host, q: String): Automation.Result {
        if (A11y.svc() == null) return no(A11y.NEEDS)
        val r = ScreenAI.explain(a.prefs, q.ifBlank { "Describe what is on my screen and what I can do here." })
        return Automation.Result(r.first, r.second)
    }

    private suspend fun lookCamera(a: Host, front: Boolean, q: String): Automation.Result {
        if (!Perms.has(a, Manifest.permission.CAMERA)) {
            a.requestPerm(Manifest.permission.CAMERA) { g -> a.toast(if (g) "Camera allowed. Ask again." else "Camera permission denied.") }
            return no("I need Camera permission to look. Allow it, then ask again.")
        }
        val jpg = Vision.cameraJpeg(a, front)
            ?: return no("I couldn't open the camera. Android only lets me use it while Munawar is open on screen (or its service was started with the camera allowed). Open Munawar and try again.")
        return ok(Vision.ask(a.prefs, jpg, q, if (front) "the front (selfie) camera" else "the back camera"))
    }

    private fun setVision(a: Host, mode: String): Automation.Result {
        if (mode != "off" && mode != "screen" && !Perms.has(a, Manifest.permission.CAMERA)) {
            a.requestPerm(Manifest.permission.CAMERA) { g -> a.toast(if (g) "Camera allowed. Say it again." else "Camera permission denied.") }
            return no("I need Camera permission first. Allow it, then say it again.")
        }
        if (mode == "screen" && A11y.svc() == null) return no(A11y.NEEDS)
        a.prefs.visionMode = mode
        AssistantService.send(a, AssistantService.A_VISION)
        return ok(when (mode) {
            "off" -> "Vision off."
            "screen" -> "👁 Now watching your screen live."
            "camera_front" -> "👁 Now watching through the front camera."
            else -> "👁 Now watching through the back camera."
        })
    }

    private suspend fun recordVideo(a: Host): Automation.Result {
        if (!Perms.has(a, Manifest.permission.CAMERA)) {
            a.requestPerm(Manifest.permission.CAMERA) { g -> a.toast(if (g) "Camera allowed. Say it again." else "Camera permission denied.") }
            return no("I need Camera permission to record. Allow it, then say it again.")
        }
        try { a.startActivity(Intent(MediaStore.ACTION_VIDEO_CAPTURE)) } catch (e: Exception) { return no("No camera app can record video here.") }
        val s = A11y.svc() ?: return ok("Camera opened. Tap the red button to record (turn on Accessibility and I can press it for you).")
        delay(2600)
        return if (anyClick(s, listOf("Start recording", "Record video", "Record", "Shutter", "Take video", "Capture")))
            ok("🔴 Recording started. Say “stop recording” to finish.")
        else ok("Camera opened. I couldn't find the record button; tap it yourself, or tell me its label.")
    }

    private suspend fun selfie(a: Host): Automation.Result {
        if (!Perms.has(a, Manifest.permission.CAMERA)) {
            a.requestPerm(Manifest.permission.CAMERA) { g -> a.toast(if (g) "Camera allowed. Say it again." else "Camera permission denied.") }
            return no("I need Camera permission. Allow it, then say it again.")
        }
        try { a.startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra("android.intent.extras.CAMERA_FACING", 1)) } catch (e: Exception) { return no("No camera app available.") }
        val s = A11y.svc() ?: return ok("Selfie camera opened. Tap the shutter.")
        delay(2600)
        return if (anyClick(s, listOf("Shutter", "Take photo", "Capture", "Take picture"))) ok("📸 Selfie taken. Confirm it in the camera app if it asks.") else ok("Selfie camera opened. Tap the shutter button.")
    }
}
