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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Artwork lookup order (file-name based metadata beats the file's own embedded art, which may be wrong):
 * 1. artwork downloaded earlier from the metadata lookup (saved on disk)
 * 2. the remote artwork URL, downloaded once and saved to disk
 * 3. embedded / MediaStore thumbnail from the file itself (offline fallback)
 */
object ArtworkLoader {
    private val memory = LruCache<String, ImageBitmap>(64)

    /** Song ids whose cached bitmap came from the metadata lookup (not the embedded fallback). */
    private val authoritative: MutableSet<String> = ConcurrentHashMap.newKeySet()

    suspend fun load(context: Context, song: Song, remoteUrl: String?): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val key = song.id.toString()
            val cached = memory.get(key)
            if (cached != null && (remoteUrl == null || key in authoritative)) return@withContext cached

            fromDisk(context, key)?.let { bmp ->
                return@withContext bmp.asImageBitmap().also {
                    memory.put(key, it)
                    authoritative += key
                }
            }
            if (remoteUrl != null) {
                fromRemote(context, key, remoteUrl)?.let { bmp ->
                    return@withContext bmp.asImageBitmap().also {
                        memory.put(key, it)
                        authoritative += key
                    }
                }
            }
            cached ?: fromLocal(context, song)?.asImageBitmap()?.also { memory.put(key, it) }
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

/**
 * A vivid accent colour from the artwork (average of a 16x16 downscale, saturation boosted).
 * Returns null for greyscale art so the default palette is used instead.
 */
fun ImageBitmap.accentColor(): Color? = runCatching {
    val src = asAndroidBitmap()
    val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
    val small = Bitmap.createScaledBitmap(soft, 16, 16, true)
    var r = 0L
    var g = 0L
    var b = 0L
    for (y in 0 until 16) {
        for (x in 0 until 16) {
            val p = small.getPixel(x, y)
            r += android.graphics.Color.red(p)
            g += android.graphics.Color.green(p)
            b += android.graphics.Color.blue(p)
        }
    }
    val avg = android.graphics.Color.rgb((r / 256).toInt(), (g / 256).toInt(), (b / 256).toInt())
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(avg, hsv)
    if (hsv[1] < 0.12f) {
        null
    } else {
        hsv[1] = (hsv[1] * 1.3f).coerceIn(0.5f, 0.95f)
        hsv[2] = hsv[2].coerceIn(0.65f, 1f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
}.getOrNull()
