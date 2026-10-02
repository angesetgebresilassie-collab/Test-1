package com.test1.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** Real Liquid Glass: samples the layer backdrop, blurs, saturates and refracts at the edges. */
@Composable
fun GlassSurface(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    strong: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedCornerShape(corner) },
            effects = {
                vibrancy()
                blur(if (strong) 10.dp.toPx() else 6.dp.toPx())
                lens(
                    if (strong) 16.dp.toPx() else 8.dp.toPx(),
                    if (strong) 32.dp.toPx() else 16.dp.toPx(),
                )
            },
            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) },
        ),
        content = content,
    )
}

@Composable
fun ArtworkImage(bitmap: ImageBitmap?, size: Dp, corner: Dp = 14.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** Full-screen backdrop the glass refracts: current artwork (or a gradient) under a dark scrim. */
@Composable
fun AmbientBackground(art: ImageBitmap?) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1F3B), Color(0xFF090A0F))))
    ) {
        Crossfade(targetState = art, label = "ambient") { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.75f))))
        )
    }
}

@Composable
fun Home(songs: List<Song>, st: PlayerState, vm: PlayerViewModel) {
    val backdrop = rememberLayerBackdrop()
    val currentArt = rememberArtwork(st.current, st.metadata?.artworkUrl)
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
            AmbientBackground(currentArt)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text("Your Music", style = MaterialTheme.typography.headlineLarge)
                    Text("${songs.size} songs", color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(6.dp))
                }
            }
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    backdrop = backdrop,
                    isCurrent = st.current?.id == song.id,
                    onClick = { vm.play(song) },
                )
            }
        }

        st.current?.let { song ->
            NowPlayingBar(
                song = song,
                st = st,
                backdrop = backdrop,
                vm = vm,
                onOpen = { expanded = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded && st.current != null,
            enter = slideInVertically(tween(450)) { it } + fadeIn(tween(450)),
            exit = slideOutVertically(tween(350)) { it } + fadeOut(tween(350)),
        ) {
            st.current?.let { song ->
                NowPlayingScreen(song, st, vm) { expanded = false }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, backdrop: LayerBackdrop, isCurrent: Boolean, onClick: () -> Unit) {
    val art = rememberArtwork(song, null)
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        corner = 20.dp,
        strong = false,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(art, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.92f),
                )
                Text(
                    song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    song: Song,
    st: PlayerState,
    backdrop: LayerBackdrop,
    vm: PlayerViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val art = rememberArtwork(song, st.metadata?.artworkUrl)
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        corner = 32.dp,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(art, 64.dp, 20.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    st.metadata?.title ?: song.title,
                    style = MaterialTheme.typography.titleMedium,
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
            IconButton(onClick = vm::toggle) {
                Icon(
                    painter = painterResource(
                        if (st.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    ),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}
