package com.example.rinlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.widget.Toast

/**
 * Accesos directos de otras apps (p. ej. un juego concreto de un emulador).
 *
 * Android tiene dos sistemas:
 *  - Clásico (EXTRA_SHORTCUT_INTENT): la app devuelve un Intent; cualquier launcher puede abrirlo.
 *  - Moderno (ShortcutManager / PinItemRequest): solo el launcher PREDETERMINADO puede aceptarlo y
 *    abrirlo después (LauncherApps.startShortcut). Si Rin Launcher no lo es, se avisa al usuario.
 */
object Shortcuts {

    fun launcherApps(c: Context) = c.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    /** true si Rin Launcher es el launcher predeterminado (necesario para accesos directos modernos). */
    fun isDefaultLauncher(c: Context): Boolean = try { launcherApps(c).hasShortcutHostPermission() } catch (_: Exception) { false }

    /** Apps que ofrecen accesos directos en el selector de widgets (ACTION_CREATE_SHORTCUT). */
    fun providers(c: Context): List<ResolveInfo> =
        c.packageManager.queryIntentActivities(Intent(Intent.ACTION_CREATE_SHORTCUT), 0)
            .filter { it.activityInfo.exported && it.activityInfo.packageName != c.packageName }
            .sortedBy { it.loadLabel(c.packageManager).toString().lowercase() }

    /** Procesa lo que devuelve la app tras crear el acceso directo. Devuelve la entrada creada o null. */
    fun handleCreateResult(a: Activity, store: LauncherStore, data: Intent?, providerPkg: String, category: String?): AppEntry? {
        data ?: return null
        // Moderno: viene una solicitud de anclaje.
        val req = try { launcherApps(a).getPinItemRequest(data) } catch (_: Exception) { null }
        if (req != null) return acceptPin(a, store, req, category)
        // Clásico: Intent + nombre + icono.
        @Suppress("DEPRECATION")
        val target = data.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT) ?: return null
        @Suppress("DEPRECATION")
        val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Acceso directo"
        @Suppress("DEPRECATION")
        val icon: Drawable? = data.getParcelableExtra<Bitmap>(Intent.EXTRA_SHORTCUT_ICON)?.let { BitmapDrawable(a.resources, it) }
            ?: iconFromResource(a, data) ?: try { a.packageManager.getApplicationIcon(providerPkg) } catch (_: Exception) { null }
        val pkg = target.component?.packageName ?: target.`package` ?: providerPkg
        val e = store.addShortcut(AppEntry.KIND_INTENT, pkg, name, icon, intentUri = target.toUri(Intent.URI_INTENT_SCHEME), category = category)
        store.save()
        return e
    }

    @Suppress("DEPRECATION")
    private fun iconFromResource(c: Context, data: Intent): Drawable? = try {
        val res = data.getParcelableExtra<Intent.ShortcutIconResource>(Intent.EXTRA_SHORTCUT_ICON_RESOURCE) ?: return null
        val r = c.packageManager.getResourcesForApplication(res.packageName)
        r.getDrawable(r.getIdentifier(res.resourceName, null, null), null)
    } catch (_: Exception) { null }

    /** Acepta una solicitud de anclaje (solo funciona siendo el launcher predeterminado). */
    fun acceptPin(c: Context, store: LauncherStore, req: LauncherApps.PinItemRequest, category: String?): AppEntry? {
        if (req.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return null
        val info = req.shortcutInfo ?: return null
        val ok = try { req.isValid && req.accept() } catch (_: Exception) { false }
        if (!ok) {
            Toast.makeText(c, "Para este acceso directo, Rin Launcher debe ser el launcher predeterminado " +
                "(Ajustes → Usar como launcher predeterminado).", Toast.LENGTH_LONG).show()
            return null
        }
        val icon = try { launcherApps(c).getShortcutIconDrawable(info, c.resources.displayMetrics.densityDpi) } catch (_: Exception) { null }
        val label = (info.longLabel ?: info.shortLabel ?: "Acceso directo").toString()
        val e = store.addShortcut(AppEntry.KIND_PINNED, info.`package`, label, icon, shortcutId = info.id, category = category)
        store.save()
        return e
    }

    /** Abre un acceso directo moderno. */
    fun startPinned(c: Context, a: AppEntry): Boolean {
        val id = a.shortcutId ?: return false
        return try {
            launcherApps(c).startShortcut(a.pkg, id, null, null, Process.myUserHandle())
            true
        } catch (_: Exception) {
        Toast.makeText(c, if (isDefaultLauncher(c)) "No se pudo abrir el acceso directo (¿se borró en la app?)"
            else "Este acceso directo solo se abre si Rin Launcher es el launcher predeterminado.", Toast.LENGTH_LONG).show()
            false
        }
    }
}

/**
 * Recibe los "Añadir a la pantalla de inicio" que hacen otras apps (p. ej. el botón de un emulador para
 * crear el acceso directo de un juego). Android solo lo envía al launcher predeterminado.
 */
class ConfirmPinActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = try { Shortcuts.launcherApps(this).getPinItemRequest(intent) } catch (_: Exception) { null }
        if (req != null) {
            val store = LauncherStore(this).apply { load() }
            Shortcuts.acceptPin(this, store, req, null)?.let {
                Toast.makeText(this, "«${store.label(it)}» añadido a Rin Launcher", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
