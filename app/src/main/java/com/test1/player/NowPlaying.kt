package com.test1.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun NowPlayingScreen(song: Song, st: PlayerState, vm: PlayerViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)

    val art = rememberArtwork(song, st.metadata?.artworkUrl)
    val pos = remember { mutableLongStateOf(0L) }
    var showLyrics by remember { mutableStateOf(false) }
    val lines = remember(st.metadata?.synced) { parseLrc(st.metadata?.synced) }

    // Frame-synced playback position; only the views that read it recompose.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            pos.longValue = vm.positionMs()
        }
    }

    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            // Material surface washed with the colour of the playing track.
            .background(
                Brush.verticalGradient(
                    listOf(colors.primaryContainer, colors.surfaceContainer, colors.background)
                )
            )
            .pointerInput(Unit) { detectTapGestures { } } // don't let taps fall through to the list
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilledTonalIconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close")
                }
                FilledTonalButton(onClick = { showLyrics = !showLyrics }) {
                    Text(if (showLyrics) "Player" else "Lyrics")
                }
            }

            if (showLyrics) {
                LyricsPage(Modifier.weight(1f), st, song, art, lines, pos, vm)
            } else {
                PlayerPage(Modifier.weight(1f), st, song, art)
            }

            SeekBar(pos, song.durationMs, vm::seekTo)
            Spacer(Modifier.height(12.dp))
            Controls(st.playing, vm)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlayerPage(modifier: Modifier, st: PlayerState, song: Song, art: ImageBitmap?) {
    // Artwork "breathes": full size while playing, shrinks when paused.
    val scale by animateFloatAsState(
        targetValue = if (st.playing) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "artScale",
    )
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 12.dp,
        ) {
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            st.metadata?.title ?: song.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            st.metadata?.artist ?: song.artist,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LyricsPage(
    modifier: Modifier,
    st: PlayerState,
    song: Song,
    art: ImageBitmap?,
    lines: List<LyricLine>,
    pos: LongState,
    vm: PlayerViewModel,
) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(art, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    st.metadata?.title ?: song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    st.metadata?.artist ?: song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LyricsView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            lines = lines,
            plain = st.metadata?.lyrics,
            pos = pos,
            onSeek = vm::seekTo,
        )
    }
}

@Composable
private fun SeekBar(pos: LongState, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val frac = if (durationMs > 0) (pos.longValue.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = if (dragging) dragValue else frac

    Column(Modifier.fillMaxWidth()) {
        // Default Material 3 slider: primary (the track's accent colour) thumb and active track.
        Slider(
            value = shown,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek((dragValue * durationMs).toLong())
                dragging = false
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatTime((shown * durationMs).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatTime(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Controls(playing: Boolean, vm: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = vm::previous, modifier = Modifier.size(60.dp)) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                modifier = Modifier.size(34.dp),
            )
        }
        FilledIconButton(onClick = vm::toggle, modifier = Modifier.size(84.dp)) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                modifier = Modifier.size(48.dp),
            )
        }
        FilledTonalIconButton(onClick = vm::next, modifier = Modifier.size(60.dp)) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "Next",
                modifier = Modifier.size(34.dp),
            )
        }
    }
}
