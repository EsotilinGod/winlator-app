package com.example.rinlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Icono de batería dibujado: contorno, relleno según el nivel y rayo si está cargando. */
class BatteryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var level = 100
        set(v) { field = v.coerceIn(0, 100); invalidate() }
    var charging = false
        set(v) { field = v; invalidate() }

    private val dp = resources.displayMetrics.density
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.6f * dp; color = Color.rgb(43, 47, 58)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val r = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val tip = 3 * dp
        r.set(outline.strokeWidth, outline.strokeWidth, w - tip - outline.strokeWidth, h - outline.strokeWidth)
        canvas.drawRoundRect(r, 3 * dp, 3 * dp, outline)
        canvas.drawRect(w - tip, h * 0.3f, w, h * 0.7f, Paint(outline).apply { style = Paint.Style.FILL })
        fill.color = when {
            charging -> Color.rgb(242, 181, 48)
            level <= 15 -> Color.rgb(226, 74, 60)
            else -> Color.rgb(43, 47, 58)
        }
        val inset = 2.4f * dp
        val inner = RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset)
        canvas.drawRoundRect(RectF(inner.left, inner.top, inner.left + inner.width() * level / 100f, inner.bottom),
            1.5f * dp, 1.5f * dp, fill)
        if (charging) {
            val cx = r.centerX(); val cy = r.centerY(); val s = h * 0.38f
            path.reset()
            path.moveTo(cx + s * 0.2f, cy - s); path.lineTo(cx - s * 0.5f, cy + s * 0.1f)
            path.lineTo(cx, cy + s * 0.1f); path.lineTo(cx - s * 0.2f, cy + s)
            path.lineTo(cx + s * 0.5f, cy - s * 0.1f); path.lineTo(cx, cy - s * 0.1f); path.close()
            canvas.drawPath(path, bolt)
        }
    }
}
