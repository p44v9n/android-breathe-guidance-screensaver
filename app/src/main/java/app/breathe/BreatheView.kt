package app.breathe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * The unph.one breathing guide, ported from its BreatheAnimation.tsx. A base line
 * grows from its centre, then a triangle, square … nonagon unfold on that same base,
 * each morphing out of the shape before it. The exhale is the inhale played backwards.
 *
 * Unlike the site, each breath is a little longer than the one before.
 */
class BreatheView(context: Context) : View(context) {
    private val ink = context.getColor(R.color.marigold)
    private val margin = 48 * resources.displayMetrics.density

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        strokeJoin = Paint.Join.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        typeface = resources.getFont(R.font.geist)
        textSize = FONT_SIZE
        // The site asks for italic Geist but only loads the upright, so browsers slant it.
        textSkewX = -0.25f
    }
    private val path = Path()

    // "breathe" stays put, placed so "breathe in" and "breathe out" are centred on average.
    private val suffixGap = FONT_SIZE * 0.28f
    private val labelX = -(label.measureText("breathe") + suffixGap +
        (label.measureText("in") + label.measureText("out")) / 2) / 2
    private val suffixX = labelX + label.measureText("breathe") + suffixGap

    private var startedAt = -1L
    private var breathStartedAt = 0L
    private var pace = 1f

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        if (startedAt < 0) {
            startedAt = now
            breathStartedAt = now
        }
        var t = (now - breathStartedAt) / 1e9f
        while (t >= CYCLE * pace) {
            breathStartedAt += (CYCLE * pace * 1e9f).toLong()
            t -= CYCLE * pace
            pace = paceAt((breathStartedAt - startedAt) / 1e9f)
        }
        // Everything below runs on the site's clock, stretched by this breath's pace.
        drawBreath(canvas, t / pace, LABEL_FADE / pace)
        postInvalidateOnAnimation()
    }

    private fun drawBreath(canvas: Canvas, t: Float, fade: Float) {
        val scale = min((width - 2 * margin) / WIDTH, (height - 2 * margin) / HEIGHT)
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)
        canvas.translate(0f, (TOP - CANVAS_BOTTOM) / 2)

        val build = build(t)
        val progress = { segment: Int -> (build * SEGMENT_COUNT - segment).coerceIn(0f, 1f) }

        val line = progress(0)
        if (line > 0) canvas.drawLine(-SIDE / 2 * line, 0f, SIDE / 2 * line, 0f, stroke)

        SHAPES.forEachIndexed { i, (from, to) ->
            val k = progress(i + 1)
            if (k <= 0) return@forEachIndexed
            path.rewind()
            from.indices.forEach { j ->
                val x = from[j].x + (to[j].x - from[j].x) * k
                val y = from[j].y + (to[j].y - from[j].y) * k
                // The geometry has y up; the canvas has it down.
                if (j == 0) path.moveTo(x, -y) else path.lineTo(x, -y)
            }
            path.close()
            canvas.drawPath(path, stroke)
        }

        // "in" hands over to "out" halfway through the rest at the top, and "out"
        // fades away by the end of the breath.
        val turn = INHALE + TOP_REST / 2
        val inOpacity = if (t < turn) min(ramp(t, 0f, fade), 1 - ramp(t, turn - fade, turn)) else 0f
        val outOpacity = if (t >= turn) min(ramp(t, turn, turn + fade), 1 - ramp(t, CYCLE - fade, CYCLE)) else 0f
        drawLabel(canvas, "breathe", labelX, 1f)
        drawLabel(canvas, "in", suffixX, inOpacity)
        drawLabel(canvas, "out", suffixX, outOpacity)
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, opacity: Float) {
        if (opacity <= 0) return
        label.alpha = (opacity * 255).toInt()
        canvas.drawText(text, x, LABEL_Y, label)
    }

    private data class Point(val x: Float, val y: Float)

    private companion object {
        // Seconds per phase of the first breath, as on the site.
        const val INHALE = 4.08f
        const val TOP_REST = 0.6f
        const val EXHALE = 4.08f
        const val BOTTOM_REST = 0.12f
        const val CYCLE = INHALE + TOP_REST + EXHALE + BOTTOM_REST
        const val LABEL_FADE = 0.35f

        // Breaths lengthen until they're six seconds in and six out, four minutes in.
        const val SLOWEST_PACE = 6f / INHALE
        const val RAMP_SECONDS = 240f

        /** How much longer than the site's a breath is, when it starts [at] seconds in. */
        fun paceAt(at: Float) = 1 + (SLOWEST_PACE - 1) * min(1f, at / RAMP_SECONDS)

        const val MAX_SIDES = 9
        const val SIDE = 90f
        const val STROKE_WIDTH = 1.5f
        const val FONT_SIZE = 16f
        const val GAP = 16f

        /** Regular n-gon on the base from (-s/2, 0) to (s/2, 0), y up. Vertex 0 is the
         *  left base corner, 1 the right, then counter-clockwise. */
        fun regularPolygon(n: Int): List<Point> {
            val apothem = SIDE / (2 * tan(PI / n))
            val radius = SIDE / (2 * sin(PI / n))
            return List(n) { j ->
                val angle = -PI / 2 - PI / n + j * 2 * PI / n
                Point((radius * cos(angle)).toFloat(), (apothem + radius * sin(angle)).toFloat())
            }
        }

        /** The (n-1)-gon re-expressed with n vertices, matched to the n-gon's. Odd n
         *  gains an apex from the midpoint of the previous top edge; even n splits the
         *  previous apex into two top corners. */
        fun startingShape(n: Int): List<Point> {
            val prev = regularPolygon(n - 1)
            if (n % 2 == 0) {
                val apex = n / 2
                return List(n) { j -> prev[if (j <= apex) j else j - 1] }
            }
            val apex = (n + 1) / 2
            return List(n) { j ->
                when {
                    j < apex -> prev[j]
                    j == apex -> prev[apex - 1].let { a ->
                        prev[apex % prev.size].let { b -> Point((a.x + b.x) / 2, (a.y + b.y) / 2) }
                    }
                    else -> prev[j - 1]
                }
            }
        }

        val SHAPES = (3..MAX_SIDES).map { n -> startingShape(n) to regularPolygon(n) }
        val SEGMENT_COUNT = SHAPES.size + 1

        // The canvas fits the largest shape with its label underneath.
        val LARGEST = regularPolygon(MAX_SIDES)
        const val PAD = STROKE_WIDTH + 2
        val TOP = LARGEST.maxOf { it.y }
        val WIDTH = (LARGEST.maxOf { abs(it.x) } + PAD) * 2
        const val LABEL_Y = GAP + FONT_SIZE * 0.8f
        const val CANVAS_BOTTOM = LABEL_Y + FONT_SIZE * 0.3f
        val HEIGHT = TOP + CANVAS_BOTTOM + PAD * 2

        fun ramp(t: Float, from: Float, to: Float) =
            if (to <= from) (if (t >= to) 1f else 0f) else ((t - from) / (to - from)).coerceIn(0f, 1f)

        fun easeInOutSine(t: Float) = (1 - cos(PI * t).toFloat()) / 2

        /** How far through the shapes we are: up on the inhale, held, then back down. */
        fun build(t: Float) = when {
            t < INHALE -> easeInOutSine(t / INHALE)
            t < INHALE + TOP_REST -> 1f
            t < INHALE + TOP_REST + EXHALE -> 1 - easeInOutSine((t - INHALE - TOP_REST) / EXHALE)
            else -> 0f
        }
    }
}
