package com.test1.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Metadata is looked up from the song's FILE NAME (embedded tags can be misleading).
 * A search result is only accepted when its words overlap enough with the file name,
 * so a wrong song never replaces the file-name title/artist.
 */
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

        val parsed = FileNames.parse(song.fileName.ifBlank { song.title })
        val attempt = runCatching { itunes(parsed) }
        val lookupOk = attempt.isSuccess
        val i = attempt.getOrNull()
        val title = i?.title?.takeIf { it.isNotBlank() } ?: parsed.title
        val artist = i?.artist?.takeIf { it.isNotBlank() } ?: parsed.artist ?: song.artist
        val album = i?.album?.takeIf { it.isNotBlank() } ?: song.album
        val found = runCatching { lyrics(artist, title, durationSec) }.getOrNull()
        val result = CachedMetadata(title, artist, album, i?.artworkUrl, found?.first, found?.second)
        // Cache only when the lookup itself worked (match or confirmed no-match), so offline plays don't lock in bare data.
        if (lookupOk) cache.put(song.id, result)
        result
    }

    // ---- file-name search with word-overlap matching ----

    private val noise = setOf("feat", "ft", "featuring", "with", "prod", "official", "audio", "video", "lyrics")
    private val brackets = Regex("""\s*[(\[{][^)\]}]*[)\]}]""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    private fun tokens(s: String): Set<String> =
        s.lowercase()
            .replace(nonWord, " ")
            .split(' ')
            .filter { it.isNotEmpty() && it !in noise }
            .toSet()

    /** Best iTunes match for the file name, or null when nothing overlaps enough. */
    private fun itunes(p: ParsedName): CachedMetadata? {
        val query = listOfNotNull(p.artist, p.title).joinToString(" ")
        val q = URLEncoder.encode(query, "UTF-8")
        val results = JSONObject(get("https://itunes.apple.com/search?term=$q&entity=song&limit=10"))
            .getJSONArray("results")

        val wantTitle = tokens(p.title)
        val wantArtist: Set<String> = p.artist?.let { tokens(it) } ?: emptySet()
        if (wantTitle.isEmpty()) return null

        var best: JSONObject? = null
        var bestScore = 0.0
        for (k in 0 until results.length()) {
            val o = results.getJSONObject(k)
            val trackName = o.optString("trackName")
            val haveTitle = tokens(brackets.replace(trackName, ""))
            if (haveTitle.isEmpty()) continue
            val union = (wantTitle + haveTitle).size.toDouble()
            val titleScore = (wantTitle intersect haveTitle).size / union
            if (titleScore < 0.7) continue

            val haveArtist = tokens(o.optString("artistName") + " " + trackName)
            val artistHit = wantArtist.isEmpty() || (wantArtist intersect haveArtist).isNotEmpty()
            // With an artist in the file name it must overlap; without one demand a near-exact title.
            if (!artistHit || (wantArtist.isEmpty() && titleScore < 0.85)) continue

            val score = titleScore + if (wantArtist.isNotEmpty()) 0.2 else 0.0
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
