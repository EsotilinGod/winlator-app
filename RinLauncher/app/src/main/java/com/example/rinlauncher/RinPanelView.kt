package com.example.rinlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Rin en el lado derecho de la pantalla horizontal, con la misma brisa del fondo animado
 * (solo se deforma la capa de lazo, mechón, pañuelo y correa; su fondo queda fijo).
 * El borde izquierdo se funde con los circuitos generados.
 */
class RinPanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        // Recorte de la ilustración (1220 × 2712) que se muestra: de la coronilla del lazo a las botas.
        const val CROP_X = 40f
        const val CROP_Y = 440f
        const val CROP_W = 1140f
        const val CROP_H = 2160f
        const val FADE = 130f // px de la ilustración que se funden por la izquierda
    }

    private var base: Bitmap? = null      // recorte ya escalado y con el borde fundido
    private var layer: Bitmap? = null     // piezas que se mecen
    private var mesh: BreezeMesh? = null
    private var scale = 1f
    private var time = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

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
        base?.recycle()
        scale = h / CROP_H
        panelWidth = (CROP_W * scale).toInt()

        // Fondo fijo: ilustración + parche reconstruido detrás de las piezas, recortada y escalada.
        val opts = BitmapFactory.Options().apply { inMutable = true }
        val full = context.assets.open("rin_wallpaper.jpg").use { BitmapFactory.decodeStream(it, null, opts) } ?: return
        context.assets.open("base_patch.png").use { BitmapFactory.decodeStream(it) }?.let { patch ->
            Canvas(full).drawBitmap(patch, BreezeMesh.LAYER_X, BreezeMesh.LAYER_Y, null)
            patch.recycle()
        }
        val out = Bitmap.createBitmap(panelWidth, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.save()
        c.scale(scale, scale)
        c.translate(-CROP_X, -CROP_Y)
        c.drawBitmap(full, 0f, 0f, paint)
        c.restore()
        full.recycle()
        // Fundido del borde izquierdo (DST_IN con un degradado de alfa).
        c.drawRect(0f, 0f, FADE * scale, h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, FADE * scale, 0f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
        base = out

        if (layer == null) {
            layer = context.assets.open("breeze_layer.png").use { BitmapFactory.decodeStream(it) }
            mesh = BreezeMesh(1220, 2712)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val b = base ?: return
        val left = (width - panelWidth).toFloat()
        canvas.drawBitmap(b, left, 0f, paint)
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
        base?.recycle(); base = null
        layer?.recycle(); layer = null
    }
}
