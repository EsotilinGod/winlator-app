package com.example.rinlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Escena horizontal del fondo animado de Rin, dibujada por el propio launcher (no depende de que el
 * sistema gire el fondo de pantalla, cosa que HyperOS no hace):
 *  - fondo original sin Rin, traspuesto a horizontal (land_bg.jpg) con sus circuitos iluminados;
 *  - la ilustración ORIGINAL completa (sin recortes) en una columna a la derecha, fundida con el fondo,
 *    con la brisa en lazo, mechón, pañuelo y correa, y sus propios circuitos con corriente;
 *  - partículas.
 * [rinScale] redimensiona a Rin (0,45 = entera; 1 = escala del dibujo original).
 */
class RinSceneView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val MIN_SCALE = 0.45f
        const val MAX_SCALE = 1.6f
        private const val CENTER_X = 0.72f           // centro de Rin (fracción del ancho del fondo)
        private const val RIN_CENTER_IN_IMAGE = 610f // x del centro de Rin en la ilustración
        private const val RIBBON_Y = 24f             // y del extremo del lazo en el fondo (1220 px)
        private const val RIBBON_IN_IMAGE = 515f     // y del extremo del lazo en la ilustración
        private const val FADE = 160f                // px de fundido a cada lado de la columna
    }

    var rinScale = 1f
        set(v) { field = v.coerceIn(MIN_SCALE, MAX_SCALE); invalidate() }

    private val dp = resources.displayMetrics.density
    private var time = 0f

    // Ilustración (fondo fijo con el parche bajo las piezas) + piezas con brisa.
    private var image: Bitmap? = null
    private var layer: Bitmap? = null
    private var mesh: BreezeMesh? = null
    private var rinGlow: Bitmap? = null
    private var rinCurrent: CircuitCurrent? = null
    // Fondo lateral horizontal.
    private var landBg: Bitmap? = null
    private var landGlow: Bitmap? = null
    private var landCurrent: CircuitCurrent? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private var fadeKey = Float.NaN
    private val landMatrix = Matrix()
    private val dest = RectF()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 181, 48) }

    private class Particle(val x: Float, val y: Float, val speed: Float, val phase: Float)
    private val particles = List(22) {
        Particle(Random.nextFloat(), Random.nextFloat(), 0.4f + Random.nextFloat(), Random.nextFloat() * 6.28f)
    }

    init {
        val a = context.assets
        val mutable = BitmapFactory.Options().apply { inMutable = true }
        image = a.open("rin_wallpaper.jpg").use { BitmapFactory.decodeStream(it, null, mutable) }
        image?.let { base ->
            a.open("base_patch.png").use { BitmapFactory.decodeStream(it) }?.let { patch ->
                Canvas(base).drawBitmap(patch, BreezeMesh.LAYER_X, BreezeMesh.LAYER_Y, null)
                patch.recycle()
            }
            mesh = BreezeMesh(base.width, base.height)
        }
        layer = a.open("breeze_layer.png").use { BitmapFactory.decodeStream(it) }
        rinGlow = a.open("rin_glow.png").use { BitmapFactory.decodeStream(it) }
        landBg = a.open("land_bg.jpg").use { BitmapFactory.decodeStream(it) }
        landGlow = a.open("land_glow.png").use { BitmapFactory.decodeStream(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val data = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB) // datos, no color
            }
            rinCurrent = current(rinGlow, "rin_flow.png", data)
            landCurrent = current(landGlow, "land_flow.png", data)
        }
    }

    private fun current(glow: Bitmap?, flowAsset: String, opts: BitmapFactory.Options): CircuitCurrent? {
        val g = glow ?: return null
        val flow = context.assets.open(flowAsset).use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        return try { CircuitCurrent(g, flow) } catch (_: Exception) { null }
    }

    fun setTime(t: Float) {
        time = t
        invalidate()
    }

    /** Brillo que respira + corriente (Android 13+) sobre una capa de circuitos a media resolución. */
    private fun drawCircuits(canvas: Canvas, g: Bitmap?, cur: CircuitCurrent?, tx: Float, ty: Float, dw: Float, dh: Float) {
        if (g == null) return
        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(dw / g.width, dh / g.height)
        val breathing = 0.22f + 0.08f * sin(2f * PI.toFloat() * time / 6f)
        if (cur != null) {
            cur.update(time, breathing)
            canvas.drawRect(0f, 0f, g.width.toFloat(), g.height.toFloat(), cur.paint)
        } else {
            glowPaint.alpha = (breathing * 255).toInt()
            canvas.drawBitmap(g, 0f, 0f, glowPaint)
        }
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val img = image ?: return
        val bg = landBg ?: return
        val w = width.toFloat(); val h = height.toFloat()

        // Fondo lateral en modo "cubrir".
        val bs = max(w / bg.width, h / bg.height)
        val bx = (w - bg.width * bs) / 2f
        val by = (h - bg.height * bs) / 2f
        landMatrix.setScale(bs, bs); landMatrix.postTranslate(bx, by)
        canvas.drawBitmap(bg, landMatrix, paint)
        drawCircuits(canvas, landGlow, landCurrent, bx, by, bg.width * bs, bg.height * bs)

        // Columna con la ilustración original.
        val s = bs * rinScale
        val colW = img.width * s
        val colLeft = bx + CENTER_X * bg.width * bs - RIN_CENTER_IN_IMAGE * s
        val ty = (by + RIBBON_Y * bs - RIBBON_IN_IMAGE * s).coerceIn(h - img.height * s, 0f)
        val fade = FADE * bs
        val key = colLeft * 31f + colW
        if (key != fadeKey) {
            fadeKey = key
            fadePaint.shader = LinearGradient(colLeft, 0f, colLeft + colW, 0f,
                intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, fade / colW, 1f - fade / colW, 1f), Shader.TileMode.CLAMP)
        }
        val save = canvas.saveLayer(colLeft, 0f, colLeft + colW, h, null)
        dest.set(colLeft, ty, colLeft + colW, ty + img.height * s)
        canvas.drawBitmap(img, null, dest, paint)
        drawCircuits(canvas, rinGlow, rinCurrent, colLeft, ty, colW, img.height * s)
        val m = mesh; val l = layer
        if (m != null && l != null) {
            m.update(time)
            canvas.save()
            canvas.translate(colLeft, ty)
            canvas.scale(s, s)
            canvas.translate(BreezeMesh.LAYER_X, BreezeMesh.LAYER_Y)
            canvas.drawBitmapMesh(l, m.cols, m.rows, m.verts, 0, null, 0, paint)
            canvas.restore()
        }
        canvas.drawRect(colLeft, 0f, colLeft + colW, h, fadePaint)
        canvas.restoreToCount(save)

        // Partículas.
        for (p in particles) {
            val raw = (p.y - time * p.speed * 0.012f) % 1f
            val y = if (raw < 0f) raw + 1f else raw
            val x = p.x + sin(time * 0.3f + p.phase) * 0.004f
            dotPaint.alpha = (40 + 40 * (0.5f + 0.5f * sin(time * 0.9f + p.phase))).toInt()
            canvas.drawCircle(x * w, y * h, 2.2f * dp, dotPaint)
        }
    }
}
