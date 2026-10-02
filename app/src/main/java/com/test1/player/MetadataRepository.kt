package com.test1.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MetadataRepository(private val cache: MetadataCache) {
    private val retried = mutableSetOf<Long>()

    suspend fun enrich(song: Song): CachedMetadata = withContext(Dispatchers.IO) {
        val durationSec = song.durationMs / 1000

        cache.get(song.id)?.let { cached ->
            // Cached without any lyrics: try once more per session (maybe it was offline).
            if (cached.lyrics != null || cached.synced != null || !retried.add(song.id)) {
                return@withContext cached
            }
            val found = runCatching { lyrics(cached.artist, cached.title, durationSec) }.getOrNull()
                ?: return@withContext cached
            return@withContext cached.copy(lyrics = found.first, synced = found.second)
                .also { cache.put(song.id, it) }
        }

        val i = runCatching { itunes(song) }.getOrNull()
        val title = i?.title?.takeIf { it.isNotBlank() } ?: song.title
        val artist = i?.artist?.takeIf { it.isNotBlank() } ?: song.artist
        val album = i?.album?.takeIf { it.isNotBlank() } ?: song.album
        val found = runCatching { lyrics(artist, title, durationSec) }.getOrNull()
        val result = CachedMetadata(title, artist, album, i?.artworkUrl, found?.first, found?.second)
        // Only cache when the lookup actually worked, so offline plays don't lock in bare metadata.
        if (i != null) cache.put(song.id, result)
        result
    }

    private fun itunes(s: Song): CachedMetadata {
        val q = URLEncoder.encode(s.artist + " " + s.title, "UTF-8")
        val o = JSONObject(get("https://itunes.apple.com/search?term=$q&entity=song&limit=1"))
            .getJSONArray("results").getJSONObject(0)
        return CachedMetadata(
            o.optString("trackName"),
            o.optString("artistName"),
            o.optString("collectionName"),
            o.optString("artworkUrl100").replace("100x100", "600x600"),
            null,
        )
    }

    /** Returns (plain, synced) or null when nothing was found. */
    private fun lyrics(artist: String, title: String, durationSec: Long): Pair<String?, String?>? {
        val q = "artist_name=" + URLEncoder.encode(artist, "UTF-8") +
            "&track_name=" + URLEncoder.encode(title, "UTF-8")
        val exact = listOf(
            "https://lrclib.net/api/get?$q&duration=$durationSec",
            "https://lrclib.net/api/get?$q",
        )
        for (u in exact) {
            val r = runCatching { parseLyrics(JSONObject(get(u))) }.getOrNull()
            if (r != null) return r
        }
        val arr = runCatching { JSONArray(get("https://lrclib.net/api/search?$q")) }.getOrNull()
            ?: return null
        var plainOnly: Pair<String?, String?>? = null
        for (k in 0 until arr.length()) {
            val r = parseLyrics(arr.getJSONObject(k)) ?: continue
            if (r.second != null) return r
            if (plainOnly == null) plainOnly = r
        }
        return plainOnly
    }

    private fun parseLyrics(o: JSONObject): Pair<String?, String?>? {
        val plain = str(o, "plainLyrics")
        val synced = str(o, "syncedLyrics")
        return if (plain == null && synced == null) null else plain to synced
    }

    // org.json's optString returns the literal "null" for JSON nulls, so check first.
    private fun str(o: JSONObject, k: String): String? =
        if (o.isNull(k)) null else o.optString(k).ifBlank { null }

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Test1Player/0.2")
        return try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP " + c.responseCode)
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
