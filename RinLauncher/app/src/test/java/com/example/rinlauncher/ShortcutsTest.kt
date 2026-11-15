package com.example.rinlauncher

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShortcutsTest {

    private fun installPkg(pkg: String) {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = pkg })
    }

    /** Acceso directo clásico (EXTRA_SHORTCUT_INTENT): se guarda, sobrevive a recargar y abre su Intent. */
    @Test
    fun classicShortcut() {
        installPkg("org.ppsspp.ppsspp")
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val store = LauncherStore(activity).apply { load() }
        val game = Intent(Intent.ACTION_VIEW, Uri.parse("content://juegos/god_of_war.iso"))
            .setClassName("org.ppsspp.ppsspp", "org.ppsspp.ppsspp.PpssppActivity")
        @Suppress("DEPRECATION")
        val result = Intent().putExtra(Intent.EXTRA_SHORTCUT_INTENT, game).putExtra(Intent.EXTRA_SHORTCUT_NAME, "God of War")
        val e = Shortcuts.handleCreateResult(activity, store, result, "org.ppsspp.ppsspp", null)
        assertNotNull(e)

        val reloaded = LauncherStore(activity).apply { load() }
        val s = reloaded.apps.single()
        assertEquals("God of War", reloaded.label(s))
        val launch = reloaded.launchIntent(s)!!
        assertEquals(Intent.ACTION_VIEW, launch.action)
        assertEquals("content://juegos/god_of_war.iso", launch.dataString)
        assertEquals("org.ppsspp.ppsspp", launch.component?.packageName)
    }

    /** Juego desde archivo: guarda emulador + archivo y lo abre con ACTION_VIEW en ese emulador. */
    @Test
    fun fileShortcut() {
        installPkg("xyz.aethersx2.android")
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val store = LauncherStore(ctx).apply { load() }
        val open = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://docs/gt4.iso"), "*/*")
            .setClassName("xyz.aethersx2.android", "xyz.aethersx2.android.EmulationActivity")
        store.addShortcut(AppEntry.KIND_FILE, "xyz.aethersx2.android", "Gran Turismo 4", null,
            intentUri = open.toUri(Intent.URI_INTENT_SCHEME), category = store.categories.first().id)
        store.save()
        val s = LauncherStore(ctx).apply { load() }.apps.single()
        assertEquals(AppEntry.KIND_FILE, s.kind)
        val launch = LauncherStore(ctx).apply { load() }.launchIntent(s)!!
        assertEquals("content://docs/gt4.iso", launch.dataString)
        assertEquals("xyz.aethersx2.android", launch.component?.packageName)
        assert(launch.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    /** Monitor: cálculo de FPS a partir del contador de fotogramas y script para ADB. */
    @Test
    fun gameMonitor() {
        assertEquals(60, GameMonitorService.fps(1000, 0, 1060, 1000))
        assertEquals(30, GameMonitorService.fps(1000, 0, 1015, 500))
        assertEquals(null, GameMonitorService.fps(-1, 0, 1060, 1000))   // primera lectura
        assertEquals(null, GameMonitorService.fps(1000, 0, 900, 1000))  // contador reiniciado
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        GameMonitorService.writeHelper(ctx)
        val script = java.io.File(GameMonitorService.dir(ctx), "fps_helper.sh").readText()
        assert("service call SurfaceFlinger 1013" in script)
        assert("monitor_on" in script && "echo \$\$ >" in script && "{'" !in script)
        println(script)
    }
}
