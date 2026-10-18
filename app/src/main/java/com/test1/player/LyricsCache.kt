package com.test1.player

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Lyrics saved on disk, one small file per song (filesDir/lyrics/<songId>.json).
 *
 * - Found lyrics are kept for good, so a song never goes back to the network for them.
 * - A confirmed "no lyrics exist" is remembered for [MISS_TTL_MS] so it is not searched on every
 *   play. Failed lookups (offline, server error) are never saved, so they are retried next time.
 * - Independent of the artwork/metadata cache: lyrics are kept even when there is no iTunes match.
 */
class LyricsCache(context: Context) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
    private val lock = Any()

    data class Entry(val plain: String?, val synced: String?, val savedAt: Long) {
        val found: Boolean get() = plain != null || synced != null

        /** Found lyrics never expire; a saved miss expires after [MISS_TTL_MS]. */
        fun fresh(now: Long = System.currentTimeMillis()): Boolean = found || now - savedAt < MISS_TTL_MS
    }

    private fun file(id: Long) = File(dir, "$id.json")

    fun get(id: Long): Entry? = synchronized(lock) {
        try {
            val f = file(id)
            if (!f.exists()) return@synchronized null
            val o = JSONObject(f.readText())
            Entry(
                plain = o.optString("plain").ifBlank { null },
                synced = o.optString("synced").ifBlank { null },
                savedAt = o.optLong("at", 0L),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Saves found lyrics (plain and/or synced LRC). */
    fun put(id: Long, plain: String?, synced: String?): Entry = write(id, plain, synced)

    /** Remembers that no lyrics were found for this song. */
    fun putMiss(id: Long): Entry = write(id, null, null)

    private fun write(id: Long, plain: String?, synced: String?): Entry {
        val entry = Entry(plain?.ifBlank { null }, synced?.ifBlank { null }, System.currentTimeMillis())
        synchronized(lock) {
            try {
                dir.mkdirs()
                val json = JSONObject()
                    .put("v", 1)
                    .put("at", entry.savedAt)
                    .put("plain", entry.plain ?: "")
                    .put("synced", entry.synced ?: "")
                    .toString()
                // Write to a temp file first so a crash can never leave a half-written entry.
                val tmp = File(dir, "$id.tmp")
                tmp.writeText(json)
                val target = file(id)
                if (!tmp.renameTo(target)) {
                    target.writeText(json)
                    tmp.delete()
                }
            } catch (e: Exception) {
                // Caching is best effort; the lyrics are still returned to the caller.
            }
        }
        return entry
    }

    /** Deletes every saved lyric (e.g. from a future "clear cache" setting). */
    fun clear() = synchronized(lock) {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    companion object {
        const val MISS_TTL_MS = 3L * 24 * 60 * 60 * 1000
    }
}
