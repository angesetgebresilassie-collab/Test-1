package com.test1.player

import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val shuffleCommand = SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY)

    private fun shuffleButton(on: Boolean): CommandButton =
        CommandButton.Builder(if (on) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName("Shuffle")
            .setSessionCommand(shuffleCommand)
            .build()

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(shuffleCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_SHUFFLE) {
                val p = session.player
                p.shuffleModeEnabled = !p.shuffleModeEnabled
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true, // pause for other audio / calls, resume afterwards
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .build()
        player = exoPlayer

        val session = MediaSession.Builder(this, exoPlayer)
            .setCallback(Callback())
            .build()
        // Shuffle button in the notification; its icon follows the shuffle state.
        session.setCustomLayout(ImmutableList.of(shuffleButton(exoPlayer.shuffleModeEnabled)))
        mediaSession = session

        exoPlayer.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton(shuffleModeEnabled)))
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private companion object {
        const val ACTION_SHUFFLE = "com.test1.player.SHUFFLE"
    }
}
