package com.safetrack.watch.app.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.safetrack.watch.R

/**
 * Short sound effects for the SOS flow (same sounds as the phone app, made by
 * SafeTrack). Loaded once at app start so the first countdown tick is ready.
 * Plays at the watch's sound volume and never throws.
 */
object WatchSounds {
    enum class Effect(val res: Int) {
        Tick(R.raw.tick),
        Sent(R.raw.sos_sent),
        Cancel(R.raw.cancel),
        Error(R.raw.error),
    }

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Effect, Int>()

    fun init(context: Context) {
        if (pool != null) return
        val created = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        Effect.entries.forEach { ids[it] = created.load(context.applicationContext, it.res, 1) }
        pool = created
    }

    fun play(effect: Effect) {
        val id = ids[effect] ?: return
        runCatching { pool?.play(id, 1f, 1f, 1, 0, 1f) }
    }
}
