package com.yaserx.voicely

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri

/** Small, low-latency soundboard engine backed by SoundPool. */
object AudioEngine {
    private var pool: SoundPool? = null
    private val sampleIds = mutableMapOf<String, Int>()
    private val ready = mutableSetOf<String>()
    private val loading = mutableSetOf<String>()
    private val queuedPlays = mutableMapOf<String, Int>()

    fun init() {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setAudioAttributes(attributes)
            .setMaxStreams(16)
            .build()
        pool?.setOnLoadCompleteListener { soundPool, sampleId, status ->
            val itemId = sampleIds.entries.firstOrNull { it.value == sampleId }?.key
            if (itemId != null) {
                loading.remove(itemId)
                if (status == 0) {
                    ready.add(itemId)
                    val count = queuedPlays.remove(itemId) ?: 0
                    repeat(count.coerceAtMost(4)) {
                        soundPool.play(sampleId, 1f, 1f, 1, 0, 1f)
                    }
                } else {
                    sampleIds.remove(itemId)
                    queuedPlays.remove(itemId)
                }
            }
        }
    }

    fun preload(context: Context, sound: SoundItem) {
        init()
        if (sampleIds.containsKey(sound.id) || loading.contains(sound.id)) return
        runCatching {
            val afd = context.contentResolver.openAssetFileDescriptor(Uri.parse(sound.uri), "r") ?: return
            val sampleId = pool?.load(afd.fileDescriptor, afd.startOffset, afd.length, 1) ?: 0
            afd.close()
            if (sampleId != 0) {
                sampleIds[sound.id] = sampleId
                loading.add(sound.id)
            }
        }
    }

    fun play(context: Context, sound: SoundItem) {
        init()
        val sampleId = sampleIds[sound.id]
        if (sampleId == null) {
            queuedPlays[sound.id] = (queuedPlays[sound.id] ?: 0) + 1
            preload(context, sound)
            return
        }
        if (!ready.contains(sound.id)) {
            queuedPlays[sound.id] = (queuedPlays[sound.id] ?: 0) + 1
            return
        }
        pool?.play(sampleId, 1f, 1f, 1, 0, 1f)
    }

    fun unload(soundId: String) {
        sampleIds.remove(soundId)?.let { pool?.unload(it) }
        ready.remove(soundId)
        loading.remove(soundId)
        queuedPlays.remove(soundId)
    }

    fun release() {
        pool?.release()
        pool = null
        sampleIds.clear()
        ready.clear()
        loading.clear()
        queuedPlays.clear()
    }
}
