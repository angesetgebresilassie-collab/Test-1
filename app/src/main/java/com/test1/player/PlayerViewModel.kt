package com.test1.player

import android.app.Application
import android.content.ComponentName
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

data class PlayerState(
    val current: Song? = null,
    val metadata: CachedMetadata? = null,
    val playing: Boolean = false,
)

class PlayerViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = LibraryRepository(app.contentResolver)
    private val meta = MetadataRepository(MetadataCache(app))
    private val store = PlaylistStore(app)

    val songs = MutableStateFlow<List<Song>>(emptyList())
    val loaded = MutableStateFlow(false)
    val scanning = MutableStateFlow(false)
    val playlists = MutableStateFlow(store.load())

    private val _state = MutableStateFlow(PlayerState())
    val state = _state.asStateFlow()

    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()

    init { load() }

    // ---- connection (async: blocking get() on the main thread can deadlock the app) ----

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val future = MediaController.Builder(
            app,
            SessionToken(app, ComponentName(app, PlaybackService::class.java)),
        ).buildAsync()
        future.addListener({
            connecting = false
            val mc = runCatching { future.get() }.getOrNull()
            if (mc != null) {
                controller = mc
                mc.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.value = state.value.copy(playing = isPlaying)
                    }

                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        songs.value.firstOrNull { it.id.toString() == item?.mediaId }?.let { playMetadata(it) }
                    }
                })
                _state.value = state.value.copy(playing = mc.isPlaying)
                val queued = pending.toList()
                pending.clear()
                queued.forEach { it(mc) }
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val mc = controller
        if (mc != null) {
            block(mc)
        } else {
            pending += block
            connect()
        }
    }

    // ---- library ----

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            if (songs.value.isEmpty()) loaded.value = false
            songs.value = repo.songs()
            loaded.value = true
        }
    }

    /** Asks MediaStore to index audio files in the usual folders, then reloads the library. */
    fun rescan() {
        if (scanning.value) return
        scanning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val extensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "amr", "mka")
            val paths = runCatching {
                listOf(
                    Environment.DIRECTORY_MUSIC,
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_PODCASTS,
                    Environment.DIRECTORY_DOCUMENTS,
                ).flatMap { dir ->
                    Environment.getExternalStoragePublicDirectory(dir)
                        .walkTopDown()
                        .maxDepth(6)
                        .filter { it.isFile && it.extension.lowercase() in extensions }
                        .map { it.absolutePath }
                        .toList()
                }
            }.getOrDefault(emptyList())

            if (paths.isEmpty()) {
                load()
                scanning.value = false
                return@launch
            }
            val remaining = AtomicInteger(paths.size)
            MediaScannerConnection.scanFile(app, paths.toTypedArray(), null) { _, _ ->
                if (remaining.decrementAndGet() == 0) {
                    load()
                    scanning.value = false
                }
            }
        }
    }

    // ---- playback ----

    /** Plays [song] with [queue] as the surrounding queue (so next/previous stay inside a list). */
    fun play(song: Song, queue: List<Song> = songs.value) {
        val list = queue.ifEmpty { listOf(song) }
        val index = list.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        startQueue(list, index)
    }

    fun playAll(queue: List<Song>) {
        if (queue.isNotEmpty()) startQueue(queue, 0)
    }

    fun shuffleAll(queue: List<Song>) {
        if (queue.isNotEmpty()) startQueue(queue.shuffled(), 0)
    }

    private fun startQueue(list: List<Song>, index: Int) {
        playMetadata(list[index])
        withController { mc ->
            mc.setMediaItems(list.map { it.toMediaItem() }, index, 0)
            mc.prepare()
            mc.play()
        }
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun positionMs(): Long = controller?.currentPosition ?: 0L

    private fun playMetadata(s: Song) {
        val cur = state.value
        if (cur.current?.id == s.id && cur.metadata != null) return
        // Show the new song immediately; metadata/lyrics fill in when they arrive.
        _state.value = cur.copy(current = s, metadata = null)
        viewModelScope.launch {
            val m = meta.enrich(s)
            if (state.value.current?.id == s.id) _state.value = state.value.copy(metadata = m)
        }
    }

    // ---- playlists ----

    private fun savePlaylists(list: List<Playlist>) {
        playlists.value = list
        store.save(list)
    }

    fun createPlaylist(name: String, firstSongId: Long?) {
        val n = name.trim().ifBlank { "New Playlist" }
        savePlaylists(playlists.value + Playlist(UUID.randomUUID().toString(), n, listOfNotNull(firstSongId)))
    }

    fun addToPlaylist(playlistId: String, songId: Long) {
        savePlaylists(
            playlists.value.map {
                if (it.id == playlistId && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
            }
        )
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        savePlaylists(
            playlists.value.map {
                if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it
            }
        )
    }

    fun deletePlaylist(playlistId: String) {
        savePlaylists(playlists.value.filterNot { it.id == playlistId })
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
