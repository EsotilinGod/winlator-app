package com.example.rinlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/**
 * Rin grande detrás del carrusel. Con [HOLOGRAM] = false (por defecto) se dibuja a todo color con un
 * halo dorado suave; con true, como holograma dorado.
 *
 * Por fotograma, todo se compone en UNA capa (saveLayer) que al cerrarse aplica la opacidad y el
 * filtro de color del holograma:
 *  1. Rin estática (rin_static.png, preescalada) + piezas con brisa (breeze_layer.png + BreezeMesh),
 *     con una leve "respiración" (dilatación vertical).
 *  2. Líneas de escaneo (DST_OUT), banda de luz que recorre la figura (SRC_ATOP) y desvanecido
 *     inferior (DST_IN), como una proyección.
 * Detrás, un halo dorado difuminado de la silueta. Cada ~7 s, un parpadeo breve de interferencia.
 */
class RinPanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        // Recorte de la ilustración original (1220 × 2712) al que corresponde rin_static.png.
        const val CROP_X = 40f
        const val CROP_Y = 440f
        const val CROP_W = 1140f
        const val CROP_H = 2160f
        const val SIZE = 1.45f          // alto de la figura respecto al alto de pantalla
        const val CENTER_X = 0.76f      // centro horizontal de la figura (fracción del ancho)
        const val RIBBON_TOP = 75f      // y del extremo del lazo dentro del recorte
        const val BREATH_PERIOD = 4.2f
        const val SWEEP_PERIOD = 5f
        const val GLITCH_PERIOD = 7.3f
        /** true = holograma dorado (tinte, escaneo, banda de luz, interferencia); false = Rin a todo color. */
        const val HOLOGRAM = false
    }

    private val dp = resources.displayMetrics.density
    private var rin: Bitmap? = null
    private var aura: Bitmap? = null
    private val auraOffset = IntArray(2)
    private var layer: Bitmap? = null
    private var mesh: BreezeMesh? = null
    private var scale = 1f
    private var left = 0f
    private var top = 0f
    private var time = 0f
    private val bounds = RectF()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (HOLOGRAM) Color.argb(110, 255, 190, 50) else Color.argb(75, 255, 190, 50) }

    /** Al cerrar la capa: opacidad + tinte dorado (mezcla 50 % dorado por luminancia, 50 % original). */
    private val layerPaint = Paint().apply {
        val k = 0.50f
        val g = floatArrayOf(1.0f * 1.15f, 0.82f * 1.15f, 0.42f * 1.15f)
        val lr = 0.30f; val lg = 0.59f; val lb = 0.11f
        fun row(i: Int, own: Int): FloatArray {
            val r = FloatArray(5)
            r[0] = k * g[i] * lr; r[1] = k * g[i] * lg; r[2] = k * g[i] * lb
            r[own] += 1 - k
            r[4] = 6f
            return r
        }
        colorFilter = ColorMatrixColorFilter(ColorMatrix(row(0, 0) + row(1, 1) + row(2, 2) + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
    }
    private val scanPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); alpha = 95 }
    private val sweepPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val sweepMatrix = Matrix()
    private var sweepShader: LinearGradient? = null

    /** Fracción del ancho donde el contenido debería terminar para no tapar la cara de Rin. */
    val contentEndFraction get() = CENTER_X - 0.07f

    fun setTime(t: Float) {
        time = t
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        rin?.recycle(); aura?.recycle()
        scale = h * SIZE / CROP_H
        val fw = (CROP_W * scale).toInt(); val fh = (CROP_H * scale).toInt()
        left = w * CENTER_X - fw / 2f
        top = h * 0.02f - RIBBON_TOP * scale
        bounds.set(left - 40 * dp, 0f, left + fw + 40 * dp, h.toFloat())

        val full = context.assets.open("rin_static.png").use { BitmapFactory.decodeStream(it) } ?: return
        rin = Bitmap.createScaledBitmap(full, fw, fh, true).also { if (it !== full) full.recycle() }
        aura = rin!!.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(26 * dp, BlurMaskFilter.Blur.NORMAL) }, auraOffset)

        if (layer == null) {
            layer = context.assets.open("breeze_layer.png").use { BitmapFactory.decodeStream(it) }
            mesh = BreezeMesh(1220, 2712)
        }

        // Líneas de escaneo: tesela de 3 dp con 1 dp opaco (se resta de la figura).
        val period = (3 * dp).toInt().coerceAtLeast(3)
        val tile = Bitmap.createBitmap(1, period, Bitmap.Config.ALPHA_8)
        for (y in 0 until (dp.toInt().coerceAtLeast(1))) tile.setPixel(0, y, Color.BLACK)
        scanPaint.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        // Desvanecido inferior: opaco hasta el 55 % de la altura, transparente en el borde.
        fadePaint.shader = LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        // Banda de luz de 140 dp.
        sweepShader = LinearGradient(0f, 0f, 0f, 140 * dp,
            intArrayOf(Color.TRANSPARENT, Color.argb(150, 255, 245, 210), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        sweepPaint.shader = sweepShader
    }

    /** Respiración: dilatación vertical de ±0,6 % con pivote en el borde inferior. */
    private fun breathAt(t: Float) = 1f + 0.006f * sin(2f * PI.toFloat() * t / BREATH_PERIOD)

    /** Rin estática + piezas con brisa, en coordenadas de pantalla. */
    private fun drawFigure(canvas: Canvas, r: Bitmap) {
        canvas.drawBitmap(r, left, top, paint)
        val m = mesh; val l = layer
        if (m != null && l != null) {
            m.update(time)
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            canvas.translate(BreezeMesh.LAYER_X - CROP_X, BreezeMesh.LAYER_Y - CROP_Y)
            canvas.drawBitmapMesh(l, m.cols, m.rows, m.verts, 0, null, 0, paint)
            canvas.restore()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val r = rin ?: return
        val h = height.toFloat()

        // Interferencia breve: desplazamiento lateral y caída de opacidad durante ~120 ms.
        val g = (time % GLITCH_PERIOD) / GLITCH_PERIOD
        val glitch = HOLOGRAM && g < 0.016f
        val dx = if (glitch) (if (g < 0.008f) 7 else -5) * dp else 0f
        layerPaint.alpha = if (glitch) 120 else (205 + 12 * sin(time * 2.1f)).toInt()
        if (!HOLOGRAM) {
            // A todo color: sin capa intermedia (más barato), solo halo + figura con respiración y brisa.
            canvas.save()
            canvas.scale(1f, breathAt(time), 0f, h)
            aura?.let { canvas.drawBitmap(it, left + auraOffset[0], top + auraOffset[1], auraPaint) }
            drawFigure(canvas, r)
            canvas.restore()
            return
        }

        // Respiración: dilatación vertical de ±0,6 % con pivote en el borde inferior.
        val breath = breathAt(time)

        canvas.save()
        canvas.translate(dx, 0f)
        canvas.scale(1f, breath, 0f, h)
        aura?.let { canvas.drawBitmap(it, left + auraOffset[0], top + auraOffset[1], auraPaint) }
        canvas.restore()

        val save = canvas.saveLayer(bounds, layerPaint)
        canvas.save()
        canvas.translate(dx, 0f)
        canvas.scale(1f, breath, 0f, h)
        drawFigure(canvas, r)
        canvas.restore()
        // Efectos del holograma, limitados a la figura por los modos de mezcla.
        canvas.drawRect(bounds, scanPaint)
        val sweepY = ((time % SWEEP_PERIOD) / SWEEP_PERIOD) * (h + 280 * dp) - 140 * dp
        sweepMatrix.setTranslate(0f, sweepY)
        sweepShader?.setLocalMatrix(sweepMatrix)
        sweepPaint.shader = sweepShader
        canvas.drawRect(bounds, sweepPaint)
        canvas.drawRect(bounds, fadePaint)
        canvas.restoreToCount(save)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rin?.recycle(); rin = null
        aura?.recycle(); aura = null
        layer?.recycle(); layer = null
    }
}
