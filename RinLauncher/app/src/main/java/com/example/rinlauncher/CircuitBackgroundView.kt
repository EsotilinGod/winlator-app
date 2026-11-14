package com.example.rinlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fondo horizontal con circuitos dorados generados (misma estética que el fondo animado de Rin).
 *
 * - La parte estática (degradado, bandas diagonales grises, pistas y nodos) se dibuja una sola vez en
 *   un bitmap al cambiar el tamaño.
 * - Cada [PERIOD] s una corriente de luz recorre todas las pistas; sale primero de las más cercanas a
 *   Rin (lado derecho) y avanza hacia la izquierda.
 * - Partículas doradas tenues con deriva lenta.
 */
class CircuitBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        const val PERIOD = 6f
        const val TRACES = 30
    }

    private val dp = resources.displayMetrics.density
    private var staticLayer: Bitmap? = null
    private val traces = mutableListOf<Trace>()
    private val segment = Path()
    private var time = 0f

    private class Trace(val measure: PathMeasure, val delay: Float)
    private class Particle(val x: Float, val y: Float, val speed: Float, val phase: Float)

    private val particles = List(22) {
        Particle(Random.nextFloat(), Random.nextFloat(), 0.4f + Random.nextFloat(), Random.nextFloat() * 6.28f)
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = Color.rgb(255, 196, 64); alpha = 70; strokeWidth = 9 * dp
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = Color.rgb(255, 214, 110); strokeWidth = 3 * dp
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 236, 170) }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 181, 48) }
    private val pos = FloatArray(2)

    fun setTime(t: Float) {
        time = t
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        staticLayer?.recycle()
        traces.clear()
        val rnd = Random(2301) // semilla fija: el diseño es siempre el mismo
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Degradado de base.
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(),
                Color.rgb(251, 252, 254), Color.rgb(236, 239, 245), Shader.TileMode.CLAMP)
        })
        // Bandas diagonales grises, como en la ilustración.
        val band = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(16) {
            band.color = Color.argb(14 + rnd.nextInt(26), 150, 160, 180)
            val x0 = rnd.nextFloat() * (w + h) - h
            val bw = (20 + rnd.nextInt(90)) * dp
            val p = Path().apply {
                moveTo(x0, h.toFloat()); lineTo(x0 + bw, h.toFloat())
                lineTo(x0 + bw + h, 0f); lineTo(x0 + h, 0f); close()
            }
            c.drawPath(p, band)
        }

        // Pistas: tramos a 0°, ±45° y 90°, en una cuadrícula de 18 dp.
        val unit = 18 * dp
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 7 * dp; strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND; color = Color.argb(34, 255, 190, 60)
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.2f * dp; strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND; color = Color.argb(150, 242, 181, 48)
        }
        val node = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.2f * dp; color = Color.argb(170, 242, 181, 48)
        }
        repeat(TRACES) {
            // Salen del borde derecho (donde está Rin), superior o inferior, y se adentran.
            var x: Float; var y: Float; var dir: Int // dir en múltiplos de 45°, 0 = derecha, 4 = izquierda
            when (rnd.nextInt(4)) {
                0, 1 -> { x = w * (0.62f + rnd.nextFloat() * 0.3f); y = h * rnd.nextFloat(); dir = listOf(4, 3, 5)[rnd.nextInt(3)] }
                2 -> { x = w * rnd.nextFloat(); y = 0f; dir = listOf(2, 3, 1)[rnd.nextInt(3)] }
                else -> { x = w * rnd.nextFloat(); y = h.toFloat(); dir = listOf(6, 5, 7)[rnd.nextInt(3)] }
            }
            val p = Path().apply { moveTo(x, y) }
            val startX = x
            val segments = 3 + rnd.nextInt(4)
            for (s in 0 until segments) {
                val len = (2 + rnd.nextInt(7)) * unit
                val ang = dir * Math.PI / 4
                x += (cos(ang) * len).toFloat(); y -= (sin(ang) * len).toFloat()
                p.lineTo(x, y)
                dir = (dir + listOf(-1, 0, 0, 1)[rnd.nextInt(4)] + 8) % 8
                if (dir == 0 || dir == 1 || dir == 7) dir = 4 // que no vuelva hacia Rin
            }
            c.drawPath(p, halo)
            c.drawPath(p, line)
            c.drawCircle(x, y, 4.5f * dp, node)
            // La corriente nace en Rin: las pistas más a la derecha se encienden antes.
            traces += Trace(PathMeasure(p, false), (1f - startX / w) * 1.4f)
        }
        staticLayer = bmp
    }

    override fun onDraw(canvas: Canvas) {
        staticLayer?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        // Corriente periódica: tramo luminoso de 70 dp con cabeza brillante.
        val speed = 260 * dp
        val pulseLen = 70 * dp
        val cycle = time % PERIOD
        for (tr in traces) {
            val head = (cycle - tr.delay) * speed
            val len = tr.measure.length
            if (head <= 0f || head - pulseLen >= len) continue
            segment.reset()
            tr.measure.getSegment(max(0f, head - pulseLen), minOf(head, len), segment, true)
            canvas.drawPath(segment, glowPaint)
            canvas.drawPath(segment, corePaint)
            if (head < len) {
                tr.measure.getPosTan(head, pos, null)
                canvas.drawCircle(pos[0], pos[1], 3.2f * dp, headPaint)
            }
        }

        // Partículas.
        val w = width.toFloat(); val h = height.toFloat()
        for (p in particles) {
            val raw = (p.y - time * p.speed * 0.012f) % 1f
            val y = if (raw < 0f) raw + 1f else raw
            val x = p.x + sin(time * 0.3f + p.phase) * 0.004f
            dotPaint.alpha = (40 + 40 * (0.5f + 0.5f * sin(time * 0.9f + p.phase))).toInt()
            canvas.drawCircle(x * w, y * h, 2.2f * dp, dotPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        staticLayer?.recycle(); staticLayer = null
    }
}
