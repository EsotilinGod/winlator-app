package com.example.rinlivewallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renderiza un fotograma del fondo en vertical y en horizontal (2712 × 1220) a build/screenshots/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenderTest {
    @Test
    fun renderFrames() {
        val service = Robolectric.setupService(RinWallpaperService::class.java)
        val engine = service.onCreateEngine() as RinWallpaperService.RinEngine
        val out = File("build/screenshots").apply { mkdirs() }
        for ((name, w, h) in listOf(Triple("horizontal", 2712, 1220), Triple("vertical", 1220, 2712))) {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            engine.render(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Comando de tamaño (como lo envía Rin Launcher) y fotogramas horizontales resultantes.
        for (scale in listOf(0.45f, 1.4f)) {
            engine.onCommand(ACTION_SET_LANDSCAPE_SCALE, 0, 0, 0,
                android.os.Bundle().apply { putFloat(EXTRA_SCALE, scale) }, false)
            val bmp = Bitmap.createBitmap(2712, 1220, Bitmap.Config.ARGB_8888)
            engine.render(Canvas(bmp))
            File(out, "horizontal_${(scale * 100).toInt()}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        engine.onCommand(ACTION_SET_LANDSCAPE_SCALE, 0, 0, 0, android.os.Bundle().apply { putFloat(EXTRA_SCALE, 1f) }, false)
    }
}
