package com.yaserx.voicely

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Local sound metadata. The audio itself stays in the user's selected storage URI. */
data class SoundItem(
    val id: String,
    val title: String,
    val uri: String,
    val favorite: Boolean = false,
    val board: String = "Gaming"
)

object SoundLibrary {
    private const val PREFS = "voicely_library"
    private const val KEY = "sounds"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun all(): List<SoundItem> {
        if (!::prefs.isInitialized) return emptyList()
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val title = o.optString("title")
                    val uri = o.optString("uri")
                    if (id.isNotBlank() && uri.isNotBlank()) {
                        add(SoundItem(id, title.ifBlank { "New Sound" }, uri, o.optBoolean("favorite"), o.optString("board", "Gaming")))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(sounds: List<SoundItem>) {
        if (!::prefs.isInitialized) return
        val array = JSONArray()
        sounds.forEach { sound ->
            array.put(JSONObject().apply {
                put("id", sound.id)
                put("title", sound.title)
                put("uri", sound.uri)
                put("favorite", sound.favorite)
                put("board", sound.board)
            })
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun add(sound: SoundItem): List<SoundItem> {
        val updated = all() + sound
        save(updated)
        return updated
    }

    fun update(updated: SoundItem): List<SoundItem> {
        val sounds = all().map { if (it.id == updated.id) updated else it }
        save(sounds)
        return sounds
    }

    fun delete(id: String): List<SoundItem> {
        val sounds = all().filterNot { it.id == id }
        save(sounds)
        return sounds
    }
}
