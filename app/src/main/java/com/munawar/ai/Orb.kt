package com.munawar.ai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 10 colour palettes x 8 forms = 80 orb designs. */
object OrbDesigns {
    val paletteNames = listOf("Galaxy", "Aurora", "Fire", "Neon", "Ocean", "Emerald", "Gold", "Ice", "Sunset", "Mono")
    val formNames = listOf("Logo sphere", "Crystal core", "Halo ring", "Plasma", "Ripples", "Swirl", "Hex shield", "Equalizer ring")
    private val ring = listOf(
        arrayOf("#22D3EE", "#3B82F6", "#8B5CF6", "#F472B6", "#F59E0B", "#84CC16", "#22D3EE"),
        arrayOf("#22D3EE", "#38BDF8", "#34D399", "#A78BFA", "#22D3EE"),
        arrayOf("#EF4444", "#F97316", "#FACC15", "#FB7185", "#EF4444"),
        arrayOf("#F472B6", "#C084FC", "#60A5FA", "#22D3EE", "#F472B6"),
        arrayOf("#0EA5E9", "#38BDF8", "#2DD4BF", "#6366F1", "#0EA5E9"),
        arrayOf("#10B981", "#34D399", "#A3E635", "#22D3EE", "#10B981"),
        arrayOf("#EAB308", "#FDE047", "#F97316", "#FACC15", "#EAB308"),
        arrayOf("#7DD3FC", "#E0F2FE", "#93C5FD", "#A5F3FC", "#7DD3FC"),
        arrayOf("#F97316", "#EC4899", "#FACC15", "#A855F7", "#F97316"),
        arrayOf("#E5E5E5", "#A3A3A3", "#FFFFFF", "#737373", "#E5E5E5")
    )
    private val hue = listOf(0f to 360f, 150f to 270f, 0f to 50f, 270f to 340f, 185f to 235f, 120f to 170f, 35f to 60f, 190f to 215f, 5f to 45f, 0f to 0f)

    fun colors(p: Int): IntArray = ring[p.coerceIn(0, 9)].map { Color.parseColor(it) }.toIntArray()
    fun hueRange(p: Int): Pair<Float, Float> = hue[p.coerceIn(0, 9)]
    fun saturation(p: Int): Float = if (p == 9) 0f else 0.75f
    fun number(pal: Int, form: Int) = pal * formNames.size + form + 1
    fun total() = paletteNames.size * formNames.size
}

/**
 * The Munawar AI orb. 50 designs (Settings → Orb design), 3 animation levels (Off / Calm / Full; Off is the default
 * and draws a still orb that only changes colour with the assistant state).
 * Public API: state, level, lite, standby, reloadTheme().
 */
class OrbView(ctx: Context) : View(ctx) {
    enum class S { IDLE, LISTENING, THINKING, SPEAKING }

    var state: S = S.IDLE
        set(v) { if (field != v) { field = v; invalidate() } }

    /** 0..1 live audio level. */
    @Volatile var level = 0f

    var lite = false

    var standby = false
        set(v) { field = v; invalidate() }

    private val logo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.orb_logo)
    private val logoShader = BitmapShader(logo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val logoMatrix = Matrix()
    private val sweepMatrix = Matrix()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = logoShader }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val hsv = floatArrayOf(0f, 0.75f, 1f)
    private val gHsv = floatArrayOf(0f, 0.75f, 1f)
    private val n = 64
    private val cosT = FloatArray(n) { cos(Math.toRadians(it * 360.0 / n)).toFloat() }
    private val sinT = FloatArray(n) { sin(Math.toRadians(it * 360.0 / n)).toFloat() }
    private var sweep: SweepGradient? = null
    private var orbitSweep: SweepGradient? = null
    private var glowShader: RadialGradient? = null
    private var hlShader: RadialGradient? = null
    private var shShader: RadialGradient? = null
    private var rimShader: RadialGradient? = null
    private var coreShader: RadialGradient? = null
    private var sm = 0f
    private var rot = 0f
    private var hue = 0f
    private var lastNs = System.nanoTime()
    private val t0 = lastNs
    private var anim: ValueAnimator? = null
    private var attached = false
    private var skip = 0
    private val oval = RectF()
    private val blob = Path()

    // design settings (read from Prefs)
    private var pal = 0
    private var form = 0
    private var animLevel = 0 // 0 off, 1 calm, 2 full
    private var spd = 1f
    private var showBars = false
    private var showSparkles = false
    private var showRings = false
    private var hueLo = 0f
    private var hueHi = 360f
    private var ringColors = OrbDesigns.colors(0)

    private fun loadTheme() {
        try {
            val q = Core.prefs
            pal = q.orbPal; form = q.orbForm; animLevel = q.orbAnim
            spd = q.orbSpeedPct / 100f; showBars = q.orbBars; showSparkles = q.orbSparkles; showRings = q.orbRings
        } catch (e: Exception) {}
        val hr = OrbDesigns.hueRange(pal)
        hueLo = hr.first; hueHi = hr.second
        gHsv[1] = OrbDesigns.saturation(pal); hsv[1] = OrbDesigns.saturation(pal)
        ringColors = OrbDesigns.colors(pal)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1f - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1f - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
    )

    private fun mapHue(h: Float) = hueLo + (h % 360f) / 360f * (hueHi - hueLo)

    private fun applyAnimator() {
        if (attached && animLevel > 0) {
            if (anim == null) {
                anim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1000; repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { if (!lite || (++skip and 1) == 0) invalidate() }; start()
                }
            }
        } else { anim?.cancel(); anim = null }
        invalidate()
    }

    /** Call after the orb design / animation changed in Settings. */
    fun reloadTheme() {
        loadTheme()
        if (width > 0) onSizeChanged(width, height, width, height)
        applyAnimator()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        loadTheme()
        if (width > 0) onSizeChanged(width, height, width, height)
        applyAnimator()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) anim?.resume() else anim?.pause()
    }

    override fun onDetachedFromWindow() {
        attached = false
        anim?.cancel(); anim = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cx = w / 2f; val cy = h / 2f
        val full = minOf(w, h) / 2f
        if (full <= 0f) return
        sweep = SweepGradient(cx, cy, ringColors, null)
        orbitSweep = SweepGradient(cx, cy, ringColors, null)
        glowShader = RadialGradient(cx, cy, full, intArrayOf(Color.WHITE, Color.argb(90, 255, 255, 255), Color.TRANSPARENT), floatArrayOf(0.45f, 0.7f, 1f), Shader.TileMode.CLAMP)
        val rr = full * 0.66f * 0.93f
        hlShader = RadialGradient(cx - rr * 0.38f, cy - rr * 0.42f, rr * 0.85f, intArrayOf(Color.argb(150, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        shShader = RadialGradient(cx + rr * 0.3f, cy + rr * 0.4f, rr * 1.2f, intArrayOf(Color.TRANSPARENT, Color.argb(170, 0, 0, 25)), floatArrayOf(0.5f, 1f), Shader.TileMode.CLAMP)
        rimShader = RadialGradient(cx, cy, rr, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(120, 160, 220, 255)), floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP)
        coreShader = RadialGradient(cx - rr * 0.25f, cy - rr * 0.3f, rr * 1.3f, intArrayOf(Color.WHITE, Color.argb(215, 255, 255, 255), Color.argb(150, 255, 255, 255)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    private fun orbit(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, tilt: Float, spin: Float, front: Boolean, width: Float, alpha: Int) {
        val sg = orbitSweep ?: return
        sweepMatrix.setRotate(spin, cx, cy)
        sg.setLocalMatrix(sweepMatrix)
        orbitPaint.shader = sg
        orbitPaint.strokeWidth = width
        c.save(); c.rotate(tilt, cx, cy)
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
        orbitPaint.alpha = alpha
        c.drawArc(oval, if (front) 0f else 180f, 180f, false, orbitPaint)
        c.restore()
    }

    private fun drawLogo(c: Canvas, cx: Float, cy: Float, rad: Float) {
        logoMatrix.reset()
        val sc = 2f * rad / logo.width
        logoMatrix.postScale(sc, sc)
        logoMatrix.postTranslate(cx - rad, cy - rad)
        logoShader.setLocalMatrix(logoMatrix)
        c.drawCircle(cx, cy, rad, logoPaint)
    }

    private fun tinted(col: Int, a: Int, shader: Shader?) {
        p.style = Paint.Style.FILL
        p.shader = shader
        p.colorFilter = PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN)
        p.alpha = a
    }

    private fun clearP() { p.colorFilter = null; p.shader = null; p.alpha = 255 }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val on = animLevel > 0
        val full2 = animLevel == 2
        val now = System.nanoTime()
        val dt = ((now - lastNs) / 1e9f).coerceIn(0f, 0.1f)
        lastNs = now
        val t = if (on) (now - t0) / 1e9f else 0f
        val cx = w / 2f; val cy = h / 2f
        val full = minOf(w, h) / 2f

        val target = if (!on) 0f else when (state) {
            S.SPEAKING -> maxOf(level, 0.22f + 0.2f * abs(sin(t * 9f)))
            S.LISTENING -> level
            S.THINKING -> 0.12f + 0.1f * abs(sin(t * 3f))
            S.IDLE -> 0f
        }
        sm += (target - sm) * 0.3f
        if (on) {
            val speed = when (state) { S.IDLE -> 0.06f; S.LISTENING -> 0.3f + sm * 0.6f; S.THINKING -> 0.9f; S.SPEAKING -> 0.25f }
            rot = (rot + dt * speed * spd * 360f) % 360f
            val hueSpeed = when (state) { S.IDLE -> 70f; S.LISTENING -> 130f; S.THINKING -> 280f; S.SPEAKING -> 170f }
            hue = (hue + dt * hueSpeed * spd) % 360f
        } else {
            rot = 20f
            hue = when (state) { S.IDLE -> 0f; S.LISTENING -> 90f; S.THINKING -> 180f; S.SPEAKING -> 270f }
        }
        gHsv[0] = mapHue(hue)
        val baseCol = Color.HSVToColor(gHsv)
        val mood = AssistantBus.mood
        val moodOn = mood != 0 && (System.currentTimeMillis() - AssistantBus.moodAt) < 9000L
        val col = if (moodOn) blend(baseCol, Emotion.color(mood), 0.55f) else baseCol
        val breathe = if (on) 1f + 0.025f * sin(t * 1.6f) + sm * 0.07f else 1f
        val r = full * 0.66f * breathe
        val ow = r * 0.07f + 2f

        // aura
        val glowA = when (state) { S.IDLE -> 70; S.LISTENING -> 140 + (sm * 100).toInt(); else -> 120 }.coerceAtMost(255)
        tinted(col, glowA, glowShader); c.drawCircle(cx, cy, full, p); clearP()

        // audio bars (Calm needs the toggle, Full always)
        if (on && !lite && (showBars || full2) && w > 140 * resources.displayMetrics.density) {
            barPaint.strokeWidth = full * 0.016f + 2f
            val base = 110 + (sm * 145).toInt()
            for (i in 0 until n) {
                val amp = 0.05f + sm * 0.5f * (0.5f + 0.5f * sin(t * 7f + i * 0.9f))
                val r0 = r * 1.16f; val r1 = r0 + r * amp
                hsv[0] = mapHue(i * (360f / n) + t * 50f)
                barPaint.color = Color.HSVToColor(base.coerceAtMost(255), hsv)
                c.drawLine(cx + cosT[i] * r0, cy + sinT[i] * r0, cx + cosT[i] * r1, cy + sinT[i] * r1, barPaint)
            }
        }

        val rings = on && (showRings || full2) && form == 0
        val sp1 = rot * 1.5f; val sp2 = -rot * 1.1f + 90f
        if (rings) {
            orbit(c, cx, cy, r * 1.42f, r * 0.42f, -24f, sp1, false, ow, 120)
            orbit(c, cx, cy, r * 1.30f, r * 0.34f, 32f, sp2, false, ow * 0.8f, 110)
        }

        val bw = r * 0.055f * (1f + sm * 0.8f) + 2f
        fun gradientRing(radius: Float, widthMul: Float) {
            val sg = sweep ?: return
            sweepMatrix.setRotate(rot, cx, cy); sg.setLocalMatrix(sweepMatrix)
            ringPaint.shader = sg
            ringPaint.strokeWidth = bw * 3.4f * widthMul; ringPaint.alpha = 40; c.drawCircle(cx, cy, radius, ringPaint)
            ringPaint.strokeWidth = bw * 2.0f * widthMul; ringPaint.alpha = 90; c.drawCircle(cx, cy, radius, ringPaint)
            ringPaint.strokeWidth = bw * widthMul; ringPaint.alpha = 255; c.drawCircle(cx, cy, radius, ringPaint)
        }

        val rr = r * 0.93f
        when (form) {
            1 -> { // Crystal core
                gradientRing(r, 1f)
                tinted(col, 255, coreShader); c.drawCircle(cx, cy, rr, p); clearP()
                p.style = Paint.Style.FILL
                p.shader = shShader; c.drawCircle(cx, cy, rr, p)
                p.shader = rimShader; c.drawCircle(cx, cy, rr, p)
                p.shader = hlShader; c.drawCircle(cx, cy, rr, p); p.shader = null
            }
            2 -> { // Halo ring + small logo
                gradientRing(r, 1.9f)
                gradientRing(r * 0.78f, 0.6f)
                drawLogo(c, cx, cy, r * 0.52f)
            }
            3 -> { // Plasma blob
                gradientRing(r * 1.04f, 0.6f)
                for (layer in 0..1) {
                    blob.reset()
                    val k = 56
                    for (i in 0..k) {
                        val a = (i * 2.0 * Math.PI / k).toFloat()
                        val wob = 1f + 0.07f * sin(a * 3f + t * 2.2f + layer) + 0.045f * sin(a * 5f - t * 3.1f) + sm * 0.08f * sin(a * 7f + t * 6f)
                        val rad = rr * (0.96f - layer * 0.12f) * wob
                        val x = cx + cos(a) * rad; val y = cy + sin(a) * rad
                        if (i == 0) blob.moveTo(x, y) else blob.lineTo(x, y)
                    }
                    blob.close()
                    p.style = Paint.Style.FILL
                    tinted(if (layer == 0) col else Color.WHITE, if (layer == 0) 230 else 70, coreShader)
                    c.drawPath(blob, p); clearP()
                }
                drawLogo(c, cx, cy, r * 0.5f)
            }
            4 -> { // Ripples
                for (i in 0..2) {
                    val ph = if (on) ((t * 0.55f + i / 3f) % 1f) else (0.15f + i * 0.3f)
                    ringPaint.shader = null
                    ringPaint.color = col
                    ringPaint.strokeWidth = bw * 0.7f
                    ringPaint.alpha = ((1f - ph) * 210).toInt().coerceIn(0, 255)
                    c.drawCircle(cx, cy, r * (0.62f + 0.75f * ph), ringPaint)
                }
                gradientRing(r * 0.7f, 0.8f)
                drawLogo(c, cx, cy, r * 0.62f)
            }
            5 -> { // Swirl: three spiral arms
                gradientRing(r * 1.02f, 0.5f)
                val sg = sweep
                if (sg != null) {
                    sweepMatrix.setRotate(rot, cx, cy); sg.setLocalMatrix(sweepMatrix)
                    ringPaint.shader = sg
                    for (arm in 0..2) {
                        blob.reset()
                        val steps = 36
                        for (i in 0..steps) {
                            val f = i / steps.toFloat()
                            val ang = Math.toRadians(arm * 120.0 + f * 520.0 + rot * 1.2).toFloat()
                            val rad = rr * (0.12f + 0.86f * f)
                            val x = cx + cos(ang) * rad; val y = cy + sin(ang) * rad
                            if (i == 0) blob.moveTo(x, y) else blob.lineTo(x, y)
                        }
                        ringPaint.strokeWidth = bw * 1.6f; ringPaint.alpha = 230
                        c.drawPath(blob, ringPaint)
                    }
                }
                drawLogo(c, cx, cy, r * 0.42f)
            }
            6 -> { // Hex shield
                for (k in 0..1) {
                    blob.reset()
                    val rad = r * (if (k == 0) 1.0f else 0.76f)
                    val off = if (k == 0) rot else -rot * 0.8f + 30f
                    for (i in 0..6) {
                        val ang = Math.toRadians(i * 60.0 + off).toFloat()
                        val x = cx + cos(ang) * rad; val y = cy + sin(ang) * rad
                        if (i == 0) blob.moveTo(x, y) else blob.lineTo(x, y)
                    }
                    blob.close()
                    val sg = sweep
                    if (sg != null) { sweepMatrix.setRotate(rot, cx, cy); sg.setLocalMatrix(sweepMatrix); ringPaint.shader = sg }
                    ringPaint.strokeWidth = bw * (if (k == 0) 1.8f else 1.0f)
                    ringPaint.alpha = if (k == 0) 255 else 170
                    c.drawPath(blob, ringPaint)
                }
                tinted(col, 60, glowShader); c.drawCircle(cx, cy, rr * 0.8f, p); clearP()
                drawLogo(c, cx, cy, r * 0.5f)
            }
            7 -> { // Equalizer ring
                val k = 48
                barPaint.strokeWidth = r * 0.07f + 2f
                for (i in 0 until k) {
                    val a = (i * 2.0 * Math.PI / k).toFloat()
                    val amp = if (on) 0.08f + (0.12f + sm * 0.55f) * (0.5f + 0.5f * sin(t * 6f + i * 0.7f)) else 0.14f + 0.08f * ((i % 5) / 4f)
                    hsv[0] = mapHue(i * (360f / k) + hue)
                    barPaint.color = Color.HSVToColor(235, hsv)
                    val r0 = r * 0.82f; val r1 = r0 + r * amp * 1.6f
                    c.drawLine(cx + cos(a) * r0, cy + sin(a) * r0, cx + cos(a) * r1, cy + sin(a) * r1, barPaint)
                }
                drawLogo(c, cx, cy, r * 0.6f)
            }
            else -> { // Logo sphere
                gradientRing(r, 1f)
                drawLogo(c, cx, cy, rr)
                p.style = Paint.Style.FILL
                p.shader = shShader; c.drawCircle(cx, cy, rr, p)
                p.shader = rimShader; c.drawCircle(cx, cy, rr, p)
                p.shader = hlShader; c.drawCircle(cx, cy, rr, p); p.shader = null
            }
        }

        if (rings) {
            orbit(c, cx, cy, r * 1.42f, r * 0.42f, -24f, sp1, true, ow * 1.25f, 255)
            orbit(c, cx, cy, r * 1.30f, r * 0.34f, 32f, sp2, true, ow, 235)
        }

        if (on && !lite && (showSparkles || full2)) {
            p.style = Paint.Style.FILL
            for (k in 0 until 7) {
                val a = Math.toRadians((t * (40.0 + k * 9.0) + k * 51.0) % 360.0)
                val rad = r * (1.2 + 0.07 * (k % 3))
                val tw = 0.5f + 0.5f * sin(t * 3f + k)
                hsv[0] = mapHue(hue + k * 50f)
                p.color = Color.HSVToColor((120 + 135 * tw).toInt(), hsv)
                c.drawCircle(cx + cos(a).toFloat() * rad.toFloat(), cy + sin(a).toFloat() * rad.toFloat() * 0.85f, full * 0.012f + 1.5f + tw * 2f, p)
            }
        }

        if (on && state == S.THINKING) {
            p.style = Paint.Style.FILL
            p.color = Color.WHITE
            for (k in 0..2) {
                val a = Math.toRadians((t * 240.0 + k * 120.0))
                c.drawCircle(cx + cos(a).toFloat() * r * 1.22f, cy + sin(a).toFloat() * r * 1.22f, full * 0.025f + 2f, p)
            }
        }

        if (standby) {
            val pulse = if (on) 0.5f + 0.5f * sin(t * 3f) else 1f
            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#22C55E")
            p.alpha = (120 + 135 * pulse).toInt()
            c.drawCircle(w - full * 0.12f, full * 0.12f + (h - 2 * full) / 2f, full * 0.05f + 2f + pulse * 2f, p)
            p.alpha = 255
        }
    }
}
