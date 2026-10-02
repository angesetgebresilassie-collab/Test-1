package com.test1.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun NowPlayingScreen(song: Song, st: PlayerState, vm: PlayerViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)

    val backdrop = rememberLayerBackdrop()
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

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } } // don't let taps fall through to the list
    ) {
        Box(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
            AmbientBackground(art)
        }

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
                GlassIconButton(backdrop, android.R.drawable.ic_menu_close_clear_cancel, 44.dp, onClose)
                GlassPill(backdrop, if (showLyrics) "Player" else "Lyrics") { showLyrics = !showLyrics }
            }

            if (showLyrics) {
                LyricsPage(Modifier.weight(1f), st, song, art, lines, pos, vm)
            } else {
                PlayerPage(Modifier.weight(1f), st, song, art)
            }

            SeekBar(pos, song.durationMs, vm::seekTo)
            Spacer(Modifier.height(12.dp))
            Controls(backdrop, st.playing, vm)
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
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.08f))
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.7f),
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
                    color = Color.White.copy(alpha = 0.7f),
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
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime((shown * durationMs).toLong()), color = Color.White.copy(alpha = 0.6f))
            Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.6f))
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Controls(backdrop: LayerBackdrop, playing: Boolean, vm: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(backdrop, android.R.drawable.ic_media_previous, 60.dp, vm::previous)
        GlassIconButton(
            backdrop,
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            84.dp,
            vm::toggle,
        )
        GlassIconButton(backdrop, android.R.drawable.ic_media_next, 60.dp, vm::next)
    }
}

@Composable
private fun GlassIconButton(backdrop: LayerBackdrop, icon: Int, size: Dp, onClick: () -> Unit) {
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        corner = size / 2,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center),
            tint = Color.White,
        )
    }
}

@Composable
private fun GlassPill(backdrop: LayerBackdrop, label: String, onClick: () -> Unit) {
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        corner = 22.dp,
    ) {
        Text(
            label,
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}
