package com.test1.player

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** True for songs that come from NewPipe search (played by resolving a stream, not from a file). */
val Song.isStreamed: Boolean get() = uri.scheme == NewPipeSupport.SCHEME

/** The YouTube video id of a streamed song. */
val Song.videoId: String? get() = if (isStreamed) uri.lastPathSegment else null

/**
 * Everything that talks to NewPipe Extractor: search, and turning a video id into a direct
 * audio URL. Streamed songs use the URI ytstream://stream/<videoId>; PlaybackService resolves
 * that to a real URL right when ExoPlayer loads it, so queues and expiring URLs just work.
 */
object NewPipeSupport {
    const val SCHEME = "ytstream"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private class OkDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val method = request.httpMethod()
            val url = request.url()
            val data = request.dataToSend()
            val body = when {
                data != null -> data.toRequestBody()
                method == "POST" -> ByteArray(0).toRequestBody()
                else -> null
            }
            val builder = okhttp3.Request.Builder()
                .method(method, body)
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
            for ((name, values) in request.headers()) {
                if (values.size > 1) {
                    builder.removeHeader(name)
                    values.forEach { builder.addHeader(name, it) }
                } else if (values.size == 1) {
                    builder.header(name, values[0])
                }
            }
            http.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) throw ReCaptchaException("reCaptcha challenge requested", url)
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string(),
                    response.request.url.toString(),
                )
            }
        }
    }

    @Volatile private var ready = false

    fun init() {
        if (ready) return
        synchronized(this) {
            if (!ready) {
                NewPipe.init(OkDownloader())
                ready = true
            }
        }
    }

    /** Negative so it can never collide with a MediaStore id. */
    private fun idFor(videoId: String): Long = -((videoId.hashCode().toLong() and 0x7fffffffL) + 1)

    private fun videoIdOf(url: String): String? =
        runCatching { YoutubeStreamLinkHandlerFactory.getInstance().getId(url) }.getOrNull()

    private fun StreamInfoItem.toSong(): Song? {
        val vid = videoIdOf(url) ?: return null
        val artist = uploaderName.orEmpty().removeSuffix(" - Topic").ifBlank { "Unknown artist" }
        return Song(
            id = idFor(vid),
            title = name,
            artist = artist,
            album = "",
            albumId = idFor(vid),
            track = 0,
            durationMs = duration.coerceAtLeast(0) * 1000,
            uri = Uri.parse("$SCHEME://stream/$vid"),
            fileName = "$artist - $name",
            artworkUrl = thumbnails.lastOrNull()?.url,
        )
    }

    /** YouTube Music song search. Blocking: call from a background thread. */
    fun search(query: String): List<Song> {
        init()
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            "",
        )
        val extractor = service.getSearchExtractor(handler)
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSong() }
            .take(20)
    }

    fun info(videoId: String): StreamInfo {
        init()
        return StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
    }

    /** Best progressive audio stream; M4A first because it plays and saves everywhere. */
    fun pickAudio(info: StreamInfo): AudioStream? {
        val usable = info.audioStreams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
        }
        val pool = usable.filter { it.format == MediaFormat.M4A }.ifEmpty { usable }
        return pool.maxByOrNull { it.averageBitrate }
    }

    private class Cached(val url: String, val at: Long)

    private val urlCache = ConcurrentHashMap<String, Cached>()
    private const val URL_TTL_MS = 20 * 60 * 1000L

    /** Direct audio URL for [videoId]. Blocking; cached briefly because these URLs expire. */
    fun streamUrl(videoId: String): String {
        val now = System.currentTimeMillis()
        urlCache[videoId]?.takeIf { now - it.at < URL_TTL_MS }?.let { return it.url }
        val stream = pickAudio(info(videoId)) ?: throw IOException("No audio stream found")
        val url = stream.content
        urlCache[videoId] = Cached(url, now)
        return url
    }
}
