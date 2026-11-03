package com.test1.player

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * Streamed (online) songs that were added to a playlist. Playlists only store song ids, and a
 * streamed song has no file for the library to find again, so its details are kept here
 * (SharedPreferences) and used to rebuild the song when the playlist opens.
 */
class SavedStreams(context: Context) {
    private val prefs = context.getSharedPreferences("saved_streams", Context.MODE_PRIVATE)

    fun load(): Map<Long, Song> = runCatching {
        val arr = JSONArray(prefs.getString("all", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val vid = o.optString("v").ifBlank { null } ?: return@mapNotNull null
            val id = o.getLong("id")
            Song(
                id = id,
                title = o.getString("title"),
                artist = o.getString("artist"),
                album = "",
                albumId = id,
                track = 0,
                durationMs = o.optLong("dur", 0L),
                uri = Uri.parse("${NewPipeSupport.SCHEME}://stream/$vid"),
                fileName = o.optString("file"),
                artworkUrl = o.optString("art").ifBlank { null },
            )
        }.associateBy { it.id }
    }.getOrDefault(emptyMap())

    fun save(songs: Map<Long, Song>) {
        val arr = JSONArray()
        songs.values.forEach { s ->
            val vid = s.videoId ?: return@forEach
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("v", vid)
                    .put("title", s.title)
                    .put("artist", s.artist)
                    .put("dur", s.durationMs)
                    .put("file", s.fileName)
                    .put("art", s.artworkUrl ?: "")
            )
        }
        prefs.edit().putString("all", arr.toString()).apply()
    }
}
