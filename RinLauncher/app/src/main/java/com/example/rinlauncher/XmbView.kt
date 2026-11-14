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

    enum class Icon { SETTINGS, ALL, GAMEPAD, CHIP, MEDIA, STAR, FOLDER, PLUS, FOLDER_PLUS, RESIZE, LAYOUT }

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
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.WHITE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()
    private val m = Matrix()
    private val iconPaths = HashMap<Icon, Path>()
    private val shaders = HashMap<Bitmap, BitmapShader>()   // se crean una vez por imagen
    private val boxShader = LinearGradient(0f, 0f, 1f, 1f, Color.rgb(70, 80, 120), Color.rgb(120, 130, 170), Shader.TileMode.CLAMP)

    init {
        isFocusable = true
        val defs = mapOf(
            Icon.SETTINGS to "M17,24a7,7 0 1,0 14,0a7,7 0 1,0 -14,0M24 6v6M24 36v6M6 24h6M36 24h6M11.3 11.3l4.2 4.2M32.5 32.5l4.2 4.2M11.3 36.7l4.2-4.2M32.5 15.5l4.2-4.2",
            Icon.ALL to "M8,8h13v13h-13zM27,8h13v13h-13zM8,27h13v13h-13zM27,27h13v13h-13z",
            Icon.GAMEPAD to "M14 16h20c5 0 8 4 9 10l1 6c.6 4-3.5 6-6 3.5L33 30H15l-5 5.5C7.5 38 3.4 36 4 32l1-6c1-6 4-10 9-10zM14 21v7M10.5 24.5h7M30.4,22a1.6,1.6 0 1,0 3.2,0a1.6,1.6 0 1,0 -3.2,0M34.4,26a1.6,1.6 0 1,0 3.2,0a1.6,1.6 0 1,0 -3.2,0",
            Icon.CHIP to "M15,12h18a3,3 0 0 1 3,3v18a3,3 0 0 1 -3,3h-18a3,3 0 0 1 -3,-3v-18a3,3 0 0 1 3,-3zM18,18h12v12h-12zM17 6v6M24 6v6M31 6v6M17 36v6M24 36v6M31 36v6M6 17h6M6 24h6M6 31h6M36 17h6M36 24h6M36 31h6",
            Icon.MEDIA to "M9,10h30a3,3 0 0 1 3,3v22a3,3 0 0 1 -3,3h-30a3,3 0 0 1 -3,-3v-22a3,3 0 0 1 3,-3zM20 18l10 6-10 6z",
            Icon.STAR to "M24 6l5.2 11.6L42 19l-9.6 8.6L35 40 24 33.6 13 40l2.6-12.4L6 19l12.8-1.4z",
            Icon.FOLDER to "M6 13h14l4 4h18v21H6z",
            Icon.PLUS to "M24 10v28M10 24h28",
            Icon.FOLDER_PLUS to "M6 13h14l4 4h18v21H6zM24 22v12M18 28h12",
            Icon.RESIZE to "M28 8h12v12M40 8L27 21M20 40H8V28M8 40l13-13",
            Icon.LAYOUT to "M8 10h32v28H8zM8 19h32M20 19v19"
        )
        for ((k, v) in defs) iconPaths[k] = PathParser.createPathFromPathData(v)
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
        itemPos = selItem().toFloat() // la lista de la nueva categoría aparece en su posición
        lastCatKey = cats[n].key
        host?.xmbCategoryChanged(cats[n])
        invalidate()
    }

    private fun setItem(i: Int) {
        val c = currentCat() ?: return
        val n = i.coerceIn(0, maxOf(0, currentItems().size - 1))
        itemSel[c.key] = n
        invalidate()
    }

    fun moveCategory(d: Int) = setCategory(catSel + d)
    fun moveItem(d: Int) = setItem(selItem() + d)

    fun activate() {
        val c = currentCat() ?: return
        currentItems().getOrNull(selItem())?.let { host?.xmbActivate(c, it) }
    }

    fun options() {
        val c = currentCat() ?: return
        currentItems().getOrNull(selItem())?.let { host?.xmbOptions(c, it) }
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

    private companion object {
        const val CAT_X = 300f      // centro horizontal de la columna seleccionada
        const val CAT_Y = 141f      // centro vertical de la fila de categorías
        const val CAT_GAP = 112f
        const val SEL_TOP = 228f    // borde superior del elemento seleccionado
    }

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
        val p = iconPaths[icon] ?: return
        canvas.save()
        canvas.translate(cx - size / 2, cy - size / 2)
        canvas.scale(size / 48f, size / 48f)
        iconPaint.strokeWidth = 2.6f
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
            } else {
                m.setScale(size, size); m.postTranslate(left, top)
                boxShader.setLocalMatrix(m)
                boxPaint.shader = boxShader; boxPaint.alpha = alpha
                canvas.drawRoundRect(rect, r, r, boxPaint)
                boxPaint.shader = null
                it.glyph?.let { g -> drawIcon(canvas, g, left + size / 2, top + size / 2, size * 0.62f, alpha, false) }
            }
            // Borde: dorado con brillo si está seleccionado.
            if (sel) {
                strokePaint.strokeWidth = 2.5f * u; strokePaint.color = Color.rgb(255, 215, 106)
                strokePaint.setShadowLayer(11f * u, 0f, 0f, Color.argb(215, 255, 210, 110))
            } else {
                strokePaint.strokeWidth = 1.5f * u; strokePaint.color = Color.argb(alpha * 90 / 255, 255, 255, 255)
                strokePaint.clearShadowLayer()
            }
            canvas.drawRoundRect(rect, r, r, strokePaint)
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
