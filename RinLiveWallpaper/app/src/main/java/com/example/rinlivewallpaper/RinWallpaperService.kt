package com.example.rinlivewallpaper

import android.graphics.Bitmap
import android.graphics.PorterDuffXfermode
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val LAND_CENTER_X = 0.72f       // centro de Rin en horizontal (fracción del ancho del fondo)
private const val RIN_CENTER_IN_IMAGE = 610f  // x del centro de Rin dentro de la ilustración (1220 px)
private const val LAND_RIBBON_Y = 24f         // y del extremo del lazo en el fondo horizontal (1220 px)
private const val RIBBON_IN_IMAGE = 515f      // y del extremo del lazo dentro de la ilustración
private const val LAND_FADE = 160f            // px de fundido a cada lado de la columna

class RinWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = RinEngine()

    inner class RinEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var bitmap: Bitmap? = null
        private var breeze: BreezeMesh? = null
        private var breezeLayer: Bitmap? = null
        private var visible = false
        private var startTime = SystemClock.uptimeMillis()
        private val dest = RectF()
        private val frameDelay = 33L // objetivo aproximado: 30 FPS
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 213, 105)
            style = Paint.Style.FILL
        }
        private val particles = List(13) { Particle(Random.nextFloat(), Random.nextFloat(), 0.12f + Random.nextFloat() * 0.2f, Random.nextFloat() * 6.28f) }

        // Capa de brillo de los circuitos (generada con tools/make_circuit_glow.py, media resolución).
        private var glow: Bitmap? = null
        private var glowShader: BitmapShader? = null
        private val glowBasePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private var current: CircuitCurrent? = null // corriente por los trazos (Android 13+)
        private val flows = listOf(
            // Luces que recorren los trazos: dirección, periodo (px de la capa), velocidad (px/s), ancho, intensidad
            Flow(-1f, 1f, 450f, 110f, 0.12f, 217),
            Flow(1f, -1f, 650f, 80f, 0.09f, 153)
        )

        private val drawFrame = object : Runnable {
            override fun run() {
                if (!visible) return
                val t0 = SystemClock.uptimeMillis()
                drawWallpaper()
                val spent = SystemClock.uptimeMillis() - t0
                handler.postDelayed(this, (frameDelay - spent).coerceAtLeast(4L))
            }
        }

        init {
            // Fondo fijo: ilustración original + parche reconstruido detrás de las piezas móviles.
            val mutable = BitmapFactory.Options().apply { inMutable = true }
            bitmap = assets.open("rin_wallpaper.jpg").use { BitmapFactory.decodeStream(it, null, mutable) }
            bitmap?.let { base ->
                assets.open("base_patch.png").use { BitmapFactory.decodeStream(it) }?.let { patch ->
                    Canvas(base).drawBitmap(patch, BreezeMesh.LAYER_X, BreezeMesh.LAYER_Y, null)
                    patch.recycle()
                }
                breeze = BreezeMesh(base.width, base.height)
            }
            // Capa móvil: solo lazo, mechón, pañuelo y correa.
            breezeLayer = assets.open("breeze_layer.png").use { BitmapFactory.decodeStream(it) }
            glow = assets.open("circuit_glow.png").use { BitmapFactory.decodeStream(it) }
            glowShader = glow?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB) // datos, no color
                }
                val flow = assets.open("circuit_flow.png").use { BitmapFactory.decodeStream(it, null, opts) }
                val g = glow
                if (flow != null && g != null) {
                    current = try { CircuitCurrent(g, flow) } catch (_: Exception) { null }
                }
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) startTime = SystemClock.uptimeMillis() - (SystemClock.uptimeMillis() - startTime) % 3_600_000L
            handler.removeCallbacks(drawFrame)
            if (visible) handler.post(drawFrame)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (visible) drawWallpaper()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawFrame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacks(drawFrame)
            bitmap?.recycle(); bitmap = null
            breezeLayer?.recycle(); breezeLayer = null
            glow?.recycle(); glow = null
            landBg?.recycle(); landBg = null
            landGlow?.recycle(); landGlow = null
            super.onDestroy()
        }

        private fun drawWallpaper() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) holder.lockHardwareCanvas() else holder.lockCanvas()
                if (canvas != null) render(canvas)
            } catch (_: Exception) {
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }

        /** Visible para la prueba de captura (Robolectric). */
        internal fun render(canvas: Canvas) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            canvas.drawColor(Color.WHITE)
            val image = bitmap ?: return
            val elapsed = (SystemClock.uptimeMillis() - startTime) / 1000f

            if (w > h) {
                renderLandscape(canvas, w, h, elapsed)
            } else {
                // Vertical: la ilustración cubre la pantalla.
                val scale = maxOf(w / image.width, h / image.height)
                drawIllustration(canvas, (w - image.width * scale) / 2f, (h - image.height * scale) / 2f, scale, elapsed)
            }

            // Partículas tenues, con deriva vertical lenta.
            particles.forEach { particle ->
                val raw = (particle.y - elapsed * particle.speed * 0.018f) % 1f
                val y = if (raw < 0f) raw + 1f else raw
                val x = particle.x + sin(elapsed * 0.35f + particle.phase) * 0.006f
                val alpha = (35 + 35 * (0.5f + 0.5f * sin(elapsed * 0.8f + particle.phase))).toInt()
                dotPaint.alpha = alpha
                canvas.drawCircle(x * w, y * h, 2.0f, dotPaint)
            }
        }

        /**
         * Ilustración completa en coordenadas de pantalla: fondo fijo, circuitos con corriente y piezas
         * con brisa. [tx], [ty] = posición de la esquina superior izquierda; [s] = escala.
         */
        private fun drawIllustration(canvas: Canvas, tx: Float, ty: Float, s: Float, elapsed: Float) {
            val image = bitmap ?: return
            dest.set(tx, ty, tx + image.width * s, ty + image.height * s)
            canvas.drawBitmap(image, null, dest, paint)
            drawCircuits(canvas, glow, glowShader, current, tx, ty, image.width * s, image.height * s, elapsed)
            val mesh = breeze
            val layer = breezeLayer
            if (mesh != null && layer != null) {
                mesh.update(elapsed)
                canvas.save()
                canvas.translate(tx, ty)
                canvas.scale(s, s)
                canvas.translate(BreezeMesh.LAYER_X, BreezeMesh.LAYER_Y)
                canvas.drawBitmapMesh(layer, mesh.cols, mesh.rows, mesh.verts, 0, null, 0, paint)
                canvas.restore()
            }
        }

        /** Brillo que respira + corriente (Android 13+) o bandas (anteriores) sobre una capa de circuitos. */
        private fun drawCircuits(canvas: Canvas, g: Bitmap?, gs: BitmapShader?, cur: CircuitCurrent?,
                                 tx: Float, ty: Float, dw: Float, dh: Float, elapsed: Float) {
            if (g == null || gs == null) return
            canvas.save()
            canvas.translate(tx, ty)
            canvas.scale(dw / g.width, dh / g.height)
            val breathing = 0.22f + 0.08f * sin(2f * PI.toFloat() * elapsed / 6f)
            if (cur != null) {
                cur.update(elapsed, breathing)
                canvas.drawRect(0f, 0f, g.width.toFloat(), g.height.toFloat(), cur.paint)
            } else {
                glowBasePaint.alpha = (breathing * 255).toInt()
                canvas.drawBitmap(g, 0f, 0f, glowBasePaint)
                for (f in flows) {
                    f.update(elapsed, gs)
                    canvas.drawRect(0f, 0f, g.width.toFloat(), g.height.toFloat(), f.paint)
                }
            }
            canvas.restore()
        }

        // --- Horizontal ------------------------------------------------------------------------
        // La ilustración se muestra a escala 1:1 respecto al alto de pantalla (del lazo a las piernas)
        // en una columna desplazada a la derecha; los lados muestran el fondo original sin Rin y
        // traspuesto a horizontal (tools/make_launcher_bg.py), con la misma escala de circuitos.
        // Los bordes de la columna se funden con ese fondo.
        private var landBg: Bitmap? = null
        private var landGlow: Bitmap? = null
        private var landGlowShader: BitmapShader? = null
        private var landCurrent: CircuitCurrent? = null
        private val landMatrix = Matrix()
        private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        private var fadeKey = 0f

        private fun loadLandscape() {
            if (landBg != null) return
            landBg = assets.open("land_bg.jpg").use { BitmapFactory.decodeStream(it) }
            landGlow = assets.open("land_glow.png").use { BitmapFactory.decodeStream(it) }
            landGlowShader = landGlow?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                }
                val flow = assets.open("land_flow.png").use { BitmapFactory.decodeStream(it, null, opts) }
                val g = landGlow
                if (flow != null && g != null) landCurrent = try { CircuitCurrent(g, flow) } catch (_: Exception) { null }
            }
        }

        private fun renderLandscape(canvas: Canvas, w: Float, h: Float, elapsed: Float) {
            loadLandscape()
            val image = bitmap ?: return
            val bg = landBg ?: return
            // Fondo lateral (2712 × 1220) en modo "cubrir".
            val bs = maxOf(w / bg.width, h / bg.height)
            val bx = (w - bg.width * bs) / 2f
            val by = (h - bg.height * bs) / 2f
            landMatrix.setScale(bs, bs); landMatrix.postTranslate(bx, by)
            canvas.drawBitmap(bg, landMatrix, paint)
            drawCircuits(canvas, landGlow, landGlowShader, landCurrent, bx, by, bg.width * bs, bg.height * bs, elapsed)

            // Columna con la ilustración: misma escala que el fondo lateral (1 px de ilustración = bs).
            val s = bs
            val colW = image.width * s
            val colLeft = bx + (LAND_CENTER_X * bg.width - RIN_CENTER_IN_IMAGE) * s
            val ty = by + (LAND_RIBBON_Y - RIBBON_IN_IMAGE) * s
            val fade = LAND_FADE * s
            if (colLeft + fade * 7919f != fadeKey) {
                fadeKey = colLeft + fade * 7919f
                fadePaint.shader = LinearGradient(colLeft, 0f, colLeft + colW, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                    floatArrayOf(0f, fade / colW, 1f - fade / colW, 1f), Shader.TileMode.CLAMP)
            }
            val save = canvas.saveLayer(colLeft, 0f, colLeft + colW, h, null)
            drawIllustration(canvas, colLeft, ty, s, elapsed)
            canvas.drawRect(colLeft, 0f, colLeft + colW, h, fadePaint)
            canvas.restoreToCount(save)
        }
    }
}

/**
 * Banda de luz que se desplaza a lo largo de [ux],[uy]. Se multiplica (DST_IN) por la capa de
 * circuitos, de modo que la luz solo aparece donde hay trazos dorados.
 */
private class Flow(ux: Float, uy: Float, private val period: Float, private val speed: Float, width: Float, intensity: Int) {
    private val nx: Float
    private val ny: Float
    private val gradient: LinearGradient
    private val matrix = Matrix()
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = intensity }

    init {
        val n = sqrt(ux * ux + uy * uy)
        nx = ux / n; ny = uy / n
        val clear = 0x00FFFFFF
        val soft = 0x55FFFFFF
        gradient = LinearGradient(
            0f, 0f, nx * period, ny * period,
            intArrayOf(clear, clear, soft, Color.WHITE, soft, clear, clear),
            floatArrayOf(0f, 0.5f - width, 0.5f - width * 0.45f, 0.5f, 0.5f + width * 0.45f, 0.5f + width, 1f),
            Shader.TileMode.REPEAT
        )
    }

    fun update(t: Float, glow: BitmapShader) {
        val d = (speed * t) % period
        matrix.setTranslate(nx * d, ny * d)
        gradient.setLocalMatrix(matrix)
        // Se recrea el ComposeShader para que el cambio de matriz se aplique en todas las versiones de Android.
        paint.shader = ComposeShader(glow, gradient, PorterDuff.Mode.DST_IN)
    }
}

private data class Particle(val x: Float, val y: Float, val speed: Float, val phase: Float)
