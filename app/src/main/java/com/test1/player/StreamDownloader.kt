package com.test1.player

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Tap-to-download for streamed songs. The audio is saved into Music/Test1 through MediaStore
 * (no storage permission needed), so once it finishes the library picks it up like any other
 * local file. Files are named "Title - Artist". Progress is 0..1, or -1 when the size is unknown.
 */
object StreamDownloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val progress: StateFlow<Map<Long, Float>> = _progress.asStateFlow()

    /** Streamed songs downloaded during this session (so the UI can show a check mark). */
    private val _done = MutableStateFlow<Set<Long>>(emptySet())
    val done: StateFlow<Set<Long>> = _done.asStateFlow()

    fun start(context: Context, song: Song, onDone: () -> Unit) {
        if (song.id in _progress.value || song.id in _done.value) return
        val app = context.applicationContext
        _progress.value = _progress.value + (song.id to 0f)
        scope.launch {
            val ok = runCatching { run(app, song) }.isSuccess
            if (ok) _done.value = _done.value + song.id
            _progress.value = _progress.value - song.id
            if (ok) onDone()
        }
    }

    private fun setProgress(id: Long, value: Float) {
        _progress.value = _progress.value + (id to value)
    }

    private fun run(context: Context, song: Song) {
        val videoId = song.videoId ?: throw IOException("Not a streamed song")
        val stream = NewPipeSupport.pickAudio(NewPipeSupport.info(videoId))
            ?: throw IOException("No audio stream found")
        val ext = stream.format?.suffix ?: "m4a"
        val mime = stream.format?.mimeType ?: "audio/mp4"
        val name = "${song.title} - ${song.artist}".replace(Regex("""[\\/:*?"<>|]"""), "").trim()

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.$ext")
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.TITLE, song.title)
            put(MediaStore.Audio.Media.ARTIST, song.artist)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Test1")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create file")
        try {
            val request = okhttp3.Request.Builder()
                .url(stream.content)
                .header("User-Agent", NewPipeSupport.USER_AGENT)
                .build()
            NewPipeSupport.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                val total = body.contentLength()
                val out = resolver.openOutputStream(target) ?: throw IOException("Cannot write file")
                out.use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = -1
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            sink.write(buffer, 0, n)
                            done += n
                            val pct = if (total > 0) (done * 100 / total).toInt() else -1
                            if (pct != lastReported) {
                                lastReported = pct
                                setProgress(song.id, if (pct < 0) -1f else pct / 100f)
                            }
                        }
                    }
                }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
    }
}
