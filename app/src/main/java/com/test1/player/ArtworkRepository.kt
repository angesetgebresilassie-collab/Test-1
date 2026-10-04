package com.test1.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/**
 * Artwork order: disk cache -> embedded art in the file -> iTunes lookup (once, then cached).
 * Lists pass allowOnline = false so scrolling never triggers network calls.
 */
class ArtworkRepository(private val context: Context) {
    private val dir = File(context.filesDir, "artwork").apply { mkdirs() }
    private val memory = LruCache<Long, Bitmap>(48)

    suspend fun get(song: Song, allowOnline: Boolean = true): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(song.id)?.let { return@withContext it }

        val file = File(dir, "${song.id}.jpg")
        var bmp: Bitmap? = if (file.exists()) BitmapFactory.decodeFile(file.path) else null

        if (bmp == null) {
            bmp = embedded(song)
            val found = bmp
            if (found != null) save(found, file)
        }

        if (bmp == null && allowOnline) {
            val miss = File(dir, "${song.id}.none")
            if (!miss.exists()) {
                var checked = false
                try {
                    bmp = online(song)
                    checked = true
                } catch (_: Exception) {
                    // offline or server error: try again next time
                }
                val found = bmp
                if (found != null) save(found, file) else if (checked) miss.createNewFile()
            }
        }

        bmp?.also { memory.put(song.id, it) }
    }

    private fun embedded(song: Song): Bitmap? = try {
        context.contentResolver.loadThumbnail(song.uri, Size(600, 600), null)
    } catch (_: Exception) {
        null
    }

    private fun online(song: Song): Bitmap? {
        val term = URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")
        val body = Http.get("https://itunes.apple.com/search?term=$term&entity=song&limit=1") ?: return null
        val results = JSONObject(body.toString(Charsets.UTF_8)).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val url = results.getJSONObject(0).optString("artworkUrl100").replace("100x100bb", "600x600bb")
        if (url.isBlank()) return null
        val bytes = Http.get(url) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun save(bmp: Bitmap, file: File) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }
}
