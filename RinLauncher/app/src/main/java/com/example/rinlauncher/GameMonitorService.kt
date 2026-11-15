package com.example.rinlauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

/**
 * Monitor en juego: cápsula flotante con FPS y temperatura de la batería, encima de cualquier app.
 *
 * - Temperatura: ACTION_BATTERY_CHANGED (cualquier app puede leerla).
 * - FPS: Android no deja a una app normal leer los fotogramas de otra. El script fps_helper.sh, lanzado
 *   desde el PC por ADB (permisos de "shell"), escribe cada 0,5 s el contador de fotogramas de la pantalla
 *   (`service call SurfaceFlinger 1013`) en files/fps_count; aquí se convierte en FPS. Sin el script
 *   se muestra "— FPS".
 */
class GameMonitorService : Service() {

    companion object {
        private const val CHANNEL = "monitor"
        private const val PREFS = "launcher"
        private const val KEY_X = "monitor_x"
        private const val KEY_Y = "monitor_y"

        fun dir(c: Context): File = c.getExternalFilesDir(null) ?: c.filesDir

        /** Escribe el script que el PC lanza por ADB (ruta compartida legible por "shell"). */
        fun writeHelper(c: Context) {
            val d = dir(c)
            val path = d.absolutePath
            File(d, "fps_helper.sh").writeText("""
                |#!/system/bin/sh
                |# Lanzado desde el PC por ADB. Escribe el contador de fotogramas de la pantalla para el
                |# monitor de Rin Launcher. Solo trabaja mientras el monitor está activo (archivo monitor_on).
                |D=$path
                |[ -f ${'$'}D/fps_helper.pid ] && kill ${'$'}(cat ${'$'}D/fps_helper.pid) 2>/dev/null
                |echo ${'$'}${'$'} > ${'$'}D/fps_helper.pid
                |while true; do
                |  if [ -f ${'$'}D/monitor_on ]; then
                |    service call SurfaceFlinger 1013 | cut -d'(' -f2 | cut -d' ' -f1 > ${'$'}D/fps_count.tmp && mv ${'$'}D/fps_count.tmp ${'$'}D/fps_count
                |    sleep 0.5
                |  else
                |    sleep 3
                |  fi
                |done
                |""".trimMargin())
        }

        /** Convierte dos lecturas del contador en FPS (null si no hay datos válidos). */
        fun fps(prevCount: Long, prevMs: Long, count: Long, ms: Long): Int? {
            val dt = ms - prevMs
            if (prevCount < 0 || dt <= 0 || count < prevCount) return null
            return Math.round((count - prevCount) * 1000.0 / dt).toInt()
        }

        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, GameMonitorService::class.java))
        fun stop(c: Context) { c.stopService(Intent(c, GameMonitorService::class.java)) }
    }

    private lateinit var wm: WindowManager
    private var view: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var temp = 0f
    private var prevCount = -1L
    private var prevMs = 0L
    private var lastFps: Int? = null

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f }
    }

    private val tick = object : Runnable {
        override fun run() {
            readFps()
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotice()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        writeHelper(this)
        File(dir(this), "monitor_on").writeText("1")
        ContextCompat.registerReceiver(this, battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f }
        addOverlay()
        handler.post(tick)
    }

    private fun startForegroundNotice() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Monitor en juego", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Monitor en juego activo")
            .setContentText("FPS y temperatura de la batería")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        val dp = resources.displayMetrics.density
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val tv = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 50 * dp
                setColor(Color.argb(150, 12, 14, 24))
                setStroke((1 * dp).toInt(), Color.argb(150, 242, 181, 48))
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, (16 * dp).toInt())
            y = prefs.getInt(KEY_Y, (12 * dp).toInt())
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Arrastrar para moverla; la posición se recuerda.
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - downX).toInt(); lp.y = startY + (e.rawY - downY).toInt()
                    wm.updateViewLayout(tv, lp)
                }
                MotionEvent.ACTION_UP -> prefs.edit().putInt(KEY_X, lp.x).putInt(KEY_Y, lp.y).apply()
            }
            true
        }
        wm.addView(tv, lp)
        view = tv
    }

    private fun readFps() {
        val f = File(dir(this), "fps_count")
        // Si el script no ha escrito en 3 s, no está corriendo.
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 3000) { lastFps = null; prevCount = -1; return }
        val count = try { f.readText().trim().toLong(16) } catch (_: Exception) { return }
        val now = SystemClock.elapsedRealtime()
        fps(prevCount, prevMs, count, now)?.let { lastFps = it }
        prevCount = count; prevMs = now
    }

    private fun render() {
        val tv = view ?: return
        val es = Locale("es")
        val fpsText = lastFps?.let { "$it FPS" } ?: "— FPS"
        val tempText = String.format(es, "%.1f °C", temp)
        val tempColor = when {
            temp >= 43f -> Color.rgb(255, 90, 80)
            temp >= 39f -> Color.rgb(255, 170, 60)
            else -> Color.rgb(140, 230, 160)
        }
        tv.text = SpannableStringBuilder().apply {
            append(fpsText)
            append("  ·  ")
            val s = length
            append(tempText)
            setSpan(ForegroundColorSpan(tempColor), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        File(dir(this), "monitor_on").delete()
        try { unregisterReceiver(battery) } catch (_: Exception) {}
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        super.onDestroy()
    }
}
