package com.munawar.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/** Camera frames without any preview UI (Camera2 + ImageReader). Needs the Camera permission. */
class CameraFeed(private val ctx: Context) {
    private var thread: HandlerThread? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    @Volatile private var listener: ((ByteArray) -> Unit)? = null
    @Volatile var running = false
        private set

    @SuppressLint("MissingPermission")
    fun start(front: Boolean, periodMs: Long, onJpeg: (ByteArray) -> Unit): Boolean {
        if (running) return true
        if (!Perms.has(ctx, Manifest.permission.CAMERA)) return false
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want } ?: return false
            val sensor = cm.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            listener = onJpeg
            val t = HandlerThread("munawar-cam"); t.start(); thread = t
            val h = Handler(t.looper)
            val ir = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2); reader = ir
            ir.setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage()
                if (img != null) {
                    try {
                        val buf = img.planes[0].buffer
                        val b = ByteArray(buf.remaining()); buf.get(b)
                        listener?.invoke(b)
                    } catch (e: Exception) {} finally { img.close() }
                }
            }, h)
            running = true
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    device = d
                    try {
                        @Suppress("DEPRECATION")
                        d.createCaptureSession(listOf(ir.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                session = s
                                h.post(object : Runnable {
                                    override fun run() {
                                        if (!running) return
                                        try {
                                            val rb = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                            rb.addTarget(ir.surface)
                                            rb.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                            rb.set(CaptureRequest.JPEG_ORIENTATION, sensor)
                                            s.capture(rb.build(), null, h)
                                        } catch (e: Exception) {}
                                        h.postDelayed(this, periodMs)
                                    }
                                })
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) { stop() }
                        }, h)
                    } catch (e: Exception) { stop() }
                }
                override fun onDisconnected(d: CameraDevice) { stop() }
                override fun onError(d: CameraDevice, e: Int) { stop() }
            }, h)
            true
        } catch (e: Exception) { stop(); false }
    }

    fun stop() {
        running = false
        try { session?.close() } catch (e: Exception) {}
        try { device?.close() } catch (e: Exception) {}
        try { reader?.close() } catch (e: Exception) {}
        try { thread?.quitSafely() } catch (e: Exception) {}
        session = null; device = null; reader = null; thread = null; listener = null
    }
}

/** Eyes for Munawar: screen and camera, one-shot or as a live stream. */
object Vision {
    private var feed: CameraFeed? = null

    fun jpeg(b: Bitmap, maxSide: Int = 800, q: Int = 55): ByteArray {
        val s = maxSide.toFloat() / maxOf(b.width, b.height)
        val bm = if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true) else b
        val o = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, q, o)
        return o.toByteArray()
    }

    private suspend fun screenBitmap(): Bitmap? {
        val s = A11y.svc() ?: return null
        return withTimeoutOrNull(4000) {
            suspendCancellableCoroutine<Bitmap?> { c -> s.screenshot { if (c.isActive) c.resume(it) } }
        }
    }

    /** A screenshot (needs Accessibility, Android 11+). */
    suspend fun screenJpeg(): ByteArray? {
        val bmp = screenBitmap() ?: return null
        return jpeg(bmp)
    }

    private var lastSig: IntArray? = null

    private fun signature(b: Bitmap): IntArray {
        val t = Bitmap.createScaledBitmap(b, 12, 12, true)
        val out = IntArray(144)
        for (y in 0 until 12) for (x in 0 until 12) {
            val p = t.getPixel(x, y)
            out[y * 12 + x] = (android.graphics.Color.red(p) * 3 + android.graphics.Color.green(p) * 6 + android.graphics.Color.blue(p)) / 10
        }
        return out
    }

    /** For 24-hour watching: returns a picture only when the screen really changed (saves data and battery). */
    suspend fun screenJpegIfChanged(): ByteArray? {
        val bmp = screenBitmap() ?: return null
        val sig = signature(bmp)
        val prev = lastSig
        if (prev != null) {
            var d = 0L
            for (i in sig.indices) d += kotlin.math.abs(sig[i] - prev[i])
            if (d / sig.size < 5) return null
        }
        lastSig = sig
        return jpeg(bmp)
    }

    fun forgetScreen() { lastSig = null }

    /** One camera picture. Returns null if the camera couldn't be opened. */
    suspend fun cameraJpeg(ctx: Context, front: Boolean): ByteArray? {
        val got = CompletableDeferred<ByteArray?>()
        val f = CameraFeed(ctx)
        var n = 0
        val ok = f.start(front, 700) { b ->
            n++
            if (n >= 2 && !got.isCompleted) got.complete(b) // 1st frame is often dark / unfocused
        }
        if (!ok) { f.stop(); return null }
        val r = withTimeoutOrNull(6000) { got.await() }
        f.stop()
        return r
    }

    fun startCamera(ctx: Context, front: Boolean, onJpeg: (ByteArray) -> Unit): Boolean {
        stopCamera()
        val f = CameraFeed(ctx)
        feed = f
        return f.start(front, 1500, onJpeg)
    }

    fun stopCamera() { feed?.stop(); feed = null }

    /** Ask Gemini about a picture. */
    suspend fun ask(prefs: Prefs, jpeg: ByteArray, question: String, what: String): String {
        val key = prefs.apiKey() ?: return "Add your Gemini API key in Settings first."
        val q = question.ifBlank { "Describe what you see and anything important." }
        return try {
            Gemini.generate(
                key, prefs.model, 0.4f,
                "You are Munawar AI. The user shows you $what. Answer their request precisely and briefly (2-4 sentences) in the user's language. Read any visible text if asked.",
                listOf(Turn("user", q)), false, Attachment("image/jpeg", Base64.encodeToString(jpeg, Base64.NO_WRAP))
            )
        } catch (e: GeminiException) { e.message ?: "Vision request failed." }
    }
}
