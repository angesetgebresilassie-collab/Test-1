package com.test1.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer

private class HttpError(val code: Int) : Exception("HTTP $code")

/** Tracks whether a lyrics lookup hit a real failure (offline, 5xx, 429) as opposed to a plain "not found". */
private class Lookup {
    var failed = false
}

/**
 * Metadata is looked up from the song's FILE NAME (embedded tags can be misleading).
 * Matching is word-based and order-independent, so "Artist - Title", "Title - Artist" and
 * names with no artist at all still find the right song, while unrelated results are rejected.
 *
 * Lyrics are cached on disk per song (see [LyricsCache]): once found they never hit the network
 * again, and a confirmed miss is not retried for a few days.
 */
class MetadataRepository(private val cache: MetadataCache) {
    private val lyricsCache get() = cache.lyrics

    private fun fullName(song: Song): String {
        val parsed = FileNames.parse(song.fileName.ifBlank { song.title })
        return listOfNotNull(parsed.artist, parsed.title).joinToString(" ")
    }

    /** Already-saved lookup result (no network). */
    fun cached(song: Song): CachedMetadata? = cache.get(song.id)

    /**
     * Title / artist / artwork only (no lyrics) - used by the background pass over the whole
     * library. Saved only when a real match was found. Lyrics are fetched when the song plays.
     */
    suspend fun lookup(song: Song): CachedMetadata? = withContext(Dispatchers.IO) {
        val i = runCatching { itunes(fullName(song)) }.getOrNull() ?: return@withContext null
        val result = CachedMetadata(i.title, i.artist, i.album, i.artworkUrl, null, null)
        cache.put(song.id, result)
        result
    }

    suspend fun enrich(song: Song): CachedMetadata = withContext(Dispatchers.IO) {
        val durationSec = song.durationMs / 1000
        val parsed = FileNames.parse(song.fileName.ifBlank { song.title })
        val full = listOfNotNull(parsed.artist, parsed.title).joinToString(" ")

        // ---- title / artist / artwork ----
        val saved = cache.get(song.id)
        val matched: Boolean
        val base: CachedMetadata
        if (saved != null) {
            matched = true
            base = saved
        } else {
            val i = runCatching { itunes(full) }.getOrNull()
            matched = i != null
            base = CachedMetadata(
                i?.title?.takeIf { it.isNotBlank() } ?: parsed.title,
                i?.artist?.takeIf { it.isNotBlank() } ?: parsed.artist ?: song.artist,
                i?.album?.takeIf { it.isNotBlank() } ?: song.album,
                i?.artworkUrl,
                null,
                null,
            )
            // Only cache a real match, so unmatched / offline songs are looked up again next time.
            if (matched) cache.put(song.id, base)
        }

        // ---- lyrics (own cache, independent of the iTunes match) ----
        var entry = lyricsCache.get(song.id)
        if (entry == null && saved != null && (saved.lyrics != null || saved.synced != null)) {
            // Older versions stored lyrics inside the metadata cache: move them over.
            entry = lyricsCache.put(song.id, saved.lyrics, saved.synced)
        }
        if (entry == null || !entry.fresh()) {
            val st = Lookup()
            val artist = if (matched) base.artist else parsed.artist
            val found = runCatching { lyrics(artist, base.title, full, durationSec, st) }
                .getOrElse { st.failed = true; null }
            if (found != null) {
                entry = lyricsCache.put(song.id, found.first, found.second)
            } else if (!st.failed) {
                // Searched properly and nothing exists: do not search again on every play.
                entry = lyricsCache.putMiss(song.id)
            }
            // else: offline / server trouble - save nothing so it is retried next time.
        }
        base.copy(lyrics = entry?.plain, synced = entry?.synced)
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

    /** GET that returns null for a plain 404 (not found) and flags [st] for any real failure. */
    private fun fetch(u: String, st: Lookup): String? = try {
        get(u)
    } catch (e: HttpError) {
        if (e.code != 404) st.failed = true
        null
    } catch (e: Exception) {
        st.failed = true
        null
    }

    /** Returns (plain, synced) or null when nothing was found. */
    private fun lyrics(
        artist: String?,
        title: String,
        full: String,
        durationSec: Long,
        st: Lookup,
    ): Pair<String?, String?>? {
        val a = artist?.takeUnless { it.isBlank() || it.equals("Unknown artist", ignoreCase = true) }
        if (a != null) {
            val q = "artist_name=" + URLEncoder.encode(a, "UTF-8") +
                "&track_name=" + URLEncoder.encode(title, "UTF-8")
            for (u in listOf("https://lrclib.net/api/get?$q&duration=$durationSec", "https://lrclib.net/api/get?$q")) {
                val body = fetch(u, st) ?: continue
                val r = runCatching { parseLyrics(JSONObject(body)) }.getOrNull()
                if (r != null) return r
            }
            val arr = fetch("https://lrclib.net/api/search?$q", st)
                ?.let { runCatching { JSONArray(it) }.getOrNull() }
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
        return lyricsByText(full.ifBlank { title }, st)
    }

    /** Free-text lyrics search on the file name (works when there is no artist or the order is swapped). */
    private fun lyricsByText(full: String, st: Lookup): Pair<String?, String?>? {
        val want = fileTokens(full)
        val arr = fetch("https://lrclib.net/api/search?q=" + URLEncoder.encode(full, "UTF-8"), st)
            ?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return null
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
            if (c.responseCode !in 200..299) throw HttpError(c.responseCode)
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
