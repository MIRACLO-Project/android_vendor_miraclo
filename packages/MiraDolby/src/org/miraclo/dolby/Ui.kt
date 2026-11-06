// SPDX-FileCopyrightText: MIRACLO
// SPDX-License-Identifier: Apache-2.0

package org.miraclo.dolby

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.PathParser
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Mira Dolby's colours, dark and light. */
class Palette(val dark: Boolean) {
    val bg = if (dark) 0xFF0B0D14.toInt() else 0xFFF2F3F7.toInt()
    val card = if (dark) 0xFF151925.toInt() else 0xFFFFFFFF.toInt()
    val card2 = if (dark) 0xFF1A1E2A.toInt() else 0xFFE3E6EE.toInt()
    val track = if (dark) 0xFF262B3A.toInt() else 0xFFE3E6EE.toInt()
    val fg = if (dark) 0xFFEEF0F5.toInt() else 0xFF15171C.toInt()
    val fg2 = if (dark) 0xFFA3A8B4.toInt() else 0xFF5E6370.toInt()
    val acc = if (dark) 0xFF4F86FF.toInt() else 0xFF3550C8.toInt()
    val accSoft = if (dark) 0xFF1A2340.toInt() else 0xFFE3E9FF.toInt()
    val link = if (dark) 0xFF9FB3FF.toInt() else 0xFF3550C8.toInt()
    val div = if (dark) 0x0FFFFFFF else 0x12000000
    val logo = if (dark) 0xFFFFFFFF.toInt() else 0xFF15171C.toInt()
    val glow = if (dark) 0x8C4F86FF.toInt() else 0x663A6BEF
    val sel = if (dark) 0xFF2A3350.toInt() else 0xFFE3E9FF.toInt()
    val grid = if (dark) 0x1AFFFFFF else 0x1A000000
    val chip = if (dark) 0xFF222737.toInt() else 0xFFEEF0F5.toInt()
    val chipFg = if (dark) 0xFFC9CDD8.toInt() else 0xFF3D4250.toInt()
    val warn = if (dark) 0xFF3A2E14.toInt() else 0xFFFFF1D6.toInt()
    val warnFg = if (dark) 0xFFF5C46B.toInt() else 0xFF8A5A00.toInt()
    val ringOff = if (dark) 0xFF3A4052.toInt() else 0xFFC6CAD4.toInt()
    val well = if (dark) 0xFF10131D.toInt() else 0xFFF2F4FA.toInt()
    val area = if (dark) 0x244F86FF else 0x1A3550C8
    val areaOff = if (dark) 0x0AFFFFFF else 0x08000000
    val ring1 = if (dark) 0x1A9FB3FF else 0x1A3550C8
    val ring2 = if (dark) 0x2E9FB3FF else 0x2E3550C8
    val hero = if (dark) intArrayOf(0xFF1E2A55.toInt(), 0xFF141A2E.toInt(), 0xFF10131D.toInt())
               else intArrayOf(0xFFDCE5FF.toInt(), 0xFFE9EDFA.toInt(), 0xFFFFFFFF.toInt())
    val ripple = if (dark) 0x1FFFFFFF else 0x14000000
    // Material 3 error container, for "Reset to default".
    val errBg = if (dark) 0xFF93000A.toInt() else 0xFFFFDAD6.toInt()
    val errFg = if (dark) 0xFFFFDAD6.toInt() else 0xFF410002.toInt()

    companion object {
        fun of(c: Context) = Palette(
            (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    }
}

val EASE = PathInterpolator(0.2f, 0f, 0f, 1f)

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
fun View.dp(v: Float): Float = context.dp(v)
fun View.dp(v: Int): Int = context.dp(v)

fun font(weight: Int): Typeface = Typeface.create(Typeface.DEFAULT, weight, false)

fun Context.text(s: CharSequence, sp: Float, color: Int, weight: Int = 400): TextView =
    TextView(this).apply {
        text = s
        textSize = sp
        setTextColor(color)
        typeface = font(weight)
        includeFontPadding = false
        setLineSpacing(0f, 1.2f)
    }

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }

/** [content] with a touch ripple clipped to the same rounded shape. */
fun touch(p: Palette, content: Drawable?, radius: Float): Drawable =
    RippleDrawable(ColorStateList.valueOf(p.ripple), content, rounded(Color.WHITE, radius))

fun Context.column(gap: Int = 0): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    if (gap > 0) {
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = GradientDrawable().apply { setSize(1, dp(gap)); setColor(Color.TRANSPARENT) }
    }
}

fun Context.row(gap: Int = 0): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    if (gap > 0) {
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = GradientDrawable().apply { setSize(dp(gap), 1); setColor(Color.TRANSPARENT) }
    }
}

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
       weight: Float = 0f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h, weight)

const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

/** An icon drawn from SVG path data on a 24 by 24 grid, as a line drawing. */
class PathIcon(c: Context, data: String, var color: Int, private val sizeDp: Int = 20,
               private val strokeDp: Float = 1.8f) : View(c) {
    private val src: Path = PathParser.createPathFromPathData(data)
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dp(sizeDp), dp(sizeDp))

    override fun onDraw(canvas: Canvas) {
        val s = width / 24f
        path.set(src)
        path.transform(Matrix().apply { setScale(s, s) })
        paint.color = color
        paint.strokeWidth = strokeDp * s
        canvas.drawPath(path, paint)
    }
}

/** On/off switch, as in the mocks. */
class SwitchView(c: Context, private val p: Palette, private val big: Boolean = false) : View(c) {
    var onChange: ((Boolean) -> Unit)? = null
    var checked = false
        private set
    private var pos = 0f
    private var anim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        contentDescription = ""
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            set(!checked, true)
            onChange?.invoke(checked)
        }
    }

    fun set(on: Boolean, animate: Boolean = false) {
        if (on == checked && anim == null) { pos = if (on) 1f else 0f; invalidate(); return }
        checked = on
        anim?.cancel()
        if (!animate || !isAttachedToWindow) { anim = null; pos = if (on) 1f else 0f; invalidate(); return }
        anim = ValueAnimator.ofFloat(pos, if (on) 1f else 0f).apply {
            duration = 220
            interpolator = EASE
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { anim = null }
            })
            start()
        }
    }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(dp(if (big) 56 else 52), dp(if (big) 32 else 30))

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        paint.color = blend(p.track, p.acc, pos)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, h / 2, h / 2, paint)
        val inset = dp(if (big) 4f else 3f)
        val r = h / 2 - inset
        val x = inset + r + (width - 2 * (inset + r)) * pos
        paint.color = Color.WHITE
        canvas.drawCircle(x, h / 2, r, paint)
    }
}

fun blend(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = ((a shr s and 0xFF) + ((b shr s and 0xFF) - (a shr s and 0xFF)) * t).roundToInt() shl s
    return ch(24) or ch(16) or ch(8) or ch(0)
}

/** Small round arrow that puts a control back to its default; only shown when changed. */
class ResetIcon(c: Context, private val p: Palette) : View(c) {
    var onReset: (() -> Unit)? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var shown = false

    init {
        contentDescription = "Reset to default"
        alpha = 0f
        scaleX = 0.6f; scaleY = 0.6f
        visibility = GONE
        background = touch(p, null, dp(16f))
        setOnClickListener {
            if (!shown) return@setOnClickListener
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onReset?.invoke()
        }
    }

    fun show(on: Boolean) {
        if (on == shown) return
        shown = on
        animate().cancel()
        if (on) {
            visibility = VISIBLE
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(EASE).start()
        } else {
            animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(150).setInterpolator(EASE)
                .withEndAction { if (!shown) visibility = GONE }.start()
        }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dp(30), dp(30))

    override fun onDraw(canvas: Canvas) = drawReset(canvas, width / 2f, height / 2f, dp(7f), p.fg2, paint)

    companion object {
        /** A three-quarter circle with an arrow head, centred on (cx, cy). */
        fun drawReset(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, paint: Paint) {
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.27f
            val o = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(o, -60f, 290f, false, paint)
            val a = Math.toRadians(-60.0)
            val ex = cx + r * cos(a).toFloat()
            val ey = cy + r * sin(a).toFloat()
            val head = r * 0.55f
            val path = Path()
            path.moveTo(ex - head, ey - head * 0.15f)
            path.lineTo(ex, ey)
            path.lineTo(ex + head * 0.15f, ey - head)
            canvas.drawPath(path, paint)
        }
    }
}

/** Horizontal slider: a 6 dp track, a filled part and a white thumb. */
class SliderView(c: Context, private val p: Palette) : View(c) {
    var value = 0f            // 0..1
        private set
    var steps = 0             // 0 = smooth
    var onChange: ((Float) -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var dragging = false
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(dp(2f), 0f, dp(1f), 0x40000000)
    }
    private var lastStep = -1

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isFocusable = true
    }

    fun set(v: Float) { value = v.coerceIn(0f, 1f); invalidate() }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), dp(30))

    private val pad get() = dp(10f)

    override fun onDraw(canvas: Canvas) {
        val y = height / 2f
        val r = dp(3f)
        val l = pad; val rt = width - pad
        paint.color = p.track
        canvas.drawRoundRect(l, y - r, rt, y + r, r, r, paint)
        val x = l + (rt - l) * value
        paint.color = p.acc
        canvas.drawRoundRect(l, y - r, max(x, l + 2 * r), y + r, r, r, paint)
        canvas.drawCircle(x, y, dp(9f), thumb)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                dragging = true
                move(e.x)
            }
            MotionEvent.ACTION_MOVE -> move(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                dragging = false
                onStop?.invoke()
            }
        }
        return true
    }

    private fun move(x: Float) {
        var v = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        if (steps > 0) {
            val s = (v * steps).roundToInt()
            v = s.toFloat() / steps
            if (s != lastStep) { lastStep = s; performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        }
        if (v != value) { value = v; invalidate(); onChange?.invoke(v) }
    }
}

/** A slider with its name, value and reset icon above it. */
class SliderRow(c: Context, p: Palette, title: String, sub: String? = null) : LinearLayout(c) {
    val slider = SliderView(c, p)
    private val valueText = c.text("", 14f, p.link, 600)
    private val reset = ResetIcon(c, p)

    init {
        orientation = VERTICAL
        setPadding(dp(18), dp(8), dp(8), dp(4))
        val top = c.row()
        val names = c.column(2)
        names.addView(c.text(title, 15f, p.fg))
        if (sub != null) names.addView(c.text(sub, 12f, p.fg2))
        top.addView(names, lp(0, WRAP, 1f))
        top.addView(valueText, lp(WRAP, WRAP).apply { marginEnd = dp(4) })
        top.addView(reset)
        top.minimumHeight = dp(30)
        addView(top, lp())
        addView(slider, lp().apply { marginEnd = dp(10); marginStart = -dp(10) })
    }

    fun bind(fraction: Float, label: String, changed: Boolean) {
        if (!slider.dragging) slider.set(fraction)
        valueText.text = label
        reset.show(changed)
    }

    fun onReset(r: () -> Unit) { reset.onReset = r }
}

/** A row with a name, a line under it and a switch; the whole row toggles. */
class SwitchRow(c: Context, p: Palette, title: String, private val subText: TextView? = null,
                sub: String? = null) : LinearLayout(c) {
    val switch = SwitchView(c, p)
    val subtitle: TextView = subText ?: c.text(sub ?: "", 12f, p.fg2)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(18), dp(12), dp(16), dp(12))
        val names = c.column(2)
        names.addView(c.text(title, 15f, p.fg))
        if (sub != null || subText != null) names.addView(subtitle)
        addView(names, lp(0, WRAP, 1f).apply { marginEnd = dp(12) })
        addView(switch)
        background = touch(p, null, dp(24f))
        setOnClickListener { switch.performClick() }
    }
}

/** A smooth curve through the band gains (dB), with the area under it filled. */
class CurveView(c: Context, private val p: Palette, private val hDp: Int, private val perDb: Float,
                private val dashed: Boolean) : View(c) {
    var values = FloatArray(Tune.BANDS)
        set(v) { field = v; invalidate() }
    var active = true
        set(v) { field = v; invalidate() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), dp(hDp))

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val mid = h / 2
        val m = dp(5f)
        val pts = values.mapIndexed { i, v ->
            floatArrayOf(m + i * (w - 2 * m) / (values.size - 1), (mid - v * dp(perDb)).coerceIn(dp(2f), h - dp(2f)))
        }
        path.reset()
        path.moveTo(pts[0][0], pts[0][1])
        for (i in 1 until pts.size) {
            val cx = (pts[i - 1][0] + pts[i][0]) / 2
            path.cubicTo(cx, pts[i - 1][1], cx, pts[i][1], pts[i][0], pts[i][1])
        }
        if (dashed) {
            dash.color = p.grid
            dash.strokeWidth = dp(1f)
            dash.pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(4f)), 0f)
            canvas.drawLine(0f, mid, w, mid, dash)
        }
        val area = Path(path)
        area.lineTo(pts.last()[0], h); area.lineTo(pts[0][0], h); area.close()
        fill.color = if (active) p.area else p.areaOff
        canvas.drawPath(area, fill)
        line.color = if (active) p.acc else p.fg2
        line.strokeWidth = dp(2.4f)
        canvas.drawPath(path, line)
    }
}

/** Ten vertical band sliders; each shows a reset icon under it when it is changed. */
class EqBandsView(c: Context, private val p: Palette) : View(c) {
    var gains = FloatArray(Tune.BANDS)
    var defaults = FloatArray(Tune.BANDS)
    var onBand: ((Int, Float) -> Unit)? = null
    var onDone: (() -> Unit)? = null
    private var drag = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(dp(2f), 0f, dp(1f), 0x4D000000)
    }
    private val resetPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun set(g: FloatArray, d: FloatArray) { gains = g.copyOf(); defaults = d.copyOf(); invalidate() }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), dp(330))

    private val colW get() = width / Tune.BANDS.toFloat()
    private val top get() = dp(26f)
    private val bottom get() = height - dp(52f)

    private fun yOf(db: Float) = top + (bottom - top) * (Tune.EQ_MAX - db) / (Tune.EQ_MAX - Tune.EQ_MIN)

    override fun onDraw(canvas: Canvas) {
        val mid = yOf(0f)
        for (i in 0 until Tune.BANDS) {
            val cx = colW * (i + 0.5f)
            val g = gains[i]
            label.color = p.fg; label.textSize = dp(10f); label.typeface = font(600)
            canvas.drawText(dbShort(g), cx, dp(12f), label)
            paint.color = p.track
            canvas.drawRoundRect(cx - dp(3f), top, cx + dp(3f), bottom, dp(3f), dp(3f), paint)
            val y = yOf(g)
            paint.color = p.acc
            canvas.drawRoundRect(cx - dp(3f), min(y, mid), cx + dp(3f), max(y, mid), dp(3f), dp(3f), paint)
            canvas.drawCircle(cx, y, dp(10f), thumb)
            label.color = p.fg2; label.typeface = font(400)
            canvas.drawText(Tune.HZ_LABEL[i], cx, bottom + dp(20f), label)
            if (abs(g - defaults[i]) > 0.01f) ResetIcon.drawReset(canvas, cx, bottom + dp(38f), dp(6f), p.fg2, resetPaint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = (e.x / colW).toInt().coerceIn(0, Tune.BANDS - 1)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (e.y > bottom + dp(26f)) {
                    if (abs(gains[i] - defaults[i]) > 0.01f) {
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        setBand(i, defaults[i]); onDone?.invoke()
                    }
                    return true
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                drag = i
                fromY(e.y)
            }
            MotionEvent.ACTION_MOVE -> if (drag >= 0) fromY(e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (drag >= 0) onDone?.invoke()
                drag = -1
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun fromY(y: Float) {
        val db = Tune.EQ_MAX - (y - top) / (bottom - top) * (Tune.EQ_MAX - Tune.EQ_MIN)
        val snapped = ((db * 2).roundToInt() / 2f).coerceIn(Tune.EQ_MIN, Tune.EQ_MAX)
        if (snapped != gains[drag]) {
            if (snapped.roundToInt().toFloat() == snapped) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            setBand(drag, snapped)
        }
    }

    private fun setBand(i: Int, db: Float) {
        gains[i] = db
        invalidate()
        onBand?.invoke(i, db)
    }

    private fun dbShort(v: Float): String {
        val s = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else v.toString()
        return if (v > 0) "+$s" else s
    }
}

/** Lays children out left to right, wrapping to new lines. */
class FlowLayout(c: Context, private val gapDp: Int) : ViewGroup(c) {
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val maxW = MeasureSpec.getSize(wSpec)
        val gap = dp(gapDp)
        var x = 0; var y = 0; var lineH = 0
        for (i in 0 until childCount) {
            val ch = getChildAt(i)
            ch.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            if (x > 0 && x + ch.measuredWidth > maxW) { x = 0; y += lineH + gap; lineH = 0 }
            x += ch.measuredWidth + gap
            lineH = max(lineH, ch.measuredHeight)
        }
        setMeasuredDimension(maxW, y + lineH)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l
        val gap = dp(gapDp)
        var x = 0; var y = 0; var lineH = 0
        for (i in 0 until childCount) {
            val ch = getChildAt(i)
            if (x > 0 && x + ch.measuredWidth > maxW) { x = 0; y += lineH + gap; lineH = 0 }
            ch.layout(x, y, x + ch.measuredWidth, y + ch.measuredHeight)
            x += ch.measuredWidth + gap
            lineH = max(lineH, ch.measuredHeight)
        }
    }
}

/** The Mira logo (the O with its dot) in its rings; the dot circles the O while Dolby is on. */
class LogoView(c: Context, private val p: Palette, private val ringDp: Float, private val oDp: Float,
               private val withRings: Boolean, private val centreY: Float = 0.5f) : View(c) {
    var on = true
        set(v) {
            if (field == v) return
            field = v
            animate().alpha(if (v) 1f else 0.35f).setDuration(250).start()
            if (v) spin.resume() else spin.pause()
        }
    private var angle = -50f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spin = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 9000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { angle = -50f + it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        spin.start()
        if (!on) spin.pause()
    }

    override fun onDetachedFromWindow() {
        spin.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(visible: Boolean) {
        super.onVisibilityAggregated(visible)
        if (!visible) spin.pause() else if (on && spin.isStarted) spin.resume()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height * centreY
        paint.style = Paint.Style.STROKE
        if (withRings) {
            paint.strokeWidth = dp(1f)
            paint.color = p.ring1; canvas.drawCircle(cx, cy, dp(118f), paint)
            paint.color = p.ring2; canvas.drawCircle(cx, cy, dp(83f), paint)
        }
        val r = dp(oDp) / 2 - dp(ringDp) / 2
        paint.strokeWidth = dp(ringDp)
        paint.color = p.logo
        canvas.drawCircle(cx, cy, r, paint)
        val a = Math.toRadians(angle.toDouble())
        val dx = cx + (r + dp(ringDp) * 0.1f) * cos(a).toFloat()
        val dy = cy + (r + dp(ringDp) * 0.1f) * sin(a).toFloat()
        val dot = dp(oDp) * 0.085f
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(dx, dy, dot * 3.2f, intArrayOf(p.glow, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(dx, dy, dot * 3.2f, paint)
        paint.shader = null
        paint.color = p.acc
        canvas.drawCircle(dx, dy, dot, paint)
    }
}

/** The hero card's soft radial background. */
fun heroBackground(p: Palette, radius: Float): Drawable = object : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val b = bounds
        paint.shader = RadialGradient(b.exactCenterX(), b.top + b.height() * 0.42f,
            max(b.width(), b.height()) * 0.75f, p.hero, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(b), radius, radius, paint)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

/** Small mini-curve badge used on the home page's preset card. */
class CurveBadge(c: Context, private val p: Palette) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    var values = FloatArray(Tune.BANDS)
        set(v) { field = v; invalidate() }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dp(64), dp(48))

    override fun onDraw(canvas: Canvas) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.accSoft }
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), dp(14f), dp(14f), bg)
        val m = dp(8f); val w = width - 2 * m; val mid = height / 2f
        val path = Path()
        val v = if (values.all { it == 0f }) floatArrayOf(2f, 1.5f, 0.5f, 0f, -0.5f, -1f, 0f, 1f, 2f, 2.5f) else values
        val peak = max(1f, v.maxOf { abs(it) })
        for (i in v.indices) {
            val x = m + i * w / (v.size - 1)
            val y = mid - v[i] / peak * dp(11f)
            if (i == 0) path.moveTo(x, y) else {
                val px = m + (i - 1) * w / (v.size - 1)
                val py = mid - v[i - 1] / peak * dp(11f)
                val cx = (px + x) / 2
                path.cubicTo(cx, py, cx, y, x, y)
            }
        }
        paint.color = p.acc
        paint.strokeWidth = dp(2.4f)
        canvas.drawPath(path, paint)
    }
}

fun ellipsize(t: TextView): TextView = t.apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
