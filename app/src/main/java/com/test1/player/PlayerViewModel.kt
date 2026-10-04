package com.test1.player

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    val artwork = ArtworkRepository(app)
    val lyricsRepo = LyricsRepository(app)
    private val playlistStore = PlaylistStore(app)

    var songs by mutableStateOf<List<Song>>(emptyList())
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var current by mutableStateOf<Song?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    private val songMap: Map<Long, Song> by derivedStateOf { songs.associateBy { it.id } }
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                sync(c)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        val d = p.duration
        durationMs = if (d > 0) d else 0L
        current = p.currentMediaItem?.mediaId?.toLongOrNull()?.let { songMap[it] }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            songs = MusicRepository.loadSongs(getApplication())
            playlists = withContext(Dispatchers.IO) { playlistStore.load() }
            controller?.let { sync(it) }
        }
    }

    fun songById(id: Long): Song? = songMap[id]

    // --- playback ---
    fun play(queue: List<Song>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    // --- playlists ---
    fun createPlaylist(name: String): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifBlank { "New playlist" }, emptyList())
        playlists = playlists + p
        persist()
        return p
    }

    fun addToPlaylist(playlistId: String, songId: Long) {
        playlists = playlists.map {
            if (it.id == playlistId && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
        }
        persist()
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        playlists = playlists.map {
            if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it
        }
        persist()
    }

    fun deletePlaylist(playlistId: String) {
        playlists = playlists.filterNot { it.id == playlistId }
        persist()
    }

    private fun persist() {
        val snapshot = playlists
        viewModelScope.launch(Dispatchers.IO) { playlistStore.save(snapshot) }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
