package com.test1.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Playlist(val id: String, val name: String, val songIds: List<Long>)

class PlaylistStore(context: Context) {
    private val file = File(context.filesDir, "playlists.json")

    fun load(): List<Playlist> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val s = o.getJSONArray("songs")
                Playlist(o.getString("id"), o.getString("name"), (0 until s.length()).map { s.getLong(it) })
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

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
        file.writeText(arr.toString())
    }
}
