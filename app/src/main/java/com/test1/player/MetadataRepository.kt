package com.test1.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class MetadataRepository(private val cache: MetadataCache) {
    private val retried: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /**
     * Looks a song up by its FILE NAME (never the embedded tags) and caches the result.
     * [withLyrics] = false is used for the bulk background pass to keep it to one request per song.
     */
    suspend fun enrich(song: Song, withLyrics: Boolean = true): CachedMetadata = withContext(Dispatchers.IO) {
        val durationSec = song.durationMs / 1000

        cache.get(song.id)?.let { cached ->
            // Cached without any lyrics: try once more per session (maybe it was offline).
            if (!withLyrics || cached.lyrics != null || cached.synced != null || !retried.add(song.id)) {
                return@withContext cached
            }
            val found = runCatching { lyrics(cached.artist, cached.title, durationSec) }.getOrNull()
                ?: return@withContext cached
            return@withContext cached.copy(lyrics = found.first, synced = found.second)
                .also { cache.put(song.id, it) }
        }

        val lookup = runCatching { itunes(song) }
        val i = lookup.getOrNull()
        val title = i?.title?.takeIf { it.isNotBlank() } ?: song.title
        val artist = i?.artist?.takeIf { it.isNotBlank() } ?: song.artist
        val album = i?.album?.takeIf { it.isNotBlank() } ?: song.album
        val found = if (withLyrics) runCatching { lyrics(artist, title, durationSec) }.getOrNull() else null
        val result = CachedMetadata(title, artist, album, i?.artworkUrl, found?.first, found?.second)
        // Cache whenever the service answered (even "no match"), so offline runs don't lock in
        // bare metadata and unmatched songs aren't searched again and again.
        if (lookup.isSuccess) cache.put(song.id, result)
        result
    }

    /**
     * Searches iTunes with the cleaned file name and only accepts a result whose words overlap
     * the file name well enough. Returns null for "no confident match"; throws on network errors.
     */
    private fun itunes(s: Song): CachedMetadata? {
        val query = FileNames.searchQuery(s.fileName.ifBlank { s.title })
        val wanted = tokens(query)
        if (wanted.isEmpty()) return null
        val q = URLEncoder.encode(query, "UTF-8")
        val results = JSONObject(get("https://itunes.apple.com/search?term=$q&entity=song&limit=10"))
            .getJSONArray("results")

        var best: JSONObject? = null
        var bestScore = 0f
        for (k in 0 until results.length()) {
            val o = results.getJSONObject(k)
            val have = tokens(o.optString("artistName") + " " + o.optString("trackName"))
            val score = wanted.count { it in have }.toFloat() / wanted.size
            if (score > bestScore) {
                best = o
                bestScore = score
            }
        }
        val chosen = best ?: return null
        if (bestScore < 0.6f) return null
        return CachedMetadata(
            chosen.optString("trackName"),
            chosen.optString("artistName"),
            chosen.optString("collectionName"),
            chosen.optString("artworkUrl100").replace("100x100", "600x600"),
            null,
        )
    }

    private fun tokens(s: String): Set<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }.toSet()

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
        c.setRequestProperty("User-Agent", "Test1Player/0.3")
        return try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP " + c.responseCode)
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
