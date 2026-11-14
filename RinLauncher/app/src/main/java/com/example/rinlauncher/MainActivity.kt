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
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.Choreographer
import android.view.Gravity
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

class MainActivity : ComponentActivity() {

    companion object {
        /** Las pruebas en la JVM lo desactivan: allí el vsync es inmediato y el bucle no cedería. */
        var animationsEnabled = true
    }

    private lateinit var store: LauncherStore
    private lateinit var adapter: CarouselAdapter
    private lateinit var carousel: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var background: CircuitBackgroundView
    private lateinit var rinPanel: RinPanelView
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
    private var pendingCover: String? = null // clave pkg/activity de la app que recibe la carátula

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
                background.setTime(t)
                rinPanel.setTime(t)
            }
            Choreographer.getInstance().postFrameCallback(this)
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

        background = findViewById(R.id.background)
        rinPanel = findViewById(R.id.rinPanel)
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

        // Medidas que dependen del tamaño real de pantalla.
        findViewById<View>(R.id.root).post { applyLayout() }
        buildTabs()
        refresh()
    }

    private fun applyLayout() {
        // El contenido termina donde empieza el pañuelo de Rin.
        val content = findViewById<LinearLayout>(R.id.content)
        content.setPadding(content.paddingLeft, content.paddingTop,
            rinPanel.panelWidth - rinPanel.clearZone + (8 * dp).toInt(), content.paddingBottom)
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
        launch(visibleApps[position])
    }

    private fun launch(a: AppEntry) {
        val intent = store.launchIntent(a)
        if (intent == null) {
            Toast.makeText(this, "No se puede abrir ${store.label(a)}", Toast.LENGTH_SHORT).show()
            return
        }
        try { startActivity(intent) } catch (_: Exception) {
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
        AppPickerDialog.show(this, store) { all, selected ->
            // Quita las desmarcadas.
            store.apps.filter { AppPickerDialog.key(it.pkg, it.activity) !in selected }.forEach { store.removeApp(it) }
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
    }

    override fun onPause() {
        super.onPause()
        animating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        unregisterReceiver(clockReceiver)
        unregisterReceiver(batteryReceiver)
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
