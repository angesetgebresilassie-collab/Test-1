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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Artwork lookup order:
 * 1. artwork found by the file-name lookup (downloaded once, saved on disk per URL)
 * 2. embedded / MediaStore thumbnail from the file itself (offline fallback)
 *
 * Bitmaps are decoded at the size the caller needs (`maxPx`, 0 = original) and the memory cache
 * is limited by bytes, so list rows no longer hold full-size covers.
 *
 * Scrolling: decoding and downloading are limited to a few at a time (the gates below). A card
 * that scrolls away while it is still waiting for its turn is cancelled and never runs, so a fast
 * fling no longer starts dozens of decodes at once and competes with the UI thread for CPU.
 */
object ArtworkLoader {
    private val memory = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8L).coerceIn(8L * 1024 * 1024, Int.MAX_VALUE.toLong()).toInt()
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            runCatching { value.asAndroidBitmap().byteCount }.getOrDefault(1_000_000)
    }

    private val decodeGate = Semaphore(3)
    private val netGate = Semaphore(4)

    private fun diskKey(song: Song, remoteUrl: String?): String =
        song.id.toString() + (remoteUrl?.takeIf { it.isNotBlank() }?.let { "-" + it.hashCode() } ?: "")

    private fun memKey(song: Song, remoteUrl: String?, maxPx: Int): String =
        diskKey(song, remoteUrl) + "@" + maxPx

    /** Already-decoded artwork, read synchronously (no flicker when a row scrolls back in). */
    fun peek(song: Song, remoteUrl: String?, maxPx: Int): ImageBitmap? =
        memory.get(memKey(song, remoteUrl, maxPx))

    suspend fun load(context: Context, song: Song, remoteUrl: String?, maxPx: Int = 0): ImageBitmap? {
        val mKey = memKey(song, remoteUrl, maxPx)
        memory.get(mKey)?.let { return it }
        val key = diskKey(song, remoteUrl)

        val bitmap: Bitmap? =
            decodeGate.withPermit { withContext(Dispatchers.IO) { fromDisk(context, key, maxPx) } }
                ?: remoteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    netGate.withPermit { withContext(Dispatchers.IO) { fromRemote(context, key, url, maxPx) } }
                }
                ?: decodeGate.withPermit { withContext(Dispatchers.IO) { fromLocal(context, song, maxPx) } }

        return bitmap?.asImageBitmap()?.also { memory.put(mKey, it) }
    }

    private fun dir(context: Context): File =
        File(context.filesDir, "artwork").apply { mkdirs() }

    private fun fromLocal(context: Context, song: Song, maxPx: Int): Bitmap? = runCatching {
        val side = if (maxPx > 0) maxPx else 600
        context.contentResolver.loadThumbnail(song.uri, Size(side, side), null)
    }.getOrNull()

    private fun fromDisk(context: Context, key: String, maxPx: Int): Bitmap? =
        File(dir(context), "$key.jpg").takeIf { it.exists() }?.let { decodeFile(it.path, maxPx) }

    private fun fromRemote(context: Context, key: String, url: String, maxPx: Int): Bitmap? =
        runCatching {
            // Timeouts: a stalled connection must not hold a download slot forever.
            val connection = URL(url).openConnection().apply {
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            val bytes = connection.getInputStream().use { it.readBytes() }
            File(dir(context), "$key.jpg").writeBytes(bytes)
            decodeBytes(bytes, maxPx)
        }.getOrNull()

    /** Largest power-of-two shrink that still leaves the longest side >= [maxPx]. */
    private fun sampleSize(width: Int, height: Int, maxPx: Int): Int {
        if (maxPx <= 0) return 1
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxPx) sample *= 2
        return sample
    }

    private fun decodeFile(path: String, maxPx: Int): Bitmap? {
        if (maxPx <= 0) return BitmapFactory.decodeFile(path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun decodeBytes(bytes: ByteArray, maxPx: Int): Bitmap? {
        if (maxPx <= 0) return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}

/**
 * [maxPx]: longest side the artwork is shown at, in pixels (0 = full size).
 *
 * When only [remoteUrl] changes (the background lookup found better art for this song), the
 * cover that is already showing stays on screen until the new one is ready, instead of blanking
 * and reloading, which used to make cards flash while scrolling during the lookup.
 */
@Composable
fun rememberArtwork(song: Song?, remoteUrl: String?, maxPx: Int = 0): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(song?.id, maxPx) {
        mutableStateOf(if (song == null) null else ArtworkLoader.peek(song, remoteUrl, maxPx))
    }
    LaunchedEffect(song?.id, remoteUrl, maxPx) {
        if (song == null) {
            bitmap = null
        } else {
            val cached = ArtworkLoader.peek(song, remoteUrl, maxPx)
            if (cached != null) {
                bitmap = cached
            } else {
                ArtworkLoader.load(context, song, remoteUrl, maxPx)?.let { bitmap = it }
            }
        }
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
