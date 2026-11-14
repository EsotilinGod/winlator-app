package com.example.rinlauncher

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.hardware.input.InputManager
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.Choreographer
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity(), XmbView.Host {

    companion object {
        /** Las pruebas en la JVM lo desactivan: allí el vsync es inmediato y el bucle no cedería. */
        var animationsEnabled = true
        private const val KEY_RIN_SCALE = "rin_landscape_scale"
        const val KEY_VIEW = "view"
        const val VIEW_XMB = "xmb"
        const val VIEW_CAROUSEL = "carousel"
        private const val KEY_SOUNDS = "menu_sounds"
        private const val KEY_BOOT_SOUND = "boot_sound"
    }

    private lateinit var store: LauncherStore
    private lateinit var adapter: CarouselAdapter
    private lateinit var carousel: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var scene: RinSceneView
    private lateinit var xmb: XmbView
    private lateinit var sounds: MenuSounds
    /** Vista activa: estilo XMB (por defecto) o carrusel de tarjetas. */
    private val xmbMode get() = prefs.getString(KEY_VIEW, VIEW_XMB) == VIEW_XMB
    private lateinit var tabs: LinearLayout
    private lateinit var title: TextView
    private lateinit var hint: TextView
    private lateinit var empty: TextView
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var batteryIcon: BatteryView
    private lateinit var batteryText: TextView
    private lateinit var tempText: TextView

    private val dp by lazy { resources.displayMetrics.density }
    private var currentCategory: String? = null // null = "Todos"
    private var visibleApps: List<AppEntry> = emptyList()
    private var pendingCover: String? = null

    // --- Animación (fondo y Rin) a ~30 FPS, solo mientras la app está en primer plano ---
    private val startTime = SystemClock.uptimeMillis()
    private var animating = false
    private val frameCallback = object : Choreographer.FrameCallback {
        private var last = 0L
        override fun doFrame(frameTimeNanos: Long) {
            if (!animating) return
            if (frameTimeNanos - last >= 32_000_000L) {
                last = frameTimeNanos
                val t = ((SystemClock.uptimeMillis() - startTime) % 3_600_000L) / 1000f
                scene.setTime(t)
                if (xmbMode) xmb.setTime(t)
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // --- Accesos directos ---------------------------------------------------------------------
    private var pendingProvider: String? = null   // app que está creando el acceso directo
    private var pendingEmulator: String? = null   // emulador elegido para "juego desde archivo"

    private val createShortcut = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val provider = pendingProvider ?: return@registerForActivityResult
        pendingProvider = null
        if (r.resultCode != RESULT_OK) return@registerForActivityResult
        val e = Shortcuts.handleCreateResult(this, store, r.data, provider, currentCategory)
        if (e != null) {
            Toast.makeText(this, "«${store.label(e)}» añadido", Toast.LENGTH_SHORT).show()
            refresh(e)
        }
    }

    private val pickGameFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val emu = pendingEmulator ?: return@registerForActivityResult
        pendingEmulator = null
        uri ?: return@registerForActivityResult
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        val type = contentResolver.getType(uri) ?: "*/*"
        val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).setPackage(emu)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val target = packageManager.resolveActivity(open, 0)
            ?: packageManager.resolveActivity(Intent(open).setDataAndType(uri, "*/*"), 0)?.also { open.setDataAndType(uri, "*/*") }
        if (target == null) {
            Toast.makeText(this, "Ese emulador no acepta abrir archivos directamente. Prueba con «Desde la app» " +
                "(sus accesos directos) o con otro emulador.", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        open.setClassName(target.activityInfo.packageName, target.activityInfo.name)
        var name = "Juego"
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0).substringBeforeLast('.')
        }
        val icon = try { packageManager.getApplicationIcon(emu) } catch (_: Exception) { null }
        val e = store.addShortcut(AppEntry.KIND_FILE, emu, name, icon, intentUri = open.toUri(Intent.URI_INTENT_SCHEME), category = currentCategory)
        store.save()
        Toast.makeText(this, "«$name» añadido. Puedes ponerle carátula con Opciones.", Toast.LENGTH_SHORT).show()
        refresh(e)
    }

    /** Menú "Añadir acceso directo": juego desde archivo o accesos directos que ofrecen las apps. */
    private fun openShortcutMenu() {
        val providers = Shortcuts.providers(this)
        val labels = listOf("Juego desde archivo (elige emulador y juego)…") +
            providers.map { "Desde la app: " + it.loadLabel(packageManager) }
        AlertDialog.Builder(this, R.style.Dialog_Rin)
            .setTitle("Añadir acceso directo")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i == 0) chooseEmulator() else {
                    val p = providers[i - 1].activityInfo
                    pendingProvider = p.packageName
                    try {
                        createShortcut.launch(Intent(Intent.ACTION_CREATE_SHORTCUT).setClassName(p.packageName, p.name))
                    } catch (_: Exception) {
                        pendingProvider = null
                        Toast.makeText(this, "No se pudo abrir el creador de accesos directos de esa app", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun chooseEmulator() {
        val apps = store.installedApps()
        AlertDialog.Builder(this, R.style.Dialog_Rin)
            .setTitle("¿Con qué app se abre el juego?")
            .setItems(apps.map { it.label }.toTypedArray()) { _, i ->
                pendingEmulator = apps[i].pkg
                pickGameFile.launch(arrayOf("*/*"))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openHomeSettings() {
        try { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }

    private val pickCover = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = pendingCover ?: return@registerForActivityResult
        pendingCover = null
        val entry = store.apps.firstOrNull { AppPickerDialog.key(it.pkg, it.activity) == key } ?: return@registerForActivityResult
        if (uri != null) {
            if (store.importCover(entry, uri)) { store.save(); refresh() }
            else Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateClock()
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateBattery(i)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        goImmersive()

        scene = findViewById(R.id.scene)
        xmb = findViewById(R.id.xmb)
        xmb.host = this
        sounds = MenuSounds(this).apply { enabled = prefs.getBoolean(KEY_SOUNDS, true) }
        xmb.onSound = { sounds.play(it) }
        // Sonido de encendido: solo al arrancar el launcher (no al volver de un juego).
        if (savedInstanceState == null && animationsEnabled && prefs.getBoolean(KEY_BOOT_SOUND, true)) playBootSound()
        scene.rinScale = prefs.getFloat(KEY_RIN_SCALE, 1f)
        carousel = findViewById(R.id.carousel)
        tabs = findViewById(R.id.tabs)
        title = findViewById(R.id.selectedTitle)
        hint = findViewById(R.id.selectedHint)
        empty = findViewById(R.id.empty)
        timeText = findViewById(R.id.time)
        dateText = findViewById(R.id.date)
        batteryIcon = findViewById(R.id.batteryIcon)
        batteryText = findViewById(R.id.batteryText)
        tempText = findViewById(R.id.tempText)

        store = LauncherStore(this).apply { load() }

        adapter = CarouselAdapter(store, ::onCardClick, ::onCardLongClick)
        layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        carousel.layoutManager = layoutManager
        carousel.adapter = adapter
        carousel.itemAnimator = null
        // Las tarjetas se desvanecen en los bordes en vez de cortarse junto a Rin.
        carousel.isHorizontalFadingEdgeEnabled = true
        carousel.setFadingEdgeLength((60 * dp).toInt())
        LinearSnapHelper().attachToRecyclerView(carousel)
        carousel.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = updateCarouselEffects()
        })

        findViewById<ImageButton>(R.id.addButton).setOnClickListener { openPicker() }
        findViewById<ImageButton>(R.id.rinSizeButton).setOnClickListener { openRinSizeDialog() }
        findViewById<ImageButton>(R.id.viewButton).setOnClickListener { setViewMode(VIEW_XMB) }
        applyViewMode()

        // Medidas que dependen del tamaño real de pantalla.
        findViewById<View>(R.id.root).post { applyLayout() }
        buildTabs()
        refresh()
    }

    private fun applyLayout() {
        // Rin queda a la derecha (centro en el 72 % del ancho); el carrusel termina antes de su cara.
        val content = findViewById<LinearLayout>(R.id.content)
        val endFraction = 0.64f // la cara de Rin empieza en ~69 % del ancho
        val end = (content.width * (1f - endFraction)).toInt()
        content.setPadding(content.paddingLeft, content.paddingTop, end, content.paddingBottom)
        content.post {
            adapter.cardHeight = (carousel.height * 0.74f).toInt()
            adapter.cardWidth = (adapter.cardHeight * 0.75f).toInt()
            val margin = (10 * dp).toInt()
            val side = (carousel.width - adapter.cardWidth) / 2 - margin
            carousel.setPadding(side, 0, side, 0)
            refresh()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Carrusel

    private fun refresh(focusOn: AppEntry? = null) {
        if (currentCategory != null && store.categories.none { it.id == currentCategory }) currentCategory = null
        visibleApps = currentCategory?.let { id -> store.apps.filter { id in it.categories } } ?: store.apps.toList()
        adapter.items = visibleApps
        @Suppress("NotifyDataSetChanged") adapter.notifyDataSetChanged()
        empty.visibility = if (visibleApps.isEmpty()) View.VISIBLE else View.GONE
        hint.visibility = if (visibleApps.isEmpty()) View.INVISIBLE else View.VISIBLE
        val target = focusOn?.let { visibleApps.indexOf(it) }?.takeIf { it >= 0 }
            ?: adapter.focused.coerceIn(0, maxOf(0, visibleApps.size - 1))
        carousel.post {
            if (visibleApps.isNotEmpty()) layoutManager.scrollToPositionWithOffset(target, 0)
            carousel.post { updateCarouselEffects() }
        }
        if (visibleApps.isEmpty()) { title.text = ""; adapter.focused = -1 }
        if (::xmb.isInitialized) xmb.reload()
    }

    /** Escala y opacidad según la distancia al centro; la tarjeta central queda seleccionada. */
    private fun updateCarouselEffects() {
        if (adapter.cardWidth == 0) return
        val rvCenter = carousel.width / 2f
        var best = -1; var bestDist = Float.MAX_VALUE
        val step = adapter.cardWidth + 20 * dp
        for (i in 0 until carousel.childCount) {
            val child = carousel.getChildAt(i)
            val c = (child.left + child.right) / 2f
            val d = abs(c - rvCenter) / step
            val s = 1.1f - 0.2f * d.coerceAtMost(1.2f)
            child.scaleX = s; child.scaleY = s
            child.alpha = 1f - 0.35f * (d / 2f).coerceAtMost(1f)
            child.translationZ = if (d < 0.5f) 8 * dp else 0f
            val pos = carousel.getChildAdapterPosition(child)
            if (pos != RecyclerView.NO_POSITION && d < bestDist) { bestDist = d; best = pos }
        }
        if (best >= 0 && best != adapter.focused) adapter.focused = best
        if (best in visibleApps.indices) title.text = store.label(visibleApps[best])
    }

    private fun onCardClick(position: Int) {
        if (position != adapter.focused) {
            layoutManager.findViewByPosition(position)?.let { v ->
                carousel.smoothScrollBy(((v.left + v.right) / 2f - carousel.width / 2f).toInt(), 0)
            }
            return
        }
        sounds.play(XmbView.SOUND_OK)
        launch(visibleApps[position])
    }

    private fun launch(a: AppEntry) {
        if (a.kind == AppEntry.KIND_PINNED) {
            if (Shortcuts.startPinned(this, a)) { a.lastUsed = System.currentTimeMillis(); store.save() }
            return
        }
        val intent = store.launchIntent(a)
        if (intent == null) {
            Toast.makeText(this, "No se puede abrir ${store.label(a)}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(intent)
            a.lastUsed = System.currentTimeMillis(); store.save()
        } catch (_: Exception) {
            Toast.makeText(this, "No se puede abrir ${store.label(a)}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onCardLongClick(position: Int) {
        val a = visibleApps.getOrNull(position) ?: return
        val actions = mutableListOf<Pair<String, () -> Unit>>(
            "Abrir" to { launch(a) },
            "Cambiar nombre" to { renameApp(a) },
            "Elegir carátula de la galería" to {
                pendingCover = AppPickerDialog.key(a.pkg, a.activity)
                pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        )
        if (a.cover != null) actions += "Quitar carátula" to { store.removeCover(a); store.save(); refresh(a) }
        actions += "Categorías…" to { chooseCategories(a) }
        if (position > 0) actions += "Mover a la izquierda" to { move(a, -1) }
        if (position < visibleApps.size - 1) actions += "Mover a la derecha" to { move(a, +1) }
        actions += "Quitar del launcher" to {
            AlertDialog.Builder(this, R.style.Dialog_Rin)
                .setMessage("¿Quitar ${store.label(a)} del launcher? (No se desinstala)")
                .setPositiveButton("Quitar") { _, _ -> store.removeApp(a); store.save(); refresh() }
                .setNegativeButton("Cancelar", null).show()
        }
        AlertDialog.Builder(this, R.style.Dialog_Rin)
            .setTitle(store.label(a))
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .show()
    }

    private fun renameApp(a: AppEntry) {
        val input = EditText(this).apply { setText(store.label(a)); setSelectAllOnFocus(true); setSingleLine() }
        AlertDialog.Builder(this, R.style.Dialog_Rin)
            .setTitle("Nombre visible")
            .setView(padded(input))
            .setPositiveButton("Guardar") { _, _ ->
                val t = input.text.toString().trim()
                a.customLabel = if (t.isEmpty() || t == store.systemLabel(a)) null else t
                store.save(); refresh(a)
            }
            .setNeutralButton("Original") { _, _ -> a.customLabel = null; store.save(); refresh(a) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun chooseCategories(a: AppEntry) {
        if (store.categories.isEmpty()) {
            Toast.makeText(this, "Crea primero una categoría con ＋ en las pestañas", Toast.LENGTH_SHORT).show()
            return
        }
        val names = store.categories.map { it.name }.toTypedArray()
        val checked = store.categories.map { it.id in a.categories }.toBooleanArray()
        AlertDialog.Builder(this, R.style.Dialog_Rin)
            .setTitle("Categorías de ${store.label(a)}")
            .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Guardar") { _, _ ->
                a.categories.clear()
                store.categories.forEachIndexed { i, c -> if (checked[i]) a.categories += c.id }
                store.save(); refresh(a)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Mueve la app una posición respecto a su vecina visible (respeta el filtro de categoría). */
    private fun move(a: AppEntry, dir: Int) {
        val i = visibleApps.indexOf(a)
        val neighbor = visibleApps.getOrNull(i + dir) ?: return
        val ia = store.apps.indexOf(a); val ib = store.apps.indexOf(neighbor)
        store.apps[ia] = neighbor; store.apps[ib] = a
        store.save(); refresh(a)
    }

    private fun openPicker() {
        AppPickerDialog.show(this, store, onShortcut = { openShortcutMenu() }) { all, selected ->
            // Quita las desmarcadas (los accesos directos no aparecen en esta lista: se conservan).
            store.apps.filter { !it.isShortcut && AppPickerDialog.key(it.pkg, it.activity) !in selected }.forEach { store.removeApp(it) }
            // Añade las nuevas al final (y a la categoría abierta, si hay una).
            val present = store.apps.map { AppPickerDialog.key(it.pkg, it.activity) }.toSet()
            var last: AppEntry? = null
            for (app in all) {
                val k = AppPickerDialog.key(app.pkg, app.activity)
                if (k in selected && k !in present) {
                    val e = AppEntry(app.pkg, app.activity)
                    currentCategory?.let { e.categories += it }
                    store.apps += e; last = e
                }
            }
            store.save(); refresh(last)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Pestañas de categorías

    private fun buildTabs() {
        tabs.removeAllViews()
        tabs.addView(chip("Todos", currentCategory == null, onClick = { selectCategory(null) }))
        for (c in store.categories) {
            tabs.addView(chip(c.name, currentCategory == c.id,
                onClick = { selectCategory(c.id) }, onLongClick = { editCategory(c) }))
        }
        tabs.addView(chip("＋  Categoría", false, onClick = { addCategory() }, ghost = true))
    }

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit,
                     onLongClick: (() -> Unit)? = null, ghost: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (7 * dp).toInt(), (16 * dp).toInt(), (7 * dp).toInt())
            setTextColor(if (selected) Color.WHITE else Color.rgb(43, 47, 58))
            if (selected) setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                cornerRadius = 50 * dp
                if (selected) setColor(Color.rgb(242, 181, 48)) else setColor(Color.argb(if (ghost) 120 else 215, 255, 255, 255))
                setStroke((1 * dp).toInt(), Color.argb(if (selected) 0 else 140, 242, 181, 48))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = (8 * dp).toInt() }
            setOnClickListener { onClick() }
            onLongClick?.let { l -> setOnLongClickListener { l(); true } }
        }

    private fun selectCategory(id: String?) {
        currentCategory = id
        adapter.focused = -1
        buildTabs(); refresh()
    }

    private fun addCategory() {
        val input = EditText(this).apply { hint = "Nombre"; setSingleLine() }
        AlertDialog.Builder(this, R.style.Dialog_Rin)
            .setTitle("Nueva categoría")
            .setView(padded(input))
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val c = Category(store.newId(), name)
                    store.categories += c; store.save()
                    selectCategory(c.id)
                    if (xmbMode) xmb.selectCategory("cat:" + c.id)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun editCategory(c: Category) {
        AlertDialog.Builder(this, R.style.Dialog_Rin)
            .setTitle(c.name)
            .setItems(arrayOf("Renombrar", "Mover a la izquierda", "Eliminar categoría")) { _, which ->
                when (which) {
                    0 -> {
                        val input = EditText(this).apply { setText(c.name); setSelectAllOnFocus(true); setSingleLine() }
                        AlertDialog.Builder(this, R.style.Dialog_Rin).setTitle("Renombrar categoría").setView(padded(input))
                            .setPositiveButton("Guardar") { _, _ ->
                                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let { c.name = it; store.save(); buildTabs() }
                            }.setNegativeButton("Cancelar", null).show()
                    }
                    1 -> {
                        val i = store.categories.indexOf(c)
                        if (i > 0) { store.categories.removeAt(i); store.categories.add(i - 1, c); store.save(); buildTabs() }
                    }
                    2 -> AlertDialog.Builder(this, R.style.Dialog_Rin)
                        .setMessage("¿Eliminar la categoría «${c.name}»? Las apps seguirán en «Todos».")
                        .setPositiveButton("Eliminar") { _, _ ->
                            store.categories.remove(c)
                            store.apps.forEach { it.categories.remove(c.id) }
                            store.save()
                            if (currentCategory == c.id) currentCategory = null
                            buildTabs(); refresh()
                        }.setNegativeButton("Cancelar", null).show()
                }
            }
            .show()
    }

    // ---------------------------------------------------------------------------------------
    // Tamaño de Rin

    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    /** Aplica el tamaño de Rin a la escena (vista previa en vivo). */
    private fun sendRinScale(scale: Float) {
        scene.rinScale = scale
    }

    private fun openRinSizeDialog() {
        val min = 45; val max = 160 // %
        var value = (prefs.getFloat(KEY_RIN_SCALE, 1f) * 100).toInt().coerceIn(min, max)
        val label = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(43, 47, 58))
            gravity = Gravity.CENTER
        }
        fun show() { label.text = "$value %" + if (value == 100) "  (original)" else "" }
        show()
        val seek = android.widget.SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    value = p + min; show(); sendRinScale(value / 100f) // vista previa en vivo
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
            addView(label)
            addView(seek, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (8 * dp).toInt() })
            addView(TextView(this@MainActivity).apply {
                text = "45 % muestra a Rin entera; 100 % es el tamaño original del dibujo."
                textSize = 12f
                setTextColor(Color.rgb(107, 112, 128))
                setPadding(0, (6 * dp).toInt(), 0, 0)
            })
        }
        val saved = prefs.getFloat(KEY_RIN_SCALE, 1f)
        AlertDialog.Builder(this, R.style.Dialog_Rin)
            .setTitle("Tamaño de Rin")
            .setView(box)
            .setPositiveButton("Listo") { _, _ -> prefs.edit().putFloat(KEY_RIN_SCALE, value / 100f).apply() }
            .setNeutralButton("Restablecer") { _, _ ->
                prefs.edit().putFloat(KEY_RIN_SCALE, 1f).apply(); sendRinScale(1f)
            }
            .setNegativeButton("Cancelar") { _, _ -> sendRinScale(saved) }
            .setOnCancelListener { sendRinScale(saved) }
            .show()
    }

    private fun padded(v: View) = FrameLayout(this).apply {
        setPadding((22 * dp).toInt(), (8 * dp).toInt(), (22 * dp).toInt(), 0)
        addView(v)
    }

    // ---------------------------------------------------------------------------------------
    // Barra de estado propia

    private fun updateClock() {
        val now = Date()
        val pattern = if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
        timeText.text = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
        dateText.text = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es")).format(now)
        xmb.clockText = timeText.text.toString()
        xmb.dateText = SimpleDateFormat("EEE d/MM", Locale("es")).format(now)
        xmb.invalidate()
    }

    private fun updateBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        batteryIcon.level = pct
        batteryIcon.charging = charging
        xmb.batteryPct = pct; xmb.charging = charging; xmb.invalidate()
        batteryText.text = if (charging) "$pct %  ·  cargando" else "$pct %"
        tempText.text = String.format(Locale("es"), "%.1f °C", temp)
        // Aviso de temperatura: útil durante sesiones largas de juego.
        tempText.setTextColor(when {
            temp >= 43f -> Color.rgb(214, 56, 46)
            temp >= 39f -> Color.rgb(221, 128, 20)
            else -> Color.rgb(43, 47, 58)
        })
    }

    // ---------------------------------------------------------------------------------------
    // Ciclo de vida

    override fun onResume() {
        super.onResume()
        goImmersive()
        // Las apps desinstaladas desaparecen solas.
        val before = store.apps.size
        store.load()
        if (store.apps.size != before) { store.save() }
        buildTabs(); refresh()
        updateClock()
        val flags = ContextCompat.RECEIVER_NOT_EXPORTED // difusiones del sistema: se reciben igual
        ContextCompat.registerReceiver(this, clockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, flags)
        ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), flags)
            ?.let { updateBattery(it) }
        if (animationsEnabled) {
            animating = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
        inputManager.registerInputDeviceListener(gamepadListener, null)
        updateHint()
    }

    override fun onPause() {
        super.onPause()
        stopBootSound()
        animating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        unregisterReceiver(clockReceiver)
        unregisterReceiver(batteryReceiver)
        inputManager.unregisterInputDeviceListener(gamepadListener)
    }

    // ---------------------------------------------------------------------------------------
    // Vista XMB

    private fun setViewMode(mode: String) {
        prefs.edit().putString(KEY_VIEW, mode).apply()
        applyViewMode()
    }

    private fun applyViewMode() {
        val x = xmbMode
        findViewById<View>(R.id.content).visibility = if (x) View.GONE else View.VISIBLE
        xmb.visibility = if (x) View.VISIBLE else View.GONE
        if (x) { xmb.reload(); xmb.requestFocus() }
    }

    private fun iconFor(name: String): XmbView.Icon {
        val n = name.lowercase(Locale("es"))
        return when {
            "jueg" in n || "game" in n -> XmbView.Icon.GAMEPAD
            "emul" in n || "retro" in n || "consola" in n -> XmbView.Icon.CHIP
            "músic" in n || "music" in n || "audio" in n || "radio" in n -> XmbView.Icon.MUSIC
            "foto" in n || "imag" in n || "cámara" in n || "camara" in n || "galer" in n -> XmbView.Icon.PHOTO
            "internet" in n || "web" in n || "naveg" in n || "red" == n -> XmbView.Icon.GLOBE
            "multi" in n || "vídeo" in n || "video" in n || "pelí" in n || "peli" in n || "serie" in n || "tv" == n -> XmbView.Icon.MEDIA
            "fav" in n -> XmbView.Icon.STAR
            "social" in n || "chat" in n || "mensaj" in n || "amig" in n -> XmbView.Icon.CHAT
            "ejerc" in n || "gym" in n || "fitness" in n || "deport" in n || "salud" in n -> XmbView.Icon.FITNESS
            "podcast" in n || "noticia" in n -> XmbView.Icon.RADIO
            "comida" in n || "cocina" in n || "receta" in n || "delivery" in n -> XmbView.Icon.FOOD
            "café" in n || "cafe" in n || "relax" in n -> XmbView.Icon.COFFEE
            "herram" in n || "útil" in n || "util" in n -> XmbView.Icon.WRENCH
            else -> XmbView.Icon.FOLDER
        }
    }

    override fun xmbCategories(): List<XmbView.Category> =
        listOf(XmbView.Category("settings", "Ajustes", XmbView.Icon.SETTINGS),
            XmbView.Category("all", "Todos", XmbView.Icon.ALL)) +
            store.categories.map { XmbView.Category("cat:" + it.id, it.name, iconFor(it.name)) }

    override fun xmbItems(category: XmbView.Category): List<XmbView.Item> {
        if (category.key == "settings") {
            val pct = (prefs.getFloat(KEY_RIN_SCALE, 1f) * 100).toInt()
            return listOf(
                XmbView.Item("apps", "Añadir o quitar apps", "Elige qué apps aparecen en el launcher", glyph = XmbView.Icon.PACKAGE),
                XmbView.Item("shortcut", "Añadir acceso directo", "Un juego de un emulador, u otro acceso directo", glyph = XmbView.Icon.GAMEPAD),
                XmbView.Item("newcat", "Nueva categoría", "Agrupa tus apps como quieras", glyph = XmbView.Icon.FOLDER_PLUS),
                XmbView.Item("editcats", "Editar categorías", "Renombrar, ordenar o eliminar", glyph = XmbView.Icon.WRENCH),
                XmbView.Item("rinsize", "Tamaño de Rin", "Actual: $pct %", glyph = XmbView.Icon.FACE),
                if (sounds.enabled) XmbView.Item("sounds", "Sonidos del menú", "Activados · pulsa para silenciar", glyph = XmbView.Icon.SOUND)
                else XmbView.Item("sounds", "Sonidos del menú", "Desactivados · pulsa para activar", glyph = XmbView.Icon.MUTE),
                if (prefs.getBoolean(KEY_BOOT_SOUND, true)) XmbView.Item("bootsound", "Sonido de inicio", "Activado · pulsa para desactivar", glyph = XmbView.Icon.SOUND)
                else XmbView.Item("bootsound", "Sonido de inicio", "Desactivado · pulsa para activar", glyph = XmbView.Icon.MUTE),
                XmbView.Item("carousel", "Cambiar a vista carrusel", "Tarjetas grandes con carátulas", glyph = XmbView.Icon.DISPLAY),
                XmbView.Item("home", "Usar como launcher predeterminado",
                    if (Shortcuts.isDefaultLauncher(this)) "Activo · accesos directos de emuladores habilitados"
                    else "Opcional · necesario para «Añadir a inicio» de los emuladores", glyph = XmbView.Icon.ALL)
            )
        }
        val id = category.key.removePrefix("cat:")
        val list = if (category.key == "all") store.apps else store.apps.filter { id in it.categories }
        if (list.isEmpty()) return listOf(XmbView.Item("apps", "Sin apps aquí", "Pulsa para añadir apps", glyph = XmbView.Icon.PLUS))
        return list.map { a -> XmbView.Item(a.pkg + "/" + a.activity, store.label(a), lastUsedText(a), xmbIcon(a), payload = a) }
    }

    override fun xmbActivate(category: XmbView.Category, item: XmbView.Item) {
        (item.payload as? AppEntry)?.let { launch(it); return }
        when (item.key) {
            "apps" -> openPicker()
            "shortcut" -> openShortcutMenu()
            "bootsound" -> {
                val on = !prefs.getBoolean(KEY_BOOT_SOUND, true)
                prefs.edit().putBoolean(KEY_BOOT_SOUND, on).apply()
                if (on) { stopBootSound(); playBootSound() } else stopBootSound()
                xmb.reload()
            }
            "home" -> openHomeSettings()
            "newcat" -> addCategory()
            "editcats" -> chooseCategoryToEdit()
            "rinsize" -> openRinSizeDialog()
            "carousel" -> setViewMode(VIEW_CAROUSEL)
            "sounds" -> {
                sounds.enabled = !sounds.enabled
                prefs.edit().putBoolean(KEY_SOUNDS, sounds.enabled).apply()
                if (sounds.enabled) sounds.play(XmbView.SOUND_OK)
                xmb.reload()
            }
        }
    }

    override fun xmbOptions(category: XmbView.Category, item: XmbView.Item) {
        val a = item.payload as? AppEntry ?: return
        val i = visibleApps.indexOf(a)
        if (i >= 0) onCardLongClick(i)
    }

    override fun xmbCategoryChanged(category: XmbView.Category) {
        // Mantiene el filtro de la app sincronizado (opciones, "mover" y altas usan esa categoría).
        val newCat = when {
            category.key == "all" -> null
            category.key.startsWith("cat:") -> category.key.removePrefix("cat:")
            else -> return
        }
        if (newCat != currentCategory) {
            currentCategory = newCat
            visibleApps = currentCategory?.let { id -> store.apps.filter { id in it.categories } } ?: store.apps.toList()
        }
    }

    private fun chooseCategoryToEdit() {
        if (store.categories.isEmpty()) {
            Toast.makeText(this, "Aún no hay categorías", Toast.LENGTH_SHORT).show(); return
        }
        AlertDialog.Builder(this, R.style.Dialog_Rin)
            .setTitle("Editar categorías")
            .setItems(store.categories.map { it.name }.toTypedArray()) { _, i -> editCategory(store.categories[i]) }
            .show()
    }

    private fun lastUsedText(a: AppEntry): String {
        if (a.lastUsed <= 0L) return "Aún no la abres desde aquí"
        val cal = java.util.Calendar.getInstance()
        fun dayIndex(t: Long): Long { cal.timeInMillis = t; return (t + cal.get(java.util.Calendar.ZONE_OFFSET) + cal.get(java.util.Calendar.DST_OFFSET)) / 86_400_000L }
        val days = dayIndex(System.currentTimeMillis()) - dayIndex(a.lastUsed)
        val hour = SimpleDateFormat(if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(a.lastUsed))
        return when {
            days <= 0L -> "Último uso: hoy, $hour"
            days == 1L -> "Último uso: ayer, $hour"
            else -> "Último uso: hace $days días"
        }
    }

    private val xmbIcons = HashMap<String, android.graphics.Bitmap>()

    /** Icono cuadrado para el XMB: la carátula (recorte central) o el icono de la app. */
    private fun xmbIcon(a: AppEntry): android.graphics.Bitmap? {
        val key = a.pkg + "/" + a.activity + "|" + (a.cover ?: "")
        return xmbIcons.getOrPut(key) {
            val size = 192
            val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            val cover = store.coverFile(a)?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
            if (cover != null) {
                val side = minOf(cover.width, cover.height)
                val src = android.graphics.Rect((cover.width - side) / 2, (cover.height - side) / 2,
                    (cover.width + side) / 2, (cover.height + side) / 2)
                c.drawBitmap(cover, src, android.graphics.Rect(0, 0, size, size), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                cover.recycle()
            } else {
                c.drawColor(Color.argb(235, 245, 246, 250))
                store.icon(a)?.let { d -> d.setBounds(14, 14, size - 14, size - 14); d.draw(c) }
            }
            out
        }
    }

    // ---------------------------------------------------------------------------------------
    // Mando (Bluetooth/USB)
    //
    // Android convierte la cruceta y el stick izquierdo en teclas DPAD (con repetición al mantener),
    // y en los diálogos traduce A -> aceptar y B -> atrás. Aquí se manejan los botones en la pantalla
    // principal; los diálogos reciben sus propias teclas.

    private val inputManager by lazy { getSystemService(INPUT_SERVICE) as InputManager }
    private val gamepadListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) = updateHint()
        override fun onInputDeviceRemoved(id: Int) = updateHint()
        override fun onInputDeviceChanged(id: Int) = updateHint()
    }
    private var navIndex = -1 // destino de la navegación en curso (pulsaciones rápidas)

    private fun gamepadConnected(): Boolean = InputDevice.getDeviceIds().any { id ->
        val src = InputDevice.getDevice(id)?.sources ?: 0
        (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    private fun updateHint() {
        xmb.gamepadHints = gamepadConnected(); xmb.invalidate()
        hint.text = if (gamepadConnected())
            "Ⓐ Abrir   Ⓧ Editar   Ⓨ Añadir apps   L1/R1 Categorías   SELECT Tamaño de Rin"
        else "Toca para abrir · Mantén pulsado para editar"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT -> true
            else -> false
        }
        if (!handled) return super.dispatchKeyEvent(event)
        // B en la pantalla principal no hace nada (no salir del launcher sin querer).
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val repeat = event.repeatCount > 0
        if (xmbMode) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START -> if (!repeat) openPicker()
                KeyEvent.KEYCODE_BUTTON_SELECT -> if (!repeat) openRinSizeDialog()
                else -> xmb.handleKey(event.keyCode, repeat)
            }
            if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) updateHint()
            return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> moveFocus(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveFocus(+1)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> Unit
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                if (!repeat) visibleApps.getOrNull(adapter.focused)?.let { launch(it) }
            KeyEvent.KEYCODE_BUTTON_X -> if (!repeat && adapter.focused in visibleApps.indices) onCardLongClick(adapter.focused)
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START -> if (!repeat) openPicker()
            KeyEvent.KEYCODE_BUTTON_L1 -> if (!repeat) cycleCategory(-1)
            KeyEvent.KEYCODE_BUTTON_R1 -> if (!repeat) cycleCategory(+1)
            KeyEvent.KEYCODE_BUTTON_SELECT -> if (!repeat) openRinSizeDialog()
        }
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) updateHint()
        return true
    }

    /** Mueve la selección del carrusel y centra la tarjeta. */
    private fun moveFocus(dir: Int) {
        if (visibleApps.isEmpty()) return
        val from = if (navIndex in visibleApps.indices && carousel.scrollState != RecyclerView.SCROLL_STATE_IDLE)
            navIndex else adapter.focused.coerceAtLeast(0)
        val target = (from + dir).coerceIn(0, visibleApps.size - 1)
        if (target != from) sounds.play(XmbView.SOUND_NAV)
        navIndex = target
        val v = layoutManager.findViewByPosition(target)
        if (v != null) {
            carousel.smoothScrollBy(((v.left + v.right) / 2f - carousel.width / 2f).toInt(), 0)
        } else {
            layoutManager.scrollToPositionWithOffset(target, 0)
            carousel.post { updateCarouselEffects() }
        }
    }

    /** L1/R1: "Todos" y luego cada categoría, en ciclo. */
    private fun cycleCategory(dir: Int) {
        sounds.play(XmbView.SOUND_CAT)
        val ids = listOf<String?>(null) + store.categories.map { it.id }
        val i = ids.indexOf(currentCategory).coerceAtLeast(0)
        selectCategory(ids[(i + dir + ids.size) % ids.size])
    }

    override fun onDestroy() {
        super.onDestroy()
        sounds.release()
        stopBootSound()
    }

    private var bootPlayer: android.media.MediaPlayer? = null

    private fun playBootSound() {
        bootPlayer = try {
            android.media.MediaPlayer.create(this, R.raw.xmb_boot)?.apply {
                setOnCompletionListener { stopBootSound() }
                start()
            }
        } catch (_: Exception) { null }
    }

    private fun stopBootSound() {
        bootPlayer?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        bootPlayer = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
