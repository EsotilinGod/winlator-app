package com.example.rinlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

/**
 * Introducción al arrancar el launcher, al estilo de un encendido de consola, sincronizada con el sonido
 * de inicio (≈ 10 s; la animación dura 7,8 s y el audio continúa sobre el menú):
 *   0–0,8 s   negro con un resplandor que aparece
 *   0,6–3 s   cintas de luz blancas que barren la pantalla de derecha a izquierda
 *   2,6–3,6 s "RIN LAUNCHER" (fuente assets/fonts/intro.otf) aparece y sus letras se juntan
 *   ≈4,2 s    destello dorado que recorre el texto
 *   6–7,8 s   el texto se desvanece y la pantalla se funde hacia el menú
 * Tocar o pulsar un botón la salta (fundido de 0,4 s).
 */
class IntroView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var onFinished: (() -> Unit)? = null
    val running get() = active

    private var active = false
    private var time = 0f
    private var skipAt = -1f

    private val font: Typeface = try { Typeface.createFromAsset(context.assets, "fonts/intro.otf") } catch (_: Exception) { Typeface.DEFAULT_BOLD }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textAlign = Paint.Align.CENTER }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
    private val glowPaint = Paint()
    private val path = Path()

    private companion object {
        const val TOTAL = 7.8f
        const val TITLE = "RIN LAUNCHER"
    }

    fun start() {
        active = true; time = 0f; skipAt = -1f
        alpha = 1f
        visibility = VISIBLE
        invalidate()
    }

    fun skip() {
        if (active && skipAt < 0f) skipAt = time
    }

    /** Avanza la animación; [t] = segundos desde start(). */
    fun setTime(t: Float) {
        if (!active) return
        time = t
        val end = if (skipAt >= 0f) skipAt + 0.4f else TOTAL
        // Fundido final hacia el menú.
        alpha = when {
            skipAt >= 0f -> (1f - (t - skipAt) / 0.4f).coerceIn(0f, 1f)
            else -> (1f - (t - 6.6f) / 1.2f).coerceIn(0f, 1f)
        }
        if (t >= end) {
            active = false
            visibility = GONE
            onFinished?.invoke()
            return
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) skip()
        return active
    }

    private fun ramp(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    private fun smooth(x: Float) = x * x * (3 - 2 * x)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = time
        canvas.drawColor(Color.BLACK)

        // Resplandor central azul muy tenue.
        val glow = smooth(ramp(t, 0f, 0.8f))
        glowPaint.shader = RadialGradient(w * 0.5f, h * 0.55f, w * 0.6f,
            Color.argb((70 * glow).toInt(), 70, 90, 150), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        // Cintas de luz: aparecen barriendo de derecha a izquierda y siguen ondulando.
        val reveal = smooth(ramp(t, 0.6f, 3.0f))
        val ribbonAlpha = smooth(ramp(t, 0.6f, 1.6f)) * (1f - 0.55f * ramp(t, 4.5f, 6.5f))
        if (reveal > 0f) {
            canvas.save()
            canvas.clipRect(w * (1f - reveal), 0f, w, h)
            val u = h / 610f
            linePaint.shader = LinearGradient(0f, 0f, w, 0f,
                intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.2f, 0.8f, 1f), Shader.TileMode.CLAMP)
            val ribbons = arrayOf(floatArrayOf(395f, 40f, 0f, 2.2f), floatArrayOf(410f, 30f, 1.2f, 1.4f),
                floatArrayOf(383f, 52f, 2.3f, 1.0f), floatArrayOf(425f, 22f, 3.5f, 1.8f), floatArrayOf(403f, 34f, 4.4f, 0.8f))
            for ((i, r) in ribbons.withIndex()) {
                val ph = r[2] + t * 0.35f
                path.reset()
                var x = 0f
                while (x <= w) {
                    val xd = x / u
                    val y = (r[0] + r[1] * sin(xd / 230 + ph) + 12 * sin(xd / 95 + ph * 1.7f)) * u
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 10f * u
                }
                linePaint.strokeWidth = r[3] * u
                linePaint.alpha = (ribbonAlpha * (200 - i * 25)).toInt().coerceIn(0, 255)
                linePaint.setShadowLayer(6f * u, 0f, 0f, Color.argb((ribbonAlpha * 160).toInt(), 255, 255, 255))
                canvas.drawPath(path, linePaint)
            }
            canvas.restore()
        }

        // Título.
        val inT = smooth(ramp(t, 2.6f, 3.6f))
        val outT = 1f - smooth(ramp(t, 6.0f, 7.0f))
        val a = inT * outT
        if (a > 0f) {
            textPaint.textSize = h * 0.105f
            textPaint.letterSpacing = 0.18f + 0.35f * (1f - inT) // las letras se juntan al aparecer
            textPaint.alpha = (255 * a).toInt()
            textPaint.setShadowLayer(h * 0.025f, 0f, 0f, Color.argb((200 * a).toInt(), 255, 255, 255))
            val baseY = h * 0.5f + textPaint.textSize * 0.35f
            canvas.drawText(TITLE, w / 2f, baseY, textPaint)

            // Destello dorado que cruza el texto.
            val s = ramp(t, 3.9f, 4.9f)
            if (s > 0f && s < 1f) {
                val cx = w * (0.15f + 0.7f * s)
                shinePaint.textSize = textPaint.textSize
                shinePaint.letterSpacing = textPaint.letterSpacing
                shinePaint.shader = LinearGradient(cx - w * 0.08f, 0f, cx + w * 0.08f, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.argb((230 * a).toInt(), 255, 214, 120), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP)
                canvas.drawText(TITLE, w / 2f, baseY, shinePaint)
            }

            // Línea fina bajo el título.
            linePaint.shader = null
            linePaint.clearShadowLayer()
            linePaint.strokeWidth = h * 0.002f
            linePaint.alpha = (110 * a).toInt()
            val half = w * 0.22f * inT
            canvas.drawLine(w / 2f - half, baseY + h * 0.05f, w / 2f + half, baseY + h * 0.05f, linePaint)
        }
    }
}
