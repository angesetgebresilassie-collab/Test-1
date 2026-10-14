package com.test1.player

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How much of a music card's height the blurred text strip covers. */
const val STRIP_FRACTION = 0.42f

private val stripCache = LruCache<Int, ImageBitmap>(96)

/**
 * The blurred strip behind a card's text, made once per artwork instead of blurring live.
 *
 * A live blur (Modifier.blur) on every card would be re-run by the GPU on every scroll frame,
 * which is exactly the kind of thing that makes a grid stutter. Instead the bottom part of the
 * artwork is shrunk to a handful of pixels (an average of that area) and stretched back up with
 * smooth filtering, which looks like a soft frosted blur. The top rows are faded to transparent
 * inside the bitmap itself, so the strip melts into the sharp artwork with no extra layers.
 */
private fun buildStrip(art: ImageBitmap): ImageBitmap? = runCatching {
    val src = art.asAndroidBitmap()
    val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src

    // Same slice the card shows: centre 80% of the width (a 4:5 card crops a square cover),
    // bottom STRIP_FRACTION of the height.
    val cropW = (soft.width * 0.8f).toInt().coerceAtLeast(1)
    val cropX = (soft.width - cropW) / 2
    val cropH = (soft.height * STRIP_FRACTION).toInt().coerceAtLeast(1)
    var cur = Bitmap.createBitmap(soft, cropX, soft.height - cropH, cropW, cropH)

    val cols = 16
    val rows = 8
    // Halve repeatedly first: one big jump with bilinear filtering would skip pixels (noisy).
    while (cur.width / 2 >= cols * 2 && cur.height / 2 >= rows * 2) {
        cur = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
    }
    val tiny = Bitmap.createScaledBitmap(cur, cols, rows, true)

    val out = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
    for (y in 0 until rows) {
        // Transparent at the top edge, fully opaque from ~45% down.
        val t = ((y + 0.5f) / rows / 0.45f).coerceIn(0f, 1f)
        val alpha = (t * t * (3f - 2f * t) * 255f).toInt()
        for (x in 0 until cols) {
            val p = tiny.getPixel(x, y)
            out.setPixel(
                x, y,
                android.graphics.Color.argb(
                    alpha,
                    android.graphics.Color.red(p),
                    android.graphics.Color.green(p),
                    android.graphics.Color.blue(p),
                ),
            )
        }
    }
    out.asImageBitmap()
}.getOrNull()

/** The blurred text strip for [art] (null until it is ready, or when there is no artwork). */
@Composable
fun rememberArtStrip(art: ImageBitmap?): ImageBitmap? {
    val id = art?.let { System.identityHashCode(it) }
    var strip by remember(id) { mutableStateOf(id?.let { stripCache.get(it) }) }
    LaunchedEffect(id) {
        if (art == null || id == null) {
            strip = null
        } else if (strip == null) {
            strip = withContext(Dispatchers.Default) { buildStrip(art) }?.also { stripCache.put(id, it) }
        }
    }
    return strip
}
