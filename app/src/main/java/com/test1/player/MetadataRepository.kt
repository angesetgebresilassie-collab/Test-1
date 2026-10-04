package com.test1.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

/**
 * Metadata is looked up from the song's FILE NAME (embedded tags can be misleading).
 * Matching is word-based and order-independent, so "Artist - Title", "Title - Artist" and
 * names with no artist at all still find the right song, while unrelated results are rejected.
 */
class MetadataRepository(private val cache: MetadataCache) {
    private val retried = mutableSetOf<Long>()

    suspend fun enrich(song: Song): CachedMetadata = withContext(Dispatchers.IO) {
        val durationSec = song.durationMs / 1000
        val parsed = FileNames.parse(song.fileName.ifBlank { song.title })
        val full = listOfNotNull(parsed.artist, parsed.title).joinToString(" ")

        cache.get(song.id)?.let { cached ->
            // Cached without any lyrics: try once more per session (maybe it was offline).
            if (cached.lyrics != null || cached.synced != null || !retried.add(song.id)) {
                return@withContext cached
            }
            val found = runCatching { lyrics(cached.artist, cached.title, full, durationSec) }.getOrNull()
                ?: return@withContext cached
            return@withContext cached.copy(lyrics = found.first, synced = found.second)
                .also { cache.put(song.id, it) }
        }

        val i = runCatching { itunes(full) }.getOrNull()
        val title = i?.title?.takeIf { it.isNotBlank() } ?: parsed.title
        val artist = i?.artist?.takeIf { it.isNotBlank() } ?: parsed.artist ?: song.artist
        val album = i?.album?.takeIf { it.isNotBlank() } ?: song.album
        val found = runCatching { lyrics(i?.artist ?: parsed.artist, title, full, durationSec) }.getOrNull()
        val result = CachedMetadata(title, artist, album, i?.artworkUrl, found?.first, found?.second)
        // Only cache a real match, so unmatched / offline songs are looked up again next time.
        if (i != null) cache.put(song.id, result)
        result
    }

    // ---- word-overlap matching ----

    private val noise = setOf("feat", "ft", "featuring", "with", "prod", "official", "audio", "video", "lyrics")
    private val brackets = Regex("""\s*[(\[{][^)\]}]*[)\]}]""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")
    private val marks = Regex("""\p{M}+""")

    private fun tokens(s: String): Set<String> =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace(nonWord, " ")
            .split(' ')
            .filter { it.isNotEmpty() && it !in noise }
            .toSet()

    private fun fileTokens(full: String): Set<String> =
        tokens(brackets.replace(full, "")).ifEmpty { tokens(full) }

    /** Best iTunes result for the file name, or null when nothing overlaps enough. */
    private fun itunes(full: String): CachedMetadata? {
        val want = fileTokens(full)
        if (want.isEmpty()) return null
        val q = URLEncoder.encode(full, "UTF-8")
        val results = JSONObject(get("https://itunes.apple.com/search?term=$q&entity=song&limit=10"))
            .getJSONArray("results")

        var best: JSONObject? = null
        var bestScore = 0.0
        for (k in 0 until results.length()) {
            val o = results.getJSONObject(k)
            val have = tokens(o.optString("artistName") + " " + brackets.replace(o.optString("trackName"), ""))
            if (have.isEmpty()) continue
            val shared = (want intersect have).size.toDouble()
            val coverage = shared / want.size   // how much of the file name the result explains
            val precision = shared / have.size  // how much of the result is in the file name
            if (coverage < 0.75 || precision < 0.5) continue
            val score = coverage + precision
            if (score > bestScore) {
                bestScore = score
                best = o
            }
        }
        val o = best ?: return null
        return CachedMetadata(
            o.optString("trackName"),
            o.optString("artistName"),
            o.optString("collectionName"),
            o.optString("artworkUrl100").replace("100x100", "600x600"),
            null,
        )
    }

    /** Returns (plain, synced) or null when nothing was found. */
    private fun lyrics(artist: String?, title: String, full: String, durationSec: Long): Pair<String?, String?>? {
        val a = artist?.takeUnless { it.isBlank() || it.equals("Unknown artist", ignoreCase = true) }
        if (a != null) {
            val q = "artist_name=" + URLEncoder.encode(a, "UTF-8") +
                "&track_name=" + URLEncoder.encode(title, "UTF-8")
            for (u in listOf("https://lrclib.net/api/get?$q&duration=$durationSec", "https://lrclib.net/api/get?$q")) {
                val r = runCatching { parseLyrics(JSONObject(get(u))) }.getOrNull()
                if (r != null) return r
            }
            val arr = runCatching { JSONArray(get("https://lrclib.net/api/search?$q")) }.getOrNull()
            if (arr != null) {
                var plainOnly: Pair<String?, String?>? = null
                for (k in 0 until arr.length()) {
                    val r = parseLyrics(arr.getJSONObject(k)) ?: continue
                    if (r.second != null) return r
                    if (plainOnly == null) plainOnly = r
                }
                if (plainOnly != null) return plainOnly
            }
        }
        return lyricsByText(full.ifBlank { title })
    }

    /** Free-text lyrics search on the file name (works when there is no artist or the order is swapped). */
    private fun lyricsByText(full: String): Pair<String?, String?>? {
        val want = fileTokens(full)
        val arr = runCatching {
            JSONArray(get("https://lrclib.net/api/search?q=" + URLEncoder.encode(full, "UTF-8")))
        }.getOrNull() ?: return null
        var plainOnly: Pair<String?, String?>? = null
        for (k in 0 until arr.length()) {
            val o = arr.getJSONObject(k)
            val t = tokens(brackets.replace(o.optString("trackName"), ""))
            if (t.isEmpty() || (t intersect want).size.toDouble() / t.size < 0.8) continue
            val r = parseLyrics(o) ?: continue
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
