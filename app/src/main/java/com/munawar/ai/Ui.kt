package com.munawar.ai

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

object Theme {
    // Palettes: bg, card, primary, primaryDark, secondary, tertiary, text
    private val palettes = linkedMapOf(
        "navy" to arrayOf("#050B1E", "#0B1530", "#3B82F6", "#1E3A8A", "#22D3EE", "#818CF8", "#E6EEFF"),
        "midnight" to arrayOf("#02060F", "#08111F", "#2563EB", "#1E40AF", "#60A5FA", "#A78BFA", "#E0EAFF"),
        "ocean" to arrayOf("#04101C", "#0B1B2E", "#38BDF8", "#0C4A6E", "#2DD4BF", "#818CF8", "#E0F2FE"),
        "deepsea" to arrayOf("#03121A", "#0A2230", "#06B6D4", "#155E75", "#34D399", "#38BDF8", "#E0FBFF"),
        "indigo" to arrayOf("#0A0A1F", "#14143A", "#6366F1", "#3730A3", "#22D3EE", "#F0ABFC", "#EEF2FF"),
        "violet" to arrayOf("#0A0714", "#161026", "#8B5CF6", "#4C2A8C", "#22D3EE", "#F472B6", "#EDE9FE"),
        "emerald" to arrayOf("#04120C", "#0B1F17", "#10B981", "#065F46", "#FACC15", "#22D3EE", "#ECFDF5"),
        "forest" to arrayOf("#050F08", "#0D1F12", "#22C55E", "#166534", "#A3E635", "#2DD4BF", "#ECFDF5"),
        "sunset" to arrayOf("#140808", "#241210", "#F97316", "#7C2D12", "#FACC15", "#F472B6", "#FFF1E6"),
        "rose" to arrayOf("#14060C", "#240E16", "#F43F5E", "#881337", "#FB923C", "#C084FC", "#FFE4E6"),
        "crimson" to arrayOf("#12060A", "#200B12", "#DC2626", "#7F1D1D", "#F59E0B", "#F472B6", "#FEE2E2"),
        "gold" to arrayOf("#100C03", "#1E1708", "#EAB308", "#713F12", "#F97316", "#FDE047", "#FEF9C3"),
        "mono" to arrayOf("#0B0B0C", "#19191B", "#A3A3A3", "#404040", "#E5E5E5", "#FAFAFA", "#F5F5F5"),
        "slate" to arrayOf("#0B1018", "#151D29", "#94A3B8", "#334155", "#38BDF8", "#CBD5E1", "#F1F5F9"),
        "aurora" to arrayOf("#04100F", "#0A1F1E", "#2DD4BF", "#115E59", "#A78BFA", "#4ADE80", "#E6FFFA"),
        "neon" to arrayOf("#070511", "#130B24", "#D946EF", "#701A75", "#22D3EE", "#FACC15", "#FAE8FF"),
        "cherry" to arrayOf("#14040C", "#260A18", "#EC4899", "#831843", "#FB7185", "#C084FC", "#FCE7F3"),
        "ice" to arrayOf("#07111C", "#112236", "#7DD3FC", "#1E4B73", "#BAE6FD", "#E0F2FE", "#F0F9FF"),
        "lava" to arrayOf("#120503", "#211009", "#F97316", "#9A3412", "#EF4444", "#FACC15", "#FFEDD5"),
        "matrix" to arrayOf("#020A04", "#08170B", "#22C55E", "#14532D", "#86EFAC", "#4ADE80", "#DCFCE7"),
        // light ("white") themes
        "white" to arrayOf("#F4F7FC", "#FFFFFF", "#2563EB", "#BFDBFE", "#0891B2", "#DB2777", "#0F172A"),
        "paper" to arrayOf("#FAF7F2", "#FFFFFF", "#B45309", "#FDE68A", "#0F766E", "#BE185D", "#292524"),
        "mint" to arrayOf("#F0FDF6", "#FFFFFF", "#059669", "#A7F3D0", "#0284C7", "#C026D3", "#052E1B"),
        "lavender" to arrayOf("#F6F3FF", "#FFFFFF", "#7C3AED", "#DDD6FE", "#0891B2", "#DB2777", "#1E1B4B")
    )
    val names = listOf(
        "navy" to "🌌 Dark blue (default)", "midnight" to "🌑 Midnight blue", "ocean" to "🌊 Ocean", "deepsea" to "🐬 Deep sea", "indigo" to "🔷 Indigo",
        "violet" to "💜 Violet", "emerald" to "🌿 Emerald", "forest" to "🌲 Forest", "sunset" to "🌇 Sunset", "rose" to "🌹 Rose",
        "crimson" to "🍷 Crimson", "gold" to "✨ Gold", "mono" to "⚫ Mono", "slate" to "🪨 Slate", "aurora" to "🌠 Aurora",
        "neon" to "💗 Neon", "cherry" to "🍒 Cherry", "ice" to "🧊 Ice", "lava" to "🌋 Lava", "matrix" to "🟩 Matrix",
        "white" to "⚪ White (light)", "paper" to "📜 Paper (light)", "mint" to "🍃 Mint (light)", "lavender" to "🪻 Lavender (light)"
    )

    var BG = Color.parseColor("#050B1E")
    var CARD = Color.parseColor("#D90B1530")
    var PURPLE = Color.parseColor("#3B82F6")
    var PURPLE_DARK = Color.parseColor("#1E3A8A")
    var CYAN = Color.parseColor("#22D3EE")
    var PINK = Color.parseColor("#818CF8")
    var TEXT = Color.parseColor("#E6EEFF")
    var MUTED = Color.parseColor("#9CA3AF")
    val RED = Color.parseColor("#EF4444")
    var fontScale = 1f
    var corner = 16
    var isLight = false

    private fun hex(h: String, d: Int): Int = try { if (h.isBlank()) d else Color.parseColor(if (h.startsWith("#")) h else "#$h") } catch (e: Exception) { d }

    /** Reads the saved appearance settings. Called at startup; screens are rebuilt (activity recreate) after a change. */
    fun apply(p: Prefs) {
        val pal = palettes[p.uiTheme] ?: palettes["navy"]!!
        val c = pal.map { Color.parseColor(it) }
        BG = hex(p.bgHex, c[0])
        isLight = (0.299f * Color.red(BG) + 0.587f * Color.green(BG) + 0.114f * Color.blue(BG)) / 255f > 0.6f
        val a = (p.cardAlphaPct * 255 / 100).coerceIn(0, 255)
        CARD = Color.argb(a, Color.red(c[1]), Color.green(c[1]), Color.blue(c[1]))
        PURPLE = hex(p.accentHex, c[2])
        PURPLE_DARK = if (p.accentHex.isBlank()) c[3] else Color.rgb((Color.red(PURPLE) * 0.5f).toInt(), (Color.green(PURPLE) * 0.5f).toInt(), (Color.blue(PURPLE) * 0.5f).toInt())
        CYAN = c[4]; PINK = c[5]; TEXT = c[6]
        MUTED = if (isLight) Color.parseColor("#64748B") else Color.parseColor("#9CA3AF")
        fontScale = p.fontPct / 100f
        corner = p.cornerDp
    }
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun gradBg(c: Int, r: Float) = GradientDrawable(
    GradientDrawable.Orientation.LEFT_RIGHT,
    intArrayOf(c, Color.rgb(minOf(255, Color.red(c) + 60), minOf(255, Color.green(c) + 40), minOf(255, Color.blue(c) + 70)))
).apply { cornerRadius = r }

fun roundBg(color: Int, radius: Float, stroke: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius
    if (stroke != 0) setStroke(2, stroke)
}


private fun lighten(c: Int, f: Float = 0.22f): Int =
    Color.argb(Color.alpha(c), (Color.red(c) + (255 - Color.red(c)) * f).toInt(), (Color.green(c) + (255 - Color.green(c)) * f).toInt(), (Color.blue(c) + (255 - Color.blue(c)) * f).toInt())

private fun darken(c: Int, f: Float = 0.55f): Int =
    Color.argb(Color.alpha(c), (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

/** 3D look: dark "thickness" edge underneath, lit gradient face, glossy top highlight. */
fun Context.depthBg(color: Int, radius: Float, stroke: Int = 0, depthDp: Int = 4): Drawable {
    val edge = GradientDrawable().apply { setColor(darken(color)); cornerRadius = radius }
    val face = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lighten(color, 0.28f), color, darken(color, 0.85f))).apply {
        cornerRadius = radius
        if (stroke != 0) setStroke(2, stroke)
    }
    val gloss = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x40FFFFFF, 0x00FFFFFF)).apply { cornerRadius = radius }
    return LayerDrawable(arrayOf<Drawable>(edge, face, gloss)).apply {
        val d = dp(depthDp)
        setLayerInset(1, 0, 0, 0, d)
        setLayerInset(2, dp(2), dp(1), dp(2), d + dp(14))
    }
}

fun withAlpha(c: Int, a: Int): Int = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

fun mix(a: Int, b: Int, t: Float): Int = Color.argb(
    (Color.alpha(a) * (1f - t) + Color.alpha(b) * t).toInt(), (Color.red(a) * (1f - t) + Color.red(b) * t).toInt(),
    (Color.green(a) * (1f - t) + Color.green(b) * t).toInt(), (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt()
)

/** "Glass": translucent body with a glowing two-colour gradient border and a soft shine on top. */
fun Context.glassBg(fill: Int, radius: Float, border: Int = 2, a: Int = Theme.PURPLE, b: Int = Theme.CYAN): Drawable {
    val style = try { Core.prefs.cardStyle } catch (e: Exception) { "glass" }
    val bpx = if (style == "minimal") 0 else dp(if (style == "neon") border + 1 else border).coerceAtLeast(1)
    val al = when (style) { "neon" -> intArrayOf(255, 150, 90); "solid" -> intArrayOf(90, 40, 20); else -> intArrayOf(170, 80, 40) }
    val outer = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(withAlpha(a, al[0]), withAlpha(b, al[1]), withAlpha(a, al[2]))).apply { cornerRadius = radius }
    val ir = (radius - bpx).coerceAtLeast(0f)
    val inner = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lighten(fill, if (style == "solid") 0.02f else 0.08f), fill)).apply { cornerRadius = ir }
    val shine = if (style == "solid" || style == "minimal") 0x00FFFFFF else 0x26FFFFFF
    val gloss = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(shine, 0x00FFFFFF)).apply { cornerRadius = ir }
    return LayerDrawable(arrayOf<Drawable>(outer, inner, gloss)).apply { setLayerInset(1, bpx, bpx, bpx, bpx); setLayerInset(2, bpx, bpx, bpx, bpx) }
}

/** Pill-shaped gradient button background with a glossy highlight. */
fun Context.pillBg(color: Int, radius: Float): Drawable {
    val second = if (color == Theme.RED) 0xFFF97316.toInt() else mix(color, Theme.CYAN, 0.55f)
    val body = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(color, second)).apply { cornerRadius = radius }
    val gloss = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x44FFFFFF, 0x00FFFFFF)).apply { cornerRadius = radius }
    return LayerDrawable(arrayOf<Drawable>(body, gloss))
}

/** Small selectable pill (used for option rows). */
fun Context.chip(text: String, selected: Boolean, onClick: () -> Unit) = TextView(this).apply {
    this.text = text; textSize = 13f * Theme.fontScale; gravity = Gravity.CENTER
    setTextColor(if (selected) Color.WHITE else Theme.TEXT)
    background = if (selected) pillBg(Theme.PURPLE, dp(22).toFloat()) else glassBg(Theme.CARD, dp(22).toFloat(), 1)
    setPadding(dp(10), dp(10), dp(10), dp(10))
    isClickable = true
    setOnClickListener { onClick() }
}

/** Round gradient icon button (☰ etc.). */
fun Context.iconButton(symbol: String, onClick: () -> Unit) = TextView(this).apply {
    text = symbol; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
    background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Theme.PURPLE, mix(Theme.PURPLE, Theme.CYAN, 0.6f))).apply { shape = GradientDrawable.OVAL }
    elevation = dp(4).toFloat(); isClickable = true
    setOnClickListener { onClick() }
}

/** Entrance animations for cards/tiles (level in Settings → Appearance → UI animation: off / smooth / full). */
object Motion {
    private var n = 0
    fun reset() { n = 0 }
    fun enter(v: View) {
        val lvl = try { Core.prefs.uiAnim } catch (e: Exception) { 1 }
        if (lvl == 0) return
        val i = n++
        if (i > 14) return
        val d = v.resources.displayMetrics.density
        v.alpha = 0f
        v.translationY = d * (if (lvl == 2) 26f else 12f)
        if (lvl == 2) { v.scaleX = 0.94f; v.scaleY = 0.94f }
        val ip: android.view.animation.Interpolator = if (lvl == 2) android.view.animation.OvershootInterpolator(0.9f) else android.view.animation.DecelerateInterpolator()
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay((i * 40L).coerceAtMost(420L))
            .setDuration(if (lvl == 2) 420L else 260L).setInterpolator(ip).start()
    }
}

/** Gives a view real perspective so rotationX/rotationY look 3D. */
fun View.enable3d() { cameraDistance = 9000f * resources.displayMetrics.density }

/** LinearLayout params; w/h in px (or MATCH/WRAP), margin m in dp. */
fun Context.lp(w: Int = MATCH, h: Int = WRAP, weight: Float = 0f, m: Int = 0) =
    LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(m), dp(m), dp(m), dp(m)) }

fun Context.vbox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.hbox() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

fun Context.label(text: String, size: Float = 15f, color: Int = Theme.TEXT, bold: Boolean = false) =
    TextView(this).apply {
        this.text = text; textSize = size * Theme.fontScale; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

fun Context.button(text: String, color: Int = Theme.PURPLE, onClick: () -> Unit) = Button(this).apply {
    this.text = text; isAllCaps = false; setTextColor(if (color == Theme.CARD) Theme.TEXT else Color.WHITE); textSize = 14f
    minimumHeight = dp(48); minHeight = dp(48)
    background = if (color == Theme.CARD) glassBg(Theme.CARD, dp(Theme.corner + 10).toFloat(), 1) else pillBg(color, dp(Theme.corner + 10).toFloat())
    setPadding(dp(18), 0, dp(18), 0); elevation = dp(3).toFloat(); stateListAnimator = null; enable3d()
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).translationZ(-dp(2).toFloat()).setDuration(70).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val full = try { Core.prefs.uiAnim == 2 } catch (e: Exception) { false }
                val ip: android.view.animation.Interpolator = if (full) android.view.animation.OvershootInterpolator(3f) else android.view.animation.DecelerateInterpolator()
                v.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(if (full) 260L else 120L).setInterpolator(ip).start()
            }
        }
        false
    }
    setOnClickListener { onClick() }
}

fun Context.field(hint: String, password: Boolean = false, multi: Boolean = false) = EditText(this).apply {
    this.hint = hint; setHintTextColor(Theme.MUTED); setTextColor(Theme.TEXT); textSize = 15f
    background = glassBg(Theme.CARD, dp(16).toFloat(), 1)
    setPadding(dp(14), dp(12), dp(14), dp(12))
    inputType = when {
        password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        else -> InputType.TYPE_CLASS_TEXT
    }
    if (password) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    if (multi) maxLines = 5
}

fun Context.scroll(content: View) = ScrollView(this).apply { addView(content) }

fun Context.section(title: String) = label("", 15f, Theme.TEXT, true).apply {
    val sp = SpannableString("▍ $title")
    sp.setSpan(ForegroundColorSpan(Theme.PURPLE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    text = sp
    letterSpacing = 0.03f
    setPadding(0, dp(16), 0, dp(6)); tag = "section"
}

fun Context.card() = vbox().apply {
    background = glassBg(Theme.CARD, dp(Theme.corner + 6).toFloat(), 2); setPadding(dp(16), dp(14), dp(16), dp(16)); elevation = dp(4).toFloat(); enable3d(); Motion.enter(this)
}

fun Context.seek(max: Int, progress: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
    this.max = max; this.progress = progress
    progressTintList = ColorStateList.valueOf(Theme.PURPLE); thumbTintList = ColorStateList.valueOf(Theme.CYAN)
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
}

fun Context.radio(options: List<Pair<String, String>>, current: String, onSel: (String) -> Unit) = RadioGroup(this).apply {
    options.forEachIndexed { idx, (value, text) ->
        addView(RadioButton(context).apply {
            id = 5000 + idx; this.text = text; setTextColor(Theme.TEXT); isChecked = value == current
            minimumHeight = dp(44); buttonTintList = ColorStateList.valueOf(Theme.PURPLE); textSize = 14f * Theme.fontScale
        })
    }
    setOnCheckedChangeListener { _, id -> onSel(options[id - 5000].first) }
}

/** Title text with a colour gradient that keeps sliding. */
class RainbowText(ctx: Context, txt: String, size: Float) : TextView(ctx) {
    private var sh: LinearGradient? = null
    private val m = Matrix()
    private var anim: ValueAnimator? = null

    init {
        text = txt; textSize = size; setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.06f
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sh = LinearGradient(
            0f, 0f, maxOf(w, 1).toFloat(), 0f,
            intArrayOf(Theme.CYAN, Theme.PURPLE, Theme.PINK, Color.parseColor("#F59E0B"), Theme.CYAN), null, Shader.TileMode.MIRROR
        )
        paint.shader = sh
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { m.setTranslate(width * 2f * it.animatedFraction, 0f); sh?.setLocalMatrix(m); invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }
}

/** Full-screen colourful background: three soft blobs drifting around while their hue keeps changing (30 fps). */
class AuroraView(ctx: Context) : View(ctx) {
    @Volatile var paused = false
    var lite = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = floatArrayOf(0f, 0.8f, 1f)
    private val t0 = System.nanoTime()
    private var anim: ValueAnimator? = null
    private var frame = 0

    private var moving = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        moving = try { Core.prefs.bgAnim } catch (e: Exception) { false }
        if (!moving) { invalidate(); return }
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { if (!paused && !lite && ++frame % 3 == 0) invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val t = if (moving) (System.nanoTime() - t0) / 1e9f else 0f
        c.drawColor(Theme.BG)
        val r = maxOf(w, h) * 0.62f
        val strength = if (Theme.isLight) 0.55f else 1f
        val cols = intArrayOf(Theme.PURPLE, Theme.CYAN, Theme.PINK)
        val alphas = intArrayOf(95, 70, 55)
        for (i in 0..2) {
            val cx = w * (0.5f + 0.42f * kotlin.math.sin(t * 0.30f + i * 2.1f) + (if (i == 0) -0.25f else if (i == 1) 0.25f else 0f))
            val cy = h * (0.5f + 0.40f * kotlin.math.cos(t * 0.24f + i * 1.7f) + (if (i == 0) -0.30f else if (i == 1) 0.30f else 0.05f))
            p.shader = RadialGradient(cx, cy, r, withAlpha(cols[i], (alphas[i] * strength).toInt()), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        }
        p.shader = null
    }
}

/** Home-screen tile: emoji + label with a colour-cycling border. Call setBorder(colour) to recolour. */
class TileView(ctx: Context, emoji: String, title: String, onClick: () -> Unit) : LinearLayout(ctx) {
    private val bg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lighten(Theme.CARD, 0.06f), Theme.CARD)).apply { cornerRadius = ctx.dp(Theme.corner + 2).toFloat(); setStroke(3, Theme.PURPLE) }

    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        background = bg
        elevation = ctx.dp(8).toFloat(); enable3d()
        setPadding(ctx.dp(6), ctx.dp(12), ctx.dp(6), ctx.dp(12))
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> if (Core.prefs.tiltOn) {
                    val fx = (e.x / maxOf(1, v.width) - 0.5f).coerceIn(-0.6f, 0.6f); val fy = (e.y / maxOf(1, v.height) - 0.5f).coerceIn(-0.6f, 0.6f)
                    v.rotationY = fx * 28f; v.rotationX = -fy * 28f; v.translationZ = ctx.dp(10).toFloat()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().rotationX(0f).rotationY(0f).translationZ(0f).setDuration(180).start()
            }
            false
        }
        addView(TextView(ctx).apply { text = emoji; textSize = 28f; gravity = Gravity.CENTER })
        addView(TextView(ctx).apply { text = title; textSize = 12f; setTextColor(Theme.TEXT); gravity = Gravity.CENTER })
        setOnClickListener { animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(120).start(); onClick() }.start() }
    }

    fun setBorder(c: Int) { bg.setStroke(3, c) }
}
