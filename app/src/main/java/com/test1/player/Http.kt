package com.test1.player

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {
    /** Returns the body, null for 4xx (not found), throws IOException for network / 5xx errors. */
    fun get(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Test1Player/0.1")
        try {
            val code = c.responseCode
            return when {
                code in 200..299 -> c.inputStream.use { it.readBytes() }
                code >= 500 -> throw IOException("HTTP $code")
                else -> null
            }
        } finally {
            c.disconnect()
        }
    }
}
