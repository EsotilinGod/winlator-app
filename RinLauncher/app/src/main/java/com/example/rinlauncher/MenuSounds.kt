package com.example.rinlauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Sonidos del menú (res/raw/xmb_*.wav, sintetizados con tools/make_sounds.py).
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
        ids[XmbView.SOUND_NAV] = pool.load(context, R.raw.xmb_nav, 1)
        ids[XmbView.SOUND_CAT] = pool.load(context, R.raw.xmb_cat, 1)
        ids[XmbView.SOUND_OK] = pool.load(context, R.raw.xmb_ok, 1)
        ids[XmbView.SOUND_OPT] = pool.load(context, R.raw.xmb_opt, 1)
    }

    fun play(sound: Int) {
        if (!enabled) return
        val id = ids.getOrNull(sound) ?: return
        if (id in loaded) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
