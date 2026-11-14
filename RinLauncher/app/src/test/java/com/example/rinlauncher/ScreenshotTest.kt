package com.example.rinlauncher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Abre la pantalla principal con apps de ejemplo en un "teléfono" horizontal de 2712 × 1220
 * (como el Xiaomi 14T) y guarda una captura en build/screenshots/. Verifica además que la carga de
 * datos, las categorías y la carátula funcionan.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w904dp-h407dp-land-xxhdpi")
class ScreenshotTest {

    private val apps = listOf(
        "com.miHoYo.GenshinImpact" to "Genshin Impact",
        "com.winlator" to "Winlator",
        "org.ppsspp.ppsspp" to "PPSSPP",
        "com.android.chrome" to "Chrome",
        "com.spotify.music" to "Spotify",
        "com.mojang.minecraftpe" to "Minecraft",
        "com.netflix.mediaclient" to "Netflix"
    )

    private fun installFakeApps() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val spm = shadowOf(ctx.packageManager)
        for ((pkg, label) in apps) {
            val ai = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label }
            val act = ActivityInfo().apply {
                packageName = pkg; name = "$pkg.Main"; applicationInfo = ai; nonLocalizedLabel = label; exported = true
            }
            spm.addOrUpdateActivity(act)
            spm.addResolveInfoForIntent(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                ResolveInfo().apply { activityInfo = act; nonLocalizedLabel = label }
            )
        }
        // Datos guardados: 3 categorías, varias apps, una con nombre propio y otra con carátula.
        val covers = File(ctx.filesDir, "covers").apply { mkdirs() }
        val cover = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        Canvas(cover).drawRect(0f, 0f, 600f, 800f, Paint().apply {
            shader = LinearGradient(0f, 0f, 600f, 800f, Color.rgb(70, 110, 220), Color.rgb(240, 120, 160), Shader.TileMode.CLAMP)
        })
        File(covers, "genshin.jpg").outputStream().use { cover.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val root = JSONObject()
        root.put("categories", JSONArray()
            .put(JSONObject().put("id", "c1").put("name", "Juegos"))
            .put(JSONObject().put("id", "c2").put("name", "Emuladores"))
            .put(JSONObject().put("id", "c3").put("name", "Multimedia")))
        val arr = JSONArray()
        apps.forEachIndexed { i, (pkg, _) ->
            arr.put(JSONObject().put("pkg", pkg).put("activity", "$pkg.Main")
                .put("customLabel", if (pkg == "org.ppsspp.ppsspp") "PSP" else "")
                .put("cover", if (i == 0) "genshin.jpg" else "")
                .put("categories", JSONArray(if (i < 3 || i == 5) listOf("c1") else listOf("c3"))))
        }
        root.put("apps", arr)
        File(ctx.filesDir, "launcher.json").writeText(root.toString())
    }

    @Test
    fun mainScreen() {
        installFakeApps()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        repeat(5) { shadowOf(Looper.getMainLooper()).idle() }

        val store = LauncherStore(activity).apply { load() }
        assertEquals(apps.size, store.apps.size)
        assertEquals("PSP", store.label(store.apps[2]))
        assertTrue(store.coverFile(store.apps[0]) != null)

        // Centra la segunda tarjeta y muestra la corriente de luz a mitad de recorrido.
        activity.findViewById<CircuitBackgroundView>(R.id.background).setTime(1.6f)
        activity.findViewById<RinPanelView>(R.id.rinPanel).setTime(1.0f)
        val rv = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.carousel)
        (rv.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).scrollToPositionWithOffset(1, 0)
        repeat(5) { shadowOf(Looper.getMainLooper()).idle() }
        rv.scrollBy(1, 0); rv.scrollBy(-1, 0)
        shadowOf(Looper.getMainLooper()).idle()

        val root = activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "main.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(2712, root.width)
    }
}
