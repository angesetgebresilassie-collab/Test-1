package com.test1.player

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import kotlin.math.abs

data class LyricWord(val timeMs: Long, val text: String)

/** timeMs < 0 means the lyrics are plain (not time-synced). */
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord>?)

sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data class Ready(val lines: List<LyricLine>) : LyricsState
}

object LrcParser {
    private val lineTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")

    private fun toMs(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return min * 60_000 + sec * 1000 + frac
    }

    /** Supports standard LRC and enhanced LRC (<mm:ss.xx> word tags). */
    fun parse(text: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in text.lines()) {
            val tags = lineTag.findAll(raw).toList()
            if (tags.isEmpty()) continue
            val body = raw.substring(tags.last().range.last + 1)
            val wm = wordTag.findAll(body).toList()
            val words = if (wm.isEmpty()) null else wm.mapIndexedNotNull { i, m ->
                val end = if (i + 1 < wm.size) wm[i + 1].range.first else body.length
                val t = body.substring(m.range.last + 1, end)
                if (t.isBlank()) null else LyricWord(toMs(m), t)
            }
            val clean = body.replace(wordTag, "").trim()
            for (t in tags) out += LyricLine(toMs(t), clean, words)
        }
        out.sortBy { it.timeMs }
        return out
    }

    fun plain(text: String): List<LyricLine> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(-1, it, null) }
}

/** Lyrics order: disk cache -> LRCLIB lookup (once, then cached). */
class LyricsRepository(context: Context) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    suspend fun get(song: Song): List<LyricLine>? = withContext(Dispatchers.IO) {
        val lrc = File(dir, "${song.id}.lrc")
        val txt = File(dir, "${song.id}.txt")
        val none = File(dir, "${song.id}.none")

        if (lrc.exists()) return@withContext LrcParser.parse(lrc.readText())
        if (txt.exists()) return@withContext LrcParser.plain(txt.readText())
        if (none.exists()) return@withContext null

        try {
            val body = fetch(song)
            if (body == null) {
                none.createNewFile()
                return@withContext null
            }
            val obj = JSONObject(body)
            val synced = obj.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
            val plain = obj.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
            when {
                synced != null -> {
                    lrc.writeText(synced)
                    LrcParser.parse(synced)
                }
                plain != null -> {
                    txt.writeText(plain)
                    LrcParser.plain(plain)
                }
                else -> {
                    none.createNewFile()
                    null
                }
            }
        } catch (_: Exception) {
            null // offline: try again next time
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun fetch(song: Song): String? {
        val seconds = song.durationMs / 1000
        val exact = Http.get(
            "https://lrclib.net/api/get?artist_name=${enc(song.artist)}&track_name=${enc(song.title)}" +
                "&album_name=${enc(song.album)}&duration=$seconds"
        )
        if (exact != null) return exact.toString(Charsets.UTF_8)

        val search = Http.get(
            "https://lrclib.net/api/search?track_name=${enc(song.title)}&artist_name=${enc(song.artist)}"
        ) ?: return null
        val arr = JSONArray(search.toString(Charsets.UTF_8))
        var best: JSONObject? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val synced = o.optString("syncedLyrics")
            if (synced.isNotBlank() && synced != "null") {
                if (abs(o.optDouble("duration", 0.0) - song.durationMs / 1000.0) <= 3.0) {
                    best = o
                    break
                }
                if (best == null) best = o
            }
        }
        return (best ?: arr.optJSONObject(0))?.toString()
    }
}
