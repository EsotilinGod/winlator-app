package com.example.rinlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** App añadida al launcher. [customLabel] y [cover] son opcionales (nombre y carátula propios). */
data class AppEntry(
    val pkg: String,
    val activity: String,
    var customLabel: String? = null,
    var cover: String? = null,
    val categories: MutableSet<String> = mutableSetOf(),
    var lastUsed: Long = 0L, // momento en que se abrió desde el launcher (0 = nunca)
    // Accesos directos: KIND_APP = app normal; el resto guarda su propio nombre, icono y forma de abrirse.
    val kind: String = KIND_APP,
    val baseLabel: String? = null,      // nombre original del acceso directo
    val intentUri: String? = null,      // KIND_INTENT y KIND_FILE: Intent serializado
    val shortcutId: String? = null,     // KIND_PINNED: id del ShortcutInfo de la app
    val iconFile: String? = null        // icono guardado en files/shortcut_icons/
) {
    val component get() = ComponentName(pkg, activity)
    val isShortcut get() = kind != KIND_APP

    companion object {
        const val KIND_APP = "app"
        const val KIND_INTENT = "intent"   // acceso directo clásico (EXTRA_SHORTCUT_INTENT)
        const val KIND_PINNED = "pinned"   // acceso directo moderno (requiere ser launcher predeterminado)
        const val KIND_FILE = "file"       // juego desde archivo: el emulador abre el archivo elegido
    }
}

data class Category(val id: String, var name: String)

/** App instalada que aparece en el cajón del sistema. */
data class InstalledApp(val pkg: String, val activity: String, val label: String)

/**
 * Persistencia sencilla en JSON (files/launcher.json). Las carátulas se copian a files/covers/,
 * así no dependen de permisos sobre la galería.
 */
class LauncherStore(private val context: Context) {
    val apps = mutableListOf<AppEntry>()
    val categories = mutableListOf<Category>()

    private val file = File(context.filesDir, "launcher.json")
    private val coverDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val pm: PackageManager = context.packageManager
    private val labelCache = HashMap<String, String>()

    fun load() {
        apps.clear(); categories.clear()
        if (!file.exists()) {
            categories += Category(newId(), "Juegos")
            categories += Category(newId(), "Emuladores")
            categories += Category(newId(), "Multimedia")
            save()
            return
        }
        try {
            val root = JSONObject(file.readText())
            val cats = root.optJSONArray("categories") ?: JSONArray()
            for (i in 0 until cats.length()) {
                val o = cats.getJSONObject(i)
                categories += Category(o.getString("id"), o.getString("name"))
            }
            val arr = root.optJSONArray("apps") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val set = mutableSetOf<String>()
                o.optJSONArray("categories")?.let { c -> for (j in 0 until c.length()) set += c.getString(j) }
                apps += AppEntry(
                    o.getString("pkg"), o.getString("activity"),
                    o.optString("customLabel").ifEmpty { null },
                    o.optString("cover").ifEmpty { null }, set,
                    o.optLong("lastUsed", 0L),
                    o.optString("kind").ifEmpty { AppEntry.KIND_APP },
                    o.optString("baseLabel").ifEmpty { null },
                    o.optString("intentUri").ifEmpty { null },
                    o.optString("shortcutId").ifEmpty { null },
                    o.optString("iconFile").ifEmpty { null }
                )
            }
        } catch (_: Exception) {
            // Archivo dañado: se empieza de cero sin cerrar la app.
        }
        // Quita apps desinstaladas.
        apps.removeAll { !isInstalled(it) }
    }

    fun save() {
        val root = JSONObject()
        root.put("categories", JSONArray().apply {
            categories.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        })
        root.put("apps", JSONArray().apply {
            apps.forEach { a ->
                put(JSONObject().put("pkg", a.pkg).put("activity", a.activity)
                    .put("customLabel", a.customLabel ?: "").put("cover", a.cover ?: "")
                    .put("categories", JSONArray(a.categories.toList()))
                    .put("lastUsed", a.lastUsed)
                    .put("kind", a.kind).put("baseLabel", a.baseLabel ?: "").put("intentUri", a.intentUri ?: "")
                    .put("shortcutId", a.shortcutId ?: "").put("iconFile", a.iconFile ?: ""))
            }
        })
        val tmp = File(file.parentFile, "launcher.json.tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(file)
    }

    fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

    // --- Apps del sistema ---

    fun installedApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { InstalledApp(it.activityInfo.packageName, it.activityInfo.name, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    private fun isInstalled(a: AppEntry): Boolean = if (a.isShortcut) try {
        pm.getPackageInfo(a.pkg, 0); true // el acceso directo vive mientras su app esté instalada
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } else try {
        pm.getActivityInfo(a.component, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun systemLabel(a: AppEntry): String = if (a.isShortcut) a.baseLabel ?: "Acceso directo" else labelCache.getOrPut(a.pkg + "/" + a.activity) {
        try { pm.getActivityInfo(a.component, 0).loadLabel(pm).toString() } catch (_: Exception) { a.pkg }
    }

    fun label(a: AppEntry): String = a.customLabel ?: systemLabel(a)

    fun icon(a: AppEntry): Drawable? {
        if (a.isShortcut) {
            a.iconFile?.let { File(shortcutIconDir, it) }?.takeIf { it.exists() }?.let { f ->
                BitmapFactory.decodeFile(f.path)?.let { return android.graphics.drawable.BitmapDrawable(context.resources, it) }
            }
            return try { pm.getApplicationIcon(a.pkg) } catch (_: Exception) { null }
        }
        return try { pm.getActivityIcon(a.component) } catch (_: Exception) { null }
    }

    private val shortcutIconDir = File(context.filesDir, "shortcut_icons").apply { mkdirs() }

    /** Guarda el icono de un acceso directo y devuelve el nombre del archivo. */
    fun saveShortcutIcon(icon: Drawable?): String? {
        icon ?: return null
        val size = 192
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size); icon.draw(android.graphics.Canvas(b))
        val name = "sc_${UUID.randomUUID()}.png"
        File(shortcutIconDir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        b.recycle()
        return name
    }

    /** Añade un acceso directo (al final, y a la categoría indicada si hay una). */
    fun addShortcut(kind: String, pkg: String, label: String, icon: Drawable?, intentUri: String? = null,
                    shortcutId: String? = null, category: String? = null): AppEntry {
        val e = AppEntry(pkg, "#sc:" + newId(), kind = kind, baseLabel = label, intentUri = intentUri,
            shortcutId = shortcutId, iconFile = saveShortcutIcon(icon))
        category?.let { e.categories += it }
        apps += e
        return e
    }

    fun launchIntent(a: AppEntry): Intent? {
        if (a.kind == AppEntry.KIND_INTENT || a.kind == AppEntry.KIND_FILE) {
            return try {
                Intent.parseUri(a.intentUri ?: return null, Intent.URI_INTENT_SCHEME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { null }
        }
        if (a.isShortcut) return null // KIND_PINNED se abre con LauncherApps
        val explicit = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(a.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return if (pm.resolveActivity(explicit, 0) != null) explicit else pm.getLaunchIntentForPackage(a.pkg)
    }

    // --- Carátulas ---

    /** Copia la imagen elegida, recortada al centro a 3:4 y reducida a 600 × 800. */
    fun importCover(a: AppEntry, uri: Uri): Boolean {
        val src = decode(uri) ?: return false
        val targetRatio = 3f / 4f
        var cw = src.width; var ch = src.height
        if (cw.toFloat() / ch > targetRatio) cw = (ch * targetRatio).toInt() else ch = (cw / targetRatio).toInt()
        val crop = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
        val scaled = Bitmap.createScaledBitmap(crop, 600, 800, true)
        val out = File(coverDir, "${a.pkg}_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        a.cover?.let { File(coverDir, it).delete() }
        a.cover = out.name
        if (scaled !== crop) scaled.recycle()
        if (crop !== src) crop.recycle()
        src.recycle()
        return true
    }

    fun removeCover(a: AppEntry) {
        a.cover?.let { File(coverDir, it).delete() }
        a.cover = null
    }

    fun coverFile(a: AppEntry): File? = a.cover?.let { File(coverDir, it) }?.takeIf { it.exists() }

    private fun decode(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
                val s = info.size
                val factor = maxOf(1, minOf(s.width / 600, s.height / 800))
                dec.setTargetSize(s.width / factor, s.height / factor)
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (_: Exception) {
        null
    }

    fun removeApp(a: AppEntry) {
        a.iconFile?.let { File(shortcutIconDir, it).delete() }
        removeCover(a)
        apps.remove(a)
    }
}
