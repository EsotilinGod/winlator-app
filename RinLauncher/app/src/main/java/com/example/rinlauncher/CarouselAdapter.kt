package com.example.rinlauncher

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Tarjetas del carrusel: carátula propia o, si no hay, icono sobre un degradado dorado. */
class CarouselAdapter(
    private val store: LauncherStore,
    private val onClick: (position: Int) -> Unit,
    private val onLongClick: (position: Int) -> Unit
) : RecyclerView.Adapter<CarouselAdapter.Holder>() {

    var items: List<AppEntry> = emptyList()
    var cardWidth = 0
    var cardHeight = 0
    var focused = -1
        set(v) {
            if (v == field) return
            val old = field; field = v
            if (old in items.indices) notifyItemChanged(old, PAYLOAD_FOCUS)
            if (v in items.indices) notifyItemChanged(v, PAYLOAD_FOCUS)
        }

    companion object { private const val PAYLOAD_FOCUS = "focus" }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val card: FrameLayout = v.findViewById(R.id.card)
        val cover: ImageView = v.findViewById(R.id.cover)
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        val dp = parent.resources.displayMetrics.density
        val h = Holder(v)
        h.card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 18 * dp)
            }
        }
        h.card.clipToOutline = true
        h.label.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.argb(170, 20, 22, 30))
        )
        return h
    }

    override fun onBindViewHolder(h: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_FOCUS)) applyFocus(h, position) else super.onBindViewHolder(h, position, payloads)
    }

    override fun onBindViewHolder(h: Holder, position: Int) {
        val a = items[position]
        if (cardWidth > 0) {
            h.itemView.layoutParams = h.itemView.layoutParams.apply { width = cardWidth; height = cardHeight }
        }
        h.label.text = store.label(a)
        val coverFile = store.coverFile(a)
        if (coverFile != null) {
            h.cover.setImageBitmap(BitmapFactory.decodeFile(coverFile.path))
            h.cover.background = null
            h.icon.visibility = View.GONE
        } else {
            h.cover.setImageDrawable(null)
            h.cover.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(255, 255, 255), Color.rgb(255, 238, 196), Color.rgb(246, 205, 120))
            )
            h.icon.visibility = View.VISIBLE
            h.icon.setImageDrawable(store.icon(a))
        }
        h.itemView.setOnClickListener { h.bindingAdapterPosition.takeIf { it >= 0 }?.let(onClick) }
        h.itemView.setOnLongClickListener { h.bindingAdapterPosition.takeIf { it >= 0 }?.let(onLongClick); true }
        applyFocus(h, position)
    }

    private fun applyFocus(h: Holder, position: Int) {
        val dp = h.itemView.resources.displayMetrics.density
        val isFocused = position == focused
        h.card.foreground = GradientDrawable().apply {
            cornerRadius = 18 * dp
            setStroke(((if (isFocused) 3.5f else 1f) * dp).toInt(),
                if (isFocused) Color.rgb(242, 181, 48) else Color.argb(90, 255, 255, 255))
        }
        h.card.elevation = (if (isFocused) 16 else 5) * dp
    }
}
