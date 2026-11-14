package com.example.rinlauncher

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Lista de apps instaladas con buscador y casillas. Marcadas = están en el launcher.
 * Devuelve el conjunto final de componentes elegidos.
 */
object AppPickerDialog {

    fun show(context: Context, store: LauncherStore, onShortcut: (() -> Unit)? = null,
             onDone: (List<InstalledApp>, Set<String>) -> Unit) {
        val dp = context.resources.displayMetrics.density
        val all = store.installedApps()
        val selected = store.apps.map { key(it.pkg, it.activity) }.toMutableSet()
        var shown = all

        val search = EditText(context).apply {
            hint = "Buscar aplicación…"
            setSingleLine()
        }
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (260 * dp).toInt())
        }
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = shown.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    // Navegable con cruceta/mando: resaltado dorado al tener el foco.
                    isFocusable = true
                    background = android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused),
                            android.graphics.drawable.ColorDrawable(android.graphics.Color.argb(70, 242, 181, 48)))
                        addState(intArrayOf(), android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    }
                    addView(ImageView(context).apply { id = 1; layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt()) })
                    addView(TextView(context).apply {
                        id = 2; textSize = 15f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (12 * dp).toInt() }
                    })
                    addView(CheckBox(context).apply { id = 3; isClickable = false; isFocusable = false })
                }
                return object : RecyclerView.ViewHolder(row) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val app = shown[position]
                val v = holder.itemView
                val k = key(app.pkg, app.activity)
                try {
                    v.findViewById<ImageView>(1).setImageDrawable(
                        context.packageManager.getActivityIcon(ComponentName(app.pkg, app.activity)))
                } catch (_: Exception) {
                    v.findViewById<ImageView>(1).setImageDrawable(null)
                }
                v.findViewById<TextView>(2).text = app.label
                val cb = v.findViewById<CheckBox>(3)
                cb.isChecked = k in selected
                v.setOnClickListener {
                    if (!selected.add(k)) selected.remove(k)
                    cb.isChecked = k in selected
                }
            }
        }
        list.adapter = adapter
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim().lowercase()
                shown = if (q.isEmpty()) all else all.filter { it.label.lowercase().contains(q) }
                @Suppress("NotifyDataSetChanged") adapter.notifyDataSetChanged()
            }
        })

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
            addView(search)
            addView(list)
        }
        AlertDialog.Builder(context, R.style.Dialog_Rin)
            .setTitle("Elige las aplicaciones")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ -> onDone(all, selected) }
            .apply { if (onShortcut != null) setNeutralButton("Acceso directo…") { _, _ -> onShortcut() } }
            .setNegativeButton("Cancelar", null)
            .show()
        search.visibility = View.VISIBLE
    }

    fun key(pkg: String, activity: String) = "$pkg/$activity"
}
