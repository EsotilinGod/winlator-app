package com.example.rinlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fondo del launcher: el fondo de pantalla original de Rin, sin Rin y en horizontal
 * (tools/make_launcher_bg.py), con sus circuitos iluminados:
 * - brillo tenue que respira;
 * - Android 13+: corriente de luz periódica que nace de Rin y recorre las pistas (shader AGSL,
 *   el mismo del fondo animado);
 * - partículas doradas tenues.
 * La imagen se ajusta a la pantalla en modo "cubrir" (centerCrop).
 */
class CircuitBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    private var bg: Bitmap? = null
    private var glow: Bitmap? = null
    private var current: CircuitCurrent? = null
    private val bgMatrix = Matrix()
    private var time = 0f

    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 181, 48) }

    private class Particle(val x: Float, val y: Float, val speed: Float, val phase: Float)
    private val particles = List(22) {
        Particle(Random.nextFloat(), Random.nextFloat(), 0.4f + Random.nextFloat(), Random.nextFloat() * 6.28f)
    }

    init {
        bg = context.assets.open("launcher_bg.jpg").use { BitmapFactory.decodeStream(it) }
        glow = context.assets.open("circuit_glow.png").use { BitmapFactory.decodeStream(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB) // datos, no color
            }
            val flow = context.assets.open("circuit_flow.png").use { BitmapFactory.decodeStream(it, null, opts) }
            val g = glow
            if (flow != null && g != null) current = try { CircuitCurrent(g, flow) } catch (_: Exception) { null }
        }
    }

    fun setTime(t: Float) {
        time = t
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val b = bg ?: return
        val s = max(w.toFloat() / b.width, h.toFloat() / b.height)
        bgMatrix.setScale(s, s)
        bgMatrix.postTranslate((w - b.width * s) / 2f, (h - b.height * s) / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        val b = bg ?: return
        canvas.save()
        canvas.concat(bgMatrix)
        canvas.drawBitmap(b, 0f, 0f, bgPaint)
        glow?.let { g ->
            // Las capas de circuito están a media resolución.
            canvas.scale(b.width.toFloat() / g.width, b.height.toFloat() / g.height)
            val breathing = 0.22f + 0.08f * sin(2f * PI.toFloat() * time / 6f)
            val cur = current
            if (cur != null) {
                cur.update(time, breathing)
                canvas.drawRect(0f, 0f, g.width.toFloat(), g.height.toFloat(), cur.paint)
            } else {
                glowPaint.alpha = (breathing * 255).toInt()
                canvas.drawBitmap(g, 0f, 0f, glowPaint)
            }
        }
        canvas.restore()

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
        bg?.recycle(); bg = null
        glow?.recycle(); glow = null
    }
}
