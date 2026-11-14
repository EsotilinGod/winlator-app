package com.example.rinlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.PathParser
import kotlin.math.abs
import kotlin.math.sin

/**
 * Vista estilo XMB (barra de categorías horizontal + lista vertical de elementos), dibujada sobre la
 * escena de Rin. Todo se pinta en un único Canvas: velo de legibilidad, onda de luz animada,
 * categorías, elementos, reloj/batería y guía de botones. Las posiciones se interpolan cada fotograma
 * para que los cambios de categoría y de elemento se deslicen suavemente.
 *
 * Geometría en "unidades de diseño" (u = alto / 610), igual que el bosquejo aprobado.
 */
class XmbView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    // --- Modelo ---------------------------------------------------------------------------------

    /** Pack de iconos propio (glifos sólidos estilo consola, 48 × 48, relleno par-impar). */
    enum class Icon {
        SETTINGS, ALL, GAMEPAD, CHIP, MEDIA, MUSIC, PHOTO, STAR, FOLDER, GLOBE, PLUS, FOLDER_PLUS, FOLDER_EDIT,
        RESIZE, LAYOUT, SOUND, MUTE, CHAT, FITNESS, RADIO, FOOD, COFFEE, PACKAGE, WRENCH, FACE, DISPLAY
    }

    companion object Sounds {
        const val SOUND_NAV = 0   // moverse entre elementos
        const val SOUND_CAT = 1   // cambiar de categoría
        const val SOUND_OK = 2    // abrir / confirmar
        const val SOUND_OPT = 3   // abrir opciones

        private const val CAT_X = 300f      // centro horizontal de la columna seleccionada
        private const val CAT_Y = 141f      // centro vertical de la fila de categorías
        private const val CAT_GAP = 112f
        private const val SEL_TOP = 228f    // borde superior del elemento seleccionado
    }

    /** Se llama al navegar para reproducir los sonidos del menú. */
    var onSound: ((Int) -> Unit)? = null

    class Category(val key: String, val name: String, val icon: Icon)

    class Item(
        val key: String,
        val title: String,
        val subtitle: String,
        val bitmap: Bitmap? = null,       // icono o carátula ya preparado (cuadrado)
        val glyph: Icon? = null,          // icono de línea (elementos de Ajustes)
        val payload: Any? = null
    )

    interface Host {
        fun xmbCategories(): List<Category>
        fun xmbItems(category: Category): List<Item>
        fun xmbActivate(category: Category, item: Item)
        fun xmbOptions(category: Category, item: Item)
        fun xmbCategoryChanged(category: Category)
    }

    var host: Host? = null

    private var cats: List<Category> = emptyList()
    private val items = HashMap<String, List<Item>>()
    private val itemSel = HashMap<String, Int>()     // elemento elegido por categoría
    private var catSel = 0
    private var catPos = 0f                           // posición animada (índice fraccionario)
    private var itemPos = 0f
    private var lastCatKey: String? = null

    var clockText = ""
    var dateText = ""
    var batteryPct = 0
    var charging = false
    var gamepadHints = false

    private var time = 0f

    // --- Pintura --------------------------------------------------------------------------------

    private var u = 1f
    private val veil = Paint()
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()
    private val m = Matrix()
    private val iconPaths = HashMap<Icon, Path>()
    // Iconos PNG diseñados por el usuario (res/drawable-nodpi/xmb_icon_*.png) y su brillo precalculado.
    private val iconBitmaps = HashMap<Icon, Bitmap>()
    private val iconGlows = HashMap<Icon, Bitmap>()
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Color.rgb(255, 214, 120) }
    private val dst = RectF()
    private val shaders = HashMap<Bitmap, BitmapShader>()   // se crean una vez por imagen
    private val boxShader = LinearGradient(0f, 0f, 1f, 1f, Color.rgb(70, 80, 120), Color.rgb(120, 130, 170), Shader.TileMode.CLAMP)

    init {
        isFocusable = true
        val defs = mapOf(
            Icon.SETTINGS to "M18 7h12a3 3 0 0 1 3 3v5h-4v-4H19v4h-4v-5a3 3 0 0 1 3-3zM7 16h34a4 4 0 0 1 4 4v8H28v-2h-8v2H3v-8a4 4 0 0 1 4-4zM3 31h17v3h8v-3h17v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4z",
            Icon.ALL to "M7 6h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2zM29 6h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H29a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2zM7 26h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V28a2 2 0 0 1 2-2zM29 26h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H29a2 2 0 0 1-2-2V28a2 2 0 0 1 2-2z",
            Icon.GAMEPAD to "M14 12h20c6.5 0 9.5 5 10.6 12.5l1 7.5c.6 4.6-4.6 7.2-7.6 4l-5.4-5.6H15.4L10 36c-3 3.2-8.2.6-7.6-4l1-7.5C4.5 17 7.5 12 14 12zM12.5 18v5h-5v4h5v5h4v-5h5v-4h-5v-5zM33 18.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 1 0 0-5zM38.5 24a2.5 2.5 0 1 0 0 5 2.5 2.5 0 1 0 0-5zM27.5 24a2.5 2.5 0 1 0 0 5 2.5 2.5 0 1 0 0-5zM33 29.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 1 0 0-5z",
            Icon.CHIP to "M11 4h20l8 8v30a3 3 0 0 1-3 3H11a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3zM14 11v15h20V11zM14 35v6h3v-6zM20 35v6h3v-6zM26 35v6h3v-6zM32 35v6h3v-6z",
            Icon.MEDIA to "M3 9h42v30H3zM6 12v3h4v-3zM14 12v3h4v-3zM22 12v3h4v-3zM30 12v3h4v-3zM38 12v3h4v-3zM6 33v3h4v-3zM14 33v3h4v-3zM22 33v3h4v-3zM30 33v3h4v-3zM38 33v3h4v-3zM8 18v12h14V18zM26 18v12h14V18z",
            Icon.MUSIC to "M17 9l25-5v28.5a6.5 6.5 0 1 1-4.5-6.2V12.8l-16 3.2v21.5a6.5 6.5 0 1 1-4.5-6.2z",
            Icon.PHOTO to "M17 8h14l3 5h7a4 4 0 0 1 4 4v20a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V17a4 4 0 0 1 4-4h7zM24 17a9 9 0 1 0 0 18 9 9 0 1 0 0-18zM24 21a5 5 0 1 1 0 10 5 5 0 1 1 0-10z",
            Icon.STAR to "M24 4l6 13.2 14.4 1.4-10.9 9.7 3.2 14.2L24 35.1 11.3 42.5l3.2-14.2L3.6 18.6 18 17.2z",
            Icon.FOLDER to "M3 11a4 4 0 0 1 4-4h12l5 5h17a4 4 0 0 1 4 4v22a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4z",
            Icon.GLOBE to "M24 3a21 21 0 1 0 0 42 21 21 0 1 0 0-42zM24 7c2.6 2.6 4.6 6.8 5.4 12H18.6C19.4 13.8 21.4 9.6 24 7zM18.3 23h11.4c0 1.4 0 2.6-.1 4H18.4c-.1-1.4-.1-2.6-.1-4zM18.6 31h10.8c-.8 4.4-2.8 8.2-5.4 10.4-2.6-2.2-4.6-6-5.4-10.4zM14.6 19H8a17 17 0 0 1 9.4-10.6c-1.4 2.8-2.4 6.6-2.8 10.6zM33.4 19c-.4-4-1.4-7.8-2.8-10.6A17 17 0 0 1 40 19zM7.2 23h7.1c0 1.4 0 2.6.1 4H7.2a17 17 0 0 1 0-4zM33.7 23h7.1a17 17 0 0 1 0 4h-7.2c.1-1.4.1-2.6.1-4zM8 31h6.6c.5 3.8 1.5 7.2 2.9 9.6A17 17 0 0 1 8 31zM33.4 31H40a17 17 0 0 1-9.5 9.6c1.4-2.4 2.4-5.8 2.9-9.6z",
            Icon.PLUS to "M9 5h30a4 4 0 0 1 4 4v30a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4V9a4 4 0 0 1 4-4zM21.5 13v8.5H13v5h8.5V35h5v-8.5H35v-5h-8.5V13z",
            Icon.FOLDER_PLUS to "M3 11a4 4 0 0 1 4-4h12l5 5h17a4 4 0 0 1 4 4v22a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4zM22 19v6h-6v4h6v6h4v-6h6v-4h-6v-6z",
            Icon.FOLDER_EDIT to "M3 11a4 4 0 0 1 4-4h12l5 5h17a4 4 0 0 1 4 4v22a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4zM30.5 18.5l4 4-11 11-5.5 1.5 1.5-5.5z",
            Icon.RESIZE to "M27 5h16v16l-6-6-7 7-4-4 7-7zM21 43H5V27l6 6 7-7 4 4-7 7z",
            Icon.LAYOUT to "M3 7h42v8H3zM3 19h12v22H3zM19 19h26v9H19zM19 32h26v9H19z",
            Icon.SOUND to "M5 17h8l11-9v32l-11-9H5zM29.5 15.5l3-3a16 16 0 0 1 0 23l-3-3a11.7 11.7 0 0 0 0-17zM35 10l3-3a23.5 23.5 0 0 1 0 34l-3-3a19.2 19.2 0 0 0 0-28z",
            Icon.MUTE to "M5 17h8l11-9v32l-11-9H5zM30 18l3-3 5 5 5-5 3 3-5 5 5 5-3 3-5-5-5 5-3-3 5-5z"
        )
        for ((k, v) in defs) iconPaths[k] = PathParser.createPathFromPathData(v).apply { fillType = Path.FillType.EVEN_ODD }
        val pngs = mapOf(
            Icon.SETTINGS to R.drawable.xmb_icon_settings, Icon.ALL to R.drawable.xmb_icon_home,
            Icon.GAMEPAD to R.drawable.xmb_icon_gamepad, Icon.CHIP to R.drawable.xmb_icon_handheld,
            Icon.MEDIA to R.drawable.xmb_icon_film, Icon.MUSIC to R.drawable.xmb_icon_music,
            Icon.PHOTO to R.drawable.xmb_icon_photo, Icon.GLOBE to R.drawable.xmb_icon_globe,
            Icon.CHAT to R.drawable.xmb_icon_chat, Icon.FITNESS to R.drawable.xmb_icon_fitness,
            Icon.RADIO to R.drawable.xmb_icon_radio, Icon.FOOD to R.drawable.xmb_icon_food,
            Icon.COFFEE to R.drawable.xmb_icon_coffee, Icon.PACKAGE to R.drawable.xmb_icon_package,
            Icon.WRENCH to R.drawable.xmb_icon_wrench, Icon.FACE to R.drawable.xmb_icon_face,
            Icon.SOUND to R.drawable.xmb_icon_sound, Icon.DISPLAY to R.drawable.xmb_icon_display
        )
        val blur = Paint().apply { maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL) }
        for ((k, res) in pngs) {
            val b = android.graphics.BitmapFactory.decodeResource(resources, res) ?: continue
            iconBitmaps[k] = b
            // Brillo de selección: alfa del icono difuminado, con 16 px de margen para que no se corte.
            val pad = 16
            val padded = Bitmap.createBitmap(b.width + pad * 2, b.height + pad * 2, Bitmap.Config.ARGB_8888)
            Canvas(padded).drawBitmap(b, pad.toFloat(), pad.toFloat(), null)
            iconGlows[k] = padded.extractAlpha(blur, null)
            padded.recycle()
        }
    }

    // --- API ------------------------------------------------------------------------------------

    /** Vuelve a pedir categorías y elementos al host, conservando la selección. */
    fun reload() {
        val h = host ?: return
        val prevKey = cats.getOrNull(catSel)?.key ?: lastCatKey
        cats = h.xmbCategories()
        items.clear()
        shaders.clear()
        for (c in cats) items[c.key] = h.xmbItems(c)
        catSel = cats.indexOfFirst { it.key == prevKey }.takeIf { it >= 0 } ?: cats.indexOfFirst { it.key == "all" }.coerceAtLeast(0)
        for (c in cats) itemSel[c.key] = (itemSel[c.key] ?: 0).coerceIn(0, maxOf(0, (items[c.key]?.size ?: 1) - 1))
        if (lastCatKey == null) { catPos = catSel.toFloat(); itemPos = selItem().toFloat() }
        lastCatKey = cats.getOrNull(catSel)?.key
        invalidate()
    }

    /** Selecciona la categoría con esa clave (p. ej. tras crear una nueva). */
    fun selectCategory(key: String) {
        val i = cats.indexOfFirst { it.key == key }
        if (i >= 0) setCategory(i)
    }

    fun setTime(t: Float) {
        time = t
        // Interpolación suave hacia la selección (≈ 120 ms a 30 FPS).
        catPos += (catSel - catPos) * 0.35f
        itemPos += (selItem() - itemPos) * 0.35f
        if (abs(catSel - catPos) < 0.002f) catPos = catSel.toFloat()
        if (abs(selItem() - itemPos) < 0.002f) itemPos = selItem().toFloat()
        invalidate()
    }

    private fun currentCat() = cats.getOrNull(catSel)
    private fun currentItems() = currentCat()?.let { items[it.key] } ?: emptyList()
    private fun selItem() = currentCat()?.let { itemSel[it.key] } ?: 0

    private fun setCategory(i: Int) {
        val n = i.coerceIn(0, maxOf(0, cats.size - 1))
        if (n == catSel) return
        catSel = n
        onSound?.invoke(SOUND_CAT)
        itemPos = selItem().toFloat() // la lista de la nueva categoría aparece en su posición
        lastCatKey = cats[n].key
        host?.xmbCategoryChanged(cats[n])
        invalidate()
    }

    private fun setItem(i: Int) {
        val c = currentCat() ?: return
        val n = i.coerceIn(0, maxOf(0, currentItems().size - 1))
        if (n != itemSel[c.key]) onSound?.invoke(SOUND_NAV)
        itemSel[c.key] = n
        invalidate()
    }

    fun moveCategory(d: Int) = setCategory(catSel + d)
    fun moveItem(d: Int) = setItem(selItem() + d)

    fun activate() {
        val c = currentCat() ?: return
        currentItems().getOrNull(selItem())?.let { onSound?.invoke(SOUND_OK); host?.xmbActivate(c, it) }
    }

    fun options() {
        val c = currentCat() ?: return
        currentItems().getOrNull(selItem())?.let { if (it.payload != null) onSound?.invoke(SOUND_OPT); host?.xmbOptions(c, it) }
    }

    /** Mando/teclado. Devuelve true si la tecla se usó. */
    fun handleKey(code: Int, repeat: Boolean): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> moveCategory(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveCategory(+1)
            KeyEvent.KEYCODE_BUTTON_L1 -> if (!repeat) moveCategory(-1)
            KeyEvent.KEYCODE_BUTTON_R1 -> if (!repeat) moveCategory(+1)
            KeyEvent.KEYCODE_DPAD_UP -> moveItem(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveItem(+1)
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (!repeat) activate()
            KeyEvent.KEYCODE_BUTTON_X -> if (!repeat) options()
            else -> return false
        }
        return true
    }

    /** Título del elemento seleccionado (para pruebas y accesibilidad). */
    val selectedTitle: String get() = currentItems().getOrNull(selItem())?.title ?: ""
    val selectedCategoryName: String get() = currentCat()?.name ?: ""

    // --- Táctil ---------------------------------------------------------------------------------

    private var accX = 0f
    private var accY = 0f
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { accX = 0f; accY = 0f; return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            accX += dx; accY += dy
            val stepX = 90 * u; val stepY = 56 * u
            if (abs(accX) > abs(accY) * 1.2f) {
                while (accX > stepX) { moveCategory(+1); accX -= stepX }
                while (accX < -stepX) { moveCategory(-1); accX += stepX }
            } else {
                while (accY > stepY) { moveItem(+1); accY -= stepY }
                while (accY < -stepY) { moveItem(-1); accY += stepY }
            }
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Toque en una categoría: seleccionarla.
            val rowY = CAT_Y * u
            if (abs(e.y - rowY) < 46 * u) {
                val idx = catSel + Math.round((e.x - CAT_X * u) / (CAT_GAP * u))
                if (idx in cats.indices) { setCategory(idx); return true }
            }
            // Toque en un elemento: el elegido se abre; otro se selecciona.
            val hit = itemAt(e.x, e.y)
            if (hit == selItem()) activate() else if (hit >= 0) setItem(hit)
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            val hit = itemAt(e.x, e.y)
            if (hit >= 0) { setItem(hit); options() }
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event) || super.onTouchEvent(event)

    private fun itemAt(x: Float, y: Float): Int {
        if (x < (CAT_X - 60) * u || x > (CAT_X + 520) * u) return -1
        for (i in currentItems().indices) {
            val (top, size) = itemGeom(i - itemPos)
            if (y >= top - 6 * u && y <= top + size + 6 * u) return i
        }
        return -1
    }

    // --- Dibujo ---------------------------------------------------------------------------------


    /** Posición vertical (top) y tamaño del icono para un elemento a distancia d del seleccionado. */
    private fun itemGeom(d: Float): Pair<Float, Float> {
        val sizeSel = 80f; val sizeNext = 52f; val sizePrev = 46f
        return when {
            d >= 1f -> Pair((SEL_TOP + sizeSel + 16 + (d - 1) * 66) * u, sizeNext * u)
            d >= 0f -> { // entre el seleccionado (0) y el siguiente (1)
                val t = d
                Pair((SEL_TOP + t * (sizeSel + 16)) * u, (sizeSel + (sizeNext - sizeSel) * t) * u)
            }
            d > -1f -> { // subiendo por encima de la fila de categorías
                val t = -d
                Pair((SEL_TOP + t * (100 - 62 - SEL_TOP)) * u, (sizeSel + (sizePrev - sizeSel) * t) * u)
            }
            else -> Pair((100 + d * 62) * u, sizePrev * u)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        u = h / 610f
        veil.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            intArrayOf(Color.argb(184, 18, 22, 40), Color.argb(140, 18, 22, 40), Color.argb(31, 18, 22, 40), Color.argb(0, 18, 22, 40)),
            floatArrayOf(0f, 0.38f, 0.58f, 0.66f), Shader.TileMode.CLAMP)
        val fadeEdges = intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT)
        val pos = floatArrayOf(0f, 0.25f, 0.75f, 1f)
        wavePaint.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, fadeEdges, pos, Shader.TileMode.CLAMP)
        waveLine.shader = LinearGradient(0f, 0f, w.toFloat(), 0f, fadeEdges, pos, Shader.TileMode.CLAMP)
        waveLine.strokeWidth = 1.6f * u
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, veil)
        drawWave(canvas, w)
        drawCategories(canvas)
        drawItems(canvas)
        drawClock(canvas, w)
        drawHints(canvas)
    }

    private fun drawWave(canvas: Canvas, w: Float) {
        // Cuatro cintas senoidales translúcidas que se desplazan lentamente.
        val ribbons = arrayOf(floatArrayOf(300f, 38f, 0f, .45f, 70f), floatArrayOf(318f, 30f, 1.3f, .32f, 40f),
            floatArrayOf(290f, 46f, 2.4f, .22f, 26f), floatArrayOf(335f, 24f, 3.6f, .18f, 18f))
        val step = 12f * u
        for (r in ribbons) {
            val ph = r[2] + time * 0.12f
            fun yAt(x: Float): Float { val xd = x / u; return (r[0] + r[1] * sin(xd / 210 + ph) + 14 * sin(xd / 90 + ph * 2)) * u }
            fun tAt(x: Float): Float { val xd = x / u; return r[4] * (0.55f + 0.45f * sin(xd / 260 + ph)) * u }
            path.reset()
            var x = 0f
            path.moveTo(0f, yAt(0f) - tAt(0f) / 2)
            while (x <= w) { path.lineTo(x, yAt(x) - tAt(x) / 2); x += step }
            x = w
            while (x >= 0) { path.lineTo(x, yAt(x) + tAt(x) / 2); x -= step }
            path.close()
            wavePaint.alpha = (r[3] * 0.45f * 255).toInt()
            canvas.drawPath(path, wavePaint)
            path.reset()
            x = 0f
            path.moveTo(0f, yAt(0f))
            while (x <= w) { path.lineTo(x, yAt(x)); x += step / 1.5f }
            waveLine.alpha = ((r[3] + .25f).coerceAtMost(1f) * 255).toInt()
            canvas.drawPath(path, waveLine)
        }
    }

    private fun drawIcon(canvas: Canvas, icon: Icon, cx: Float, cy: Float, size: Float, alpha: Int, glow: Boolean) {
        iconBitmaps[icon]?.let { b ->
            // PNG: se ajusta a la caja conservando la proporción.
            val k = size / maxOf(b.width, b.height)
            val w = b.width * k; val h = b.height * k
            if (glow) iconGlows[icon]?.let { g ->
                val gk = k * 1.0f
                dst.set(cx - g.width * gk / 2, cy - g.height * gk / 2, cx + g.width * gk / 2, cy + g.height * gk / 2)
                glowPaint.alpha = alpha
                canvas.drawBitmap(g, null, dst, glowPaint)
            }
            dst.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            bmpPaint.shader = null; bmpPaint.alpha = alpha
            canvas.drawBitmap(b, null, dst, bmpPaint)
            return
        }
        val p = iconPaths[icon] ?: return
        canvas.save()
        canvas.translate(cx - size / 2, cy - size / 2)
        canvas.scale(size / 48f, size / 48f)
        iconPaint.alpha = alpha
        iconPaint.setShadowLayer(if (glow) 6f else 2f, 0f, if (glow) 0f else 0.6f,
            if (glow) Color.argb(220, 255, 220, 140) else Color.argb(110, 0, 0, 0))
        canvas.drawPath(p, iconPaint)
        canvas.restore()
    }

    private fun drawCategories(canvas: Canvas) {
        for ((i, c) in cats.withIndex()) {
            val d = i - catPos
            val cx = (CAT_X + d * CAT_GAP) * u
            if (cx < -60 * u || cx > width + 60 * u) continue
            val sel = abs(d) < 0.5f
            val k = (1f - abs(d)).coerceIn(0f, 1f) // 1 en el centro
            val size = (46f + 18f * k) * u
            drawIcon(canvas, c.icon, cx, CAT_Y * u, size, (140 + 115 * k).toInt(), sel)
            if (sel) {
                text.textSize = 14f * u; text.typeface = Typeface.DEFAULT; text.alpha = (255 * k).toInt()
                text.setShadowLayer(3f * u, 0f, u, Color.argb(180, 0, 0, 0))
                val tw = text.measureText(c.name)
                canvas.drawText(c.name, cx - tw / 2, (CAT_Y + 50) * u, text)
            }
        }
    }

    private fun drawItems(canvas: Canvas) {
        val list = currentItems()
        val x0 = (CAT_X - 40) * u
        for ((i, it) in list.withIndex()) {
            val d = i - itemPos
            val (top, size) = itemGeom(d)
            if (top > height || top + size < 0) continue
            val left = x0 + (80 * u - size) / 2
            val sel = abs(d) < 0.5f
            val above = d < -0.5f
            val alpha = if (above) 115 else 255
            // Caja del icono.
            rect.set(left, top, left + size, top + size)
            val r = 12f * u * (size / (80 * u))
            if (it.bitmap != null) {
                val b = it.bitmap
                val sh = shaders.getOrPut(b) { BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
                m.setScale(size / b.width, size / b.height); m.postTranslate(left, top)
                sh.setLocalMatrix(m)
                bmpPaint.shader = sh; bmpPaint.alpha = alpha
                canvas.drawRoundRect(rect, r, r, bmpPaint)
            } else if (it.glyph != null) {
                // Elementos de Ajustes: solo el glifo, sin caja ni borde (como en el XMB).
                drawIcon(canvas, it.glyph, left + size / 2, top + size / 2, size * 0.78f, alpha, sel)
            } else {
                m.setScale(size, size); m.postTranslate(left, top)
                boxShader.setLocalMatrix(m)
                boxPaint.shader = boxShader; boxPaint.alpha = alpha
                canvas.drawRoundRect(rect, r, r, boxPaint)
                boxPaint.shader = null
            }
            // Borde: dorado con brillo si está seleccionado (no en los glifos de Ajustes).
            if (it.bitmap == null && it.glyph != null) {
                // sin borde
            } else if (sel) {
                strokePaint.strokeWidth = 2.5f * u; strokePaint.color = Color.rgb(255, 215, 106)
                strokePaint.setShadowLayer(11f * u, 0f, 0f, Color.argb(215, 255, 210, 110))
            } else {
                strokePaint.strokeWidth = 1.5f * u; strokePaint.color = Color.argb(alpha * 90 / 255, 255, 255, 255)
                strokePaint.clearShadowLayer()
            }
            if (!(it.bitmap == null && it.glyph != null)) canvas.drawRoundRect(rect, r, r, strokePaint)
            // Texto.
            val tx = left + size + 16 * u
            text.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            text.setShadowLayer(3f * u, 0f, u, Color.argb(200, 0, 0, 0))
            if (sel) {
                text.textSize = 22f * u; text.alpha = 255
                canvas.drawText(it.title, tx, top + size / 2 - 2 * u, text)
                text.typeface = Typeface.DEFAULT
                text.textSize = 12.5f * u; text.alpha = 230
                canvas.drawText(it.subtitle, tx, top + size / 2 + 18 * u, text)
            } else {
                text.textSize = 15f * u; text.alpha = if (above) 115 else 205
                canvas.drawText(it.title, tx, top + size / 2 + 5 * u, text)
            }
        }
    }

    private fun drawClock(canvas: Canvas, w: Float) {
        text.typeface = Typeface.DEFAULT
        text.clearShadowLayer()
        text.textSize = 17f * u
        val date = dateText
        val dw = text.measureText(date)
        text.textSize = 20f * u
        val tw = text.measureText(clockText)
        text.textSize = 14f * u
        val pct = "$batteryPct %"
        val pw = text.measureText(pct)
        val pad = 14f * u; val gap = 14f * u; val battW = 30f * u
        val boxW = pad * 2 + dw + gap + tw + gap + battW + gap + pw
        val right = w - 26f * u; val top = 18f * u; val boxH = 36f * u
        rect.set(right - boxW, top, right, top + boxH)
        boxPaint.shader = null; boxPaint.color = Color.argb(140, 18, 22, 40)
        canvas.drawRoundRect(rect, 4 * u, 4 * u, boxPaint)
        strokePaint.clearShadowLayer(); strokePaint.strokeWidth = u; strokePaint.color = Color.argb(178, 255, 215, 110)
        canvas.drawRoundRect(rect, 4 * u, 4 * u, strokePaint)
        val base = top + boxH / 2 + 6 * u
        var x = rect.left + pad
        text.alpha = 255
        text.textSize = 17f * u; canvas.drawText(date, x, base, text); x += dw + gap
        text.textSize = 20f * u; canvas.drawText(clockText, x, base + u, text); x += tw + gap
        // Batería.
        val by = top + boxH / 2
        strokePaint.color = Color.WHITE; strokePaint.strokeWidth = 1.6f * u
        rect.set(x, by - 6 * u, x + 26 * u, by + 6 * u)
        canvas.drawRoundRect(rect, 3 * u, 3 * u, strokePaint)
        boxPaint.color = if (charging) Color.rgb(255, 215, 106) else Color.WHITE
        rect.set(x + 2.5f * u, by - 3.5f * u, x + 2.5f * u + 21 * u * batteryPct / 100f, by + 3.5f * u)
        canvas.drawRect(rect, boxPaint)
        boxPaint.color = Color.WHITE
        canvas.drawRect(x + 26 * u, by - 2 * u, x + 28.5f * u, by + 2 * u, boxPaint)
        x += battW + gap
        text.textSize = 14f * u; canvas.drawText(pct, x, base - u, text)
    }

    private fun drawHints(canvas: Canvas) {
        val y = height - 22f * u
        var x = 22f * u
        text.typeface = Typeface.DEFAULT; text.textSize = 12f * u; text.alpha = 220
        text.setShadowLayer(3f * u, 0f, u, Color.argb(180, 0, 0, 0))
        val hints = if (gamepadHints)
            listOf("A" to "Abrir", "X" to "Opciones", "Y" to "Añadir apps", "L1 R1" to "Categoría", "SELECT" to "Tamaño de Rin")
        else listOf("" to "Toca para abrir · Mantén pulsado para opciones · Desliza para moverte")
        strokePaint.clearShadowLayer(); strokePaint.color = Color.WHITE; strokePaint.strokeWidth = 1.4f * u
        for ((btn, label) in hints) {
            if (btn.isNotEmpty()) {
                text.textSize = 10.5f * u
                val bw = maxOf(18f * u, text.measureText(btn) + 10 * u)
                rect.set(x, y - 13 * u, x + bw, y + 5 * u)
                canvas.drawRoundRect(rect, 9 * u, 9 * u, strokePaint)
                canvas.drawText(btn, x + (bw - text.measureText(btn)) / 2, y + 0.5f * u, text)
                x += bw + 5 * u
            }
            text.textSize = 12f * u
            canvas.drawText(label, x, y, text)
            x += text.measureText(label) + 16 * u
        }
    }
}
