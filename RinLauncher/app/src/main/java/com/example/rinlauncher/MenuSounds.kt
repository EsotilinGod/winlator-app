package com.example.rinlauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Sonidos del menú. El cursor (xmb_cursor.ogg) y el de encendido (xmb_boot.ogg) son creaciones propias
 * del usuario; abrir/opciones (xmb_ok.wav, xmb_opt.wav) se sintetizan con tools/make_sounds.py.
 * Al cambiar de categoría se reproduce el cursor un poco más grave (velocidad 0,9).
 * SoundPool los mantiene decodificados en memoria (≈ 120 kB) para que suenen sin retardo.
 * Usan el volumen multimedia; [enabled] los silencia por completo.
 */
class MenuSounds(context: Context) {
    var enabled = true

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .build()
    private val loaded = HashSet<Int>()
    private val ids = IntArray(4)

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        val cursor = pool.load(context, R.raw.xmb_cursor, 1)
        ids[XmbView.SOUND_NAV] = cursor
        ids[XmbView.SOUND_CAT] = cursor
        ids[XmbView.SOUND_OK] = pool.load(context, R.raw.xmb_ok, 1)
        ids[XmbView.SOUND_OPT] = pool.load(context, R.raw.xmb_opt, 1)
    }

    fun play(sound: Int) {
        if (!enabled) return
        val id = ids.getOrNull(sound) ?: return
        val rate = if (sound == XmbView.SOUND_CAT) 0.9f else 1f
        if (id in loaded) pool.play(id, 1f, 1f, 1, 0, rate)
    }

    fun release() = pool.release()
}
