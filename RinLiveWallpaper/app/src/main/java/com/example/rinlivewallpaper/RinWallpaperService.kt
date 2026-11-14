package com.example.rinlivewallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
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
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 190, 55)
            style = Paint.Style.FILL
            alpha = 70
        }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 246, 190)
            style = Paint.Style.FILL
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 213, 105)
            style = Paint.Style.FILL
        }
        private val paths = mutableListOf<PathMeasure>()
        private val path = Path()
        private val position = FloatArray(2)
        private val particles = List(13) { Particle(Random.nextFloat(), Random.nextFloat(), 0.12f + Random.nextFloat() * 0.2f, Random.nextFloat() * 6.28f) }

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
            buildCircuitPaths()
        }

        private fun buildCircuitPaths() {
            // Trazos normalizados que siguen los circuitos visibles del fondo, evitando el cuerpo.
            val traces = listOf(
                floatArrayOf(.00f,.35f, .10f,.35f, .16f,.39f, .30f,.39f, .35f,.42f),
                floatArrayOf(.00f,.49f, .11f,.49f, .17f,.46f, .29f,.46f, .34f,.48f),
                floatArrayOf(.00f,.61f, .12f,.61f, .18f,.58f, .27f,.58f),
                floatArrayOf(.98f,.10f, .91f,.10f, .88f,.14f, .88f,.27f, .83f,.30f),
                floatArrayOf(.99f,.32f, .93f,.32f, .89f,.36f, .89f,.47f, .84f,.50f),
                floatArrayOf(.99f,.67f, .94f,.67f, .91f,.70f, .91f,.82f, .86f,.85f),
                floatArrayOf(.45f,.00f, .45f,.08f, .42f,.12f, .42f,.20f),
                floatArrayOf(.67f,.00f, .67f,.08f, .71f,.12f, .71f,.20f),
                floatArrayOf(.25f,.90f, .31f,.86f, .39f,.86f, .45f,.81f),
                floatArrayOf(.70f,.94f, .75f,.90f, .80f,.90f, .85f,.86f)
            )
            for (coords in traces) {
                val p = Path()
                p.moveTo(coords[0], coords[1])
                var i = 2
                while (i < coords.size) { p.lineTo(coords[i], coords[i + 1]); i += 2 }
                paths.add(PathMeasure(p, false))
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
            bitmap?.recycle()
            bitmap = null
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
            // Cada pulso recorre lentamente un circuito; el brillo se mantiene discreto.
            paths.forEachIndexed { index, measure ->
                val length = measure.length
                val phase = (elapsed * (0.10f + (index % 3) * 0.025f) + index * 0.137f) % 1f
                measure.getPosTan(length * phase, position, null)
                val x = position[0] * w
                val y = position[1] * h
                canvas.drawCircle(x, y, 14f, glowPaint)
                canvas.drawCircle(x, y, 8f, glowPaint)
                canvas.drawCircle(x, y, 3.2f, corePaint)
            }
            // Partículas tenues, con deriva vertical lenta.
            particles.forEach { particle ->
                val raw = (particle.y - elapsed * particle.speed * 0.018f) % 1f
                val y = if (raw < 0f) raw + 1f else raw
                val x = particle.x + kotlin.math.sin(elapsed * 0.35f + particle.phase) * 0.006f
                val alpha = (35 + 35 * (0.5f + 0.5f * kotlin.math.sin(elapsed * 0.8f + particle.phase))).toInt()
                dotPaint.alpha = alpha
                canvas.drawCircle(x * w, y * h, 2.0f, dotPaint)
            }
        }

    }
}

private data class Particle(val x: Float, val y: Float, val speed: Float, val phase: Float)
