package com.test1.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(val id: String, val name: String, val songIds: List<Long>)

/** Playlists are stored as JSON in SharedPreferences (song ids only, so they survive rescans). */
class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(): List<Playlist> = runCatching {
        val arr = JSONArray(prefs.getString("all", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("songs")
            Playlist(
                id = o.getString("id"),
                name = o.getString("name"),
                songIds = (0 until ids.length()).map { ids.getLong(it) },
            )
        }
    }.getOrDefault(emptyList())

    fun save(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("songs", JSONArray(p.songIds))
            )
        }
        prefs.edit().putString("all", arr.toString()).apply()
    }
}
