package com.test1.player

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches Google Sans from Google Fonts at runtime (first launch), caches the .ttf files in the
 * app's private storage, and builds a FontFamily from them. Later launches load from disk, so the
 * font works offline. If nothing can be fetched and nothing is cached, the app keeps the system font.
 */
object AppFont {
    // Tried in order. "Google Sans Flex" is the family published on Google Fonts.
    private val families = listOf("Google Sans Flex", "Google Sans")
    private val weights = listOf(400, 500, 700)

    // An old Android user agent makes the Google Fonts CSS API return plain .ttf links
    // (modern agents get .woff2, which Android can't load).
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; U; Android 4.0; en-us) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"

    suspend fun load(context: Context): FontFamily? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "fonts").apply { mkdirs() }
        for (name in families) {
            val slug = name.replace(' ', '_')
            val fonts = weights.mapNotNull { weight ->
                val file = File(dir, "${slug}_$weight.ttf")
                if (!file.exists() || file.length() == 0L) {
                    runCatching { download(name, weight, file) }
                }
                if (file.exists() && file.length() > 0L) Font(file, FontWeight(weight)) else null
            }
            if (fonts.isNotEmpty()) return@withContext FontFamily(fonts)
        }
        null
    }

    private fun download(family: String, weight: Int, target: File) {
        val css = httpGet(
            "https://fonts.googleapis.com/css2?family=${family.replace(' ', '+')}:wght@$weight"
        ).toString(Charsets.UTF_8)
        val fontUrl = Regex("url\\((https:[^)]+)\\)").find(css)?.groupValues?.get(1)
            ?: error("No font url for $family $weight")
        val bytes = httpGet(fontUrl)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) tmp.delete()
    }

    private fun httpGet(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}

/** Same Material 3 type scale, with every style switched to [family]. */
fun Typography.withFontFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
