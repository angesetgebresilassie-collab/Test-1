package com.test1.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Artwork lookup order:
 * 1. embedded / MediaStore thumbnail from the file itself (offline)
 * 2. previously downloaded artwork saved on disk
 * 3. remote artwork URL (iTunes), downloaded once and saved to disk
 */
object ArtworkLoader {
    private val memory = LruCache<String, ImageBitmap>(64)

    suspend fun load(context: Context, song: Song, remoteUrl: String?): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val key = song.id.toString()
            memory.get(key)?.let { return@withContext it }
            val bitmap = fromLocal(context, song)
                ?: fromDisk(context, key)
                ?: fromRemote(context, key, remoteUrl)
            bitmap?.asImageBitmap()?.also { memory.put(key, it) }
        }

    private fun dir(context: Context): File =
        File(context.filesDir, "artwork").apply { mkdirs() }

    private fun fromLocal(context: Context, song: Song): Bitmap? = runCatching {
        context.contentResolver.loadThumbnail(song.uri, Size(600, 600), null)
    }.getOrNull()

    private fun fromDisk(context: Context, key: String): Bitmap? =
        File(dir(context), "$key.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    private fun fromRemote(context: Context, key: String, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val bytes = URL(url).openStream().use { it.readBytes() }
            File(dir(context), "$key.jpg").writeBytes(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}

@Composable
fun rememberArtwork(song: Song?, remoteUrl: String?): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(song?.id, remoteUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(song?.id, remoteUrl) {
        bitmap = if (song == null) null else ArtworkLoader.load(context, song, remoteUrl)
    }
    return bitmap
}
