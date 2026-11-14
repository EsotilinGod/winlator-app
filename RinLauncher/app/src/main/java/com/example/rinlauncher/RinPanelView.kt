package com.example.rinlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Rin recortada (PNG con transparencia) sobre los circuitos del launcher, en el lado derecho.
 *
 * - rin_static.png: Rin sin fondo y sin las piezas que se mecen (tools/make_rin_cutout.py).
 * - breeze_layer.png + BreezeMesh: lazo, mechón, pañuelo y correa con brisa.
 * - Un halo dorado difuminado alrededor de la silueta la integra con la luz de los circuitos.
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
    }

    private var rin: Bitmap? = null       // Rin estática, ya escalada a pantalla
    private var aura: Bitmap? = null      // alfa difuminado de la silueta
    private val auraOffset = IntArray(2)
    private var layer: Bitmap? = null     // piezas que se mecen (resolución original)
    private var mesh: BreezeMesh? = null
    private var scale = 1f
    private var time = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 255, 200, 70) }

    /** Ancho en píxeles que ocupa Rin en pantalla (para dejarle espacio al contenido). */
    var panelWidth = 0
        private set

    /** Píxeles de pantalla desde el borde izquierdo del panel hasta donde empieza el pañuelo. */
    val clearZone get() = ((185f - CROP_X) * scale).toInt()

    fun setTime(t: Float) {
        time = t
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        rin?.recycle(); aura?.recycle()
        scale = h / CROP_H
        panelWidth = (CROP_W * scale).toInt()

        val full = context.assets.open("rin_static.png").use { BitmapFactory.decodeStream(it) } ?: return
        val scaled = Bitmap.createScaledBitmap(full, panelWidth, h, true)
        if (scaled !== full) full.recycle()
        rin = scaled
        // Halo: alfa de la silueta con desenfoque (se calcula una sola vez).
        val blur = Paint().apply { maskFilter = BlurMaskFilter(22 * resources.displayMetrics.density, BlurMaskFilter.Blur.NORMAL) }
        aura = scaled.extractAlpha(blur, auraOffset)

        if (layer == null) {
            layer = context.assets.open("breeze_layer.png").use { BitmapFactory.decodeStream(it) }
            mesh = BreezeMesh(1220, 2712)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val r = rin ?: return
        val left = (width - panelWidth).toFloat()
        aura?.let { canvas.drawBitmap(it, left + auraOffset[0], auraOffset[1].toFloat(), auraPaint) }
        canvas.drawBitmap(r, left, 0f, paint)
        val m = mesh; val l = layer
        if (m != null && l != null) {
            m.update(time)
            canvas.save()
            canvas.translate(left, 0f)
            canvas.scale(scale, scale)
            canvas.translate(BreezeMesh.LAYER_X - CROP_X, BreezeMesh.LAYER_Y - CROP_Y)
            canvas.drawBitmapMesh(l, m.cols, m.rows, m.verts, 0, null, 0, paint)
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rin?.recycle(); rin = null
        aura?.recycle(); aura = null
        layer?.recycle(); layer = null
    }
}
