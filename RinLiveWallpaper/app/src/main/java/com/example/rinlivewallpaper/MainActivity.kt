package com.example.rinlivewallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        val title = TextView(this).apply {
            text = "Rin Live Wallpaper"
            textSize = 24f
        }
        val info = TextView(this).apply {
            text = "Fondo vertical con pulsos dorados y partículas sutiles.\n\nPulsa el botón para abrir la vista previa del fondo animado y aplicarlo."
            textSize = 16f
            setPadding(0, 24, 0, 32)
        }
        val button = Button(this).apply {
            text = "Previsualizar y aplicar fondo"
            setOnClickListener {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, RinWallpaperService::class.java))
                }
                startActivity(intent)
            }
        }
        layout.addView(title)
        layout.addView(info)
        layout.addView(button)
        setContentView(layout)
    }
}
