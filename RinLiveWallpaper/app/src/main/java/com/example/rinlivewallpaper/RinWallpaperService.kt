package com.example.rinlivewallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
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

class RinWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = RinEngine()

    inner class RinEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var bitmap: Bitmap? = null
        private var breeze: BreezeMesh? = null
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
            bitmap = assets.open("rin_wallpaper.jpg").use { BitmapFactory.decodeStream(it) }
            breeze = bitmap?.let { BreezeMesh(it.width, it.height) }
            glow = assets.open("circuit_glow.png").use { BitmapFactory.decodeStream(it) }
            glowShader = glow?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
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
            glow?.recycle(); glow = null
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

        private fun render(canvas: Canvas) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            canvas.drawColor(Color.WHITE)
            val image = bitmap ?: return
            val scale = maxOf(w / image.width, h / image.height)
            val dw = image.width * scale
            val dh = image.height * scale
            val left = (w - dw) / 2f
            val top = (h - dh) / 2f
            val elapsed = (SystemClock.uptimeMillis() - startTime) / 1000f

            // Ilustración con brisa: misma imagen, deformada solo en pelo, lazo, pañuelo y correa.
            val mesh = breeze
            if (mesh != null) {
                mesh.update(elapsed)
                canvas.save()
                canvas.translate(left, top)
                canvas.scale(scale, scale)
                canvas.drawBitmapMesh(image, mesh.cols, mesh.rows, mesh.verts, 0, null, 0, paint)
                canvas.restore()
            } else {
                dest.set(left, top, left + dw, top + dh)
                canvas.drawBitmap(image, null, dest, paint)
            }

            // Circuitos: brillo tenue que respira + luces que recorren los trazos.
            val g = glow
            val gs = glowShader
            if (g != null && gs != null) {
                canvas.save()
                canvas.translate(left, top)
                canvas.scale(dw / g.width, dh / g.height)
                glowBasePaint.alpha = (56 + 20 * sin(2f * PI.toFloat() * elapsed / 6f)).toInt()
                canvas.drawBitmap(g, 0f, 0f, glowBasePaint)
                for (f in flows) {
                    f.update(elapsed, gs)
                    canvas.drawRect(0f, 0f, g.width.toFloat(), g.height.toFloat(), f.paint)
                }
                canvas.restore()
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
