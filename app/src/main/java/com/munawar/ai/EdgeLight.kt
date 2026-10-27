package com.munawar.ai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.min

private val defaultColors = intArrayOf(
    Color.parseColor("#22D3EE"), Color.parseColor("#3B82F6"), Color.parseColor("#8B5CF6"), Color.parseColor("#F472B6"),
    Color.parseColor("#F43F5E"), Color.parseColor("#F59E0B"), Color.parseColor("#84CC16"), Color.parseColor("#22D3EE")
)

/** Colourful "edge lighting" that runs around the whole screen border, matched to the phone's rounded corners. */
class EdgeLightView(ctx: Context) : View(ctx) {
    var state: OrbView.S = OrbView.S.IDLE
    @Volatile var level = 0f
    var lightOn = true
    var thicknessDp = 5
    var speed = 2
    var glow = 1f
    var style = "run"
    private var pulseT = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var sweep: SweepGradient? = null
    private var anim: ValueAnimator? = null
    private var angle = 0f
    private var last = System.nanoTime()
    private var corner = 0f
    private var lite = false
    private var skip = 0
    private var colors = defaultColors

    /** Called when Settings change the palette. */
    fun setPalette(c: IntArray) { colors = c; rebuild() }

    fun setAnimStyle(st: String) { style = st; rebuild() }

    private fun rebuild() {
        if (width > 0 && height > 0) sweep = makeSweep(width, height)
        invalidate()
    }

    private fun makeSweep(w: Int, h: Int): SweepGradient {
        val cx = w / 2f; val cy = h / 2f
        val a = colors[0]; val b = colors[colors.size / 2]
        return when (style) {
            "comet" -> SweepGradient(cx, cy, intArrayOf(0x00000000, 0x00000000, a, b, Color.WHITE, 0x00000000), floatArrayOf(0f, 0.5f, 0.78f, 0.93f, 0.985f, 1f))
            "dual" -> SweepGradient(cx, cy, colors + colors.drop(1), null)
            "snake" -> SweepGradient(cx, cy, intArrayOf(0x00000000, 0x00000000, a, Color.WHITE, 0x00000000, 0x00000000, b, Color.WHITE, 0x00000000), floatArrayOf(0f, 0.30f, 0.43f, 0.49f, 0.5f, 0.80f, 0.93f, 0.99f, 1f))
            "rainbow" -> SweepGradient(cx, cy, defaultColors, null)
            "wave" -> {
                val wc = IntArray(colors.size * 2 + 1)
                for (i in colors.indices) { wc[i * 2] = colors[i]; wc[i * 2 + 1] = (colors[i] and 0x00FFFFFF) or 0x30000000 }
                wc[wc.size - 1] = wc[0]
                SweepGradient(cx, cy, wc, null)
            }
            else -> SweepGradient(cx, cy, colors, null)
        }
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lite = Core.prefs.liteMode
        if (Build.VERSION.SDK_INT >= 31) {
            val ins: WindowInsets? = rootWindowInsets
            val r = ins?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius
            if (r != null && r > 0) corner = r.toFloat()
        }
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { if (!lite || (++skip and 1) == 0) invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }

    private val shaderMatrix = android.graphics.Matrix()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sweep = makeSweep(w, h)
    }

    override fun onDraw(c: Canvas) {
        if (!lightOn) return
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val sg = sweep ?: return
        val now = System.nanoTime()
        val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
        last = now
        val base = when (state) { OrbView.S.IDLE -> 80f; OrbView.S.LISTENING -> 240f; OrbView.S.THINKING -> 460f; OrbView.S.SPEAKING -> 320f }
        pulseT += dt
        if (style != "static" && style != "pulse" && style != "heartbeat") {
            val mul = if (style == "rainbow") 1.8f else if (style == "wave") 0.8f else 1f
            angle = (angle + dt * (base + level * 260f) * (0.5f + speed * 0.5f) * mul) % 360f
        }
        val t = dp(thicknessDp) * (1f + level * 0.9f)
        val r = if (corner > 0f) corner else max(dp(28), min(w, h) * 0.075f)
        val inset = t * 1.6f + 1f
        rect.set(inset, inset, w - inset, h - inset)
        // FIX: rotate the colours (shader), NOT the canvas. Rotating the canvas spun the frame off-screen.
        shaderMatrix.setRotate(angle, w / 2f, h / 2f)
        sg.setLocalMatrix(shaderMatrix)
        paint.shader = sg
        val pulse = when (style) {
            "pulse" -> 0.55f + 0.45f * sin(pulseT * (1.5f + speed))
            "heartbeat" -> {
                val ph = (pulseT * 1.1f) % 1f
                val a1 = Math.pow(Math.abs(Math.sin(ph * Math.PI * 2)), 12.0)
                val a2 = Math.pow(Math.abs(Math.sin(ph * Math.PI * 2 + 1.0)), 12.0) * 0.6
                (0.2 + 0.8 * minOf(1.0, a1 + a2)).toFloat()
            }
            else -> 1f
        }
        val boost = (if (state == OrbView.S.IDLE) 0.6f else 1f) * glow * pulse
        paint.strokeWidth = t * 3.4f; paint.alpha = (40 * boost).toInt().coerceIn(0, 255); c.drawRoundRect(rect, r, r, paint)
        paint.strokeWidth = t * 2.0f; paint.alpha = (95 * boost).toInt().coerceIn(0, 255); c.drawRoundRect(rect, r, r, paint)
        paint.strokeWidth = t; paint.alpha = (255 * boost).toInt().coerceIn(0, 255); c.drawRoundRect(rect, r, r, paint)
        // thin bright core line for a neon look
        paint.strokeWidth = max(1f, t * 0.28f); paint.alpha = (235 * boost).toInt().coerceIn(0, 255)
        c.drawRoundRect(rect, r, r, paint)
    }

    private fun dp(v: Int) = v * resources.displayMetrics.density
}

/** System-wide edge light (above every app) when 'Display over other apps' is allowed. Also keeps a visible overlay window while a session runs, which lets Android allow background app launches. */
object EdgeLight {
    private var view: View? = null
    private var appCtx: Context? = null
    private var fullMode = false
    val systemShown: Boolean get() = view != null
    val fullShown: Boolean get() = view != null && fullMode

    fun show(ctx: Context) {
        if (view != null || !Perms.overlay(ctx)) return
        val app = ctx.applicationContext
        appCtx = app
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val wantFull = Core.prefs.edgeLight && Core.prefs.edgeOverlay
        val v: View = if (wantFull) EdgeLightView(app).also { apply(it) } else View(app)
        fullMode = wantFull
        val (w, h) = if (wantFull) realSize(app, wm) else Pair(1, 1)
        val lp = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Android 12+ drops touches for every other app when an overlay of ANOTHER app is more than 80% opaque.
            // Keeping the window alpha at 0.79 (below that limit) is what keeps touch working everywhere.
            alpha = 0.79f
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try { wm.addView(v, lp); view = v } catch (e: Exception) { view = null }
    }

    fun hide() {
        val v = view ?: return
        try { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (e: Exception) {}
        view = null
    }

    fun refreshPrefs() {
        val v = view ?: return
        val wantFull = Core.prefs.edgeLight && Core.prefs.edgeOverlay
        if (wantFull != fullMode) { val c = appCtx; hide(); if (c != null) show(c); return }
        if (v is EdgeLightView) apply(v)
    }

    fun update(s: OrbView.S, level: Float) { (view as? EdgeLightView)?.let { it.state = s; it.level = level } }

    private fun apply(v: EdgeLightView) {
        val p = Core.prefs
        v.lightOn = p.edgeLight; v.thicknessDp = p.edgeThickness; v.speed = p.edgeSpeed; v.glow = p.edgeGlowPct / 100f; v.setAnimStyle(p.edgeStyle)
        v.setPalette(when (p.edgeColors) {
            "accent" -> intArrayOf(Theme.PURPLE, Theme.CYAN, Theme.PINK, Theme.PURPLE)
            "orb" -> OrbDesigns.colors(p.orbPal)
            else -> defaultColors
        })
    }

    @Suppress("DEPRECATION")
    private fun realSize(ctx: Context, wm: WindowManager): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) { val b = wm.currentWindowMetrics.bounds; return Pair(b.width(), b.height()) }
        val m = android.util.DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        return Pair(m.widthPixels, m.heightPixels)
    }
}
