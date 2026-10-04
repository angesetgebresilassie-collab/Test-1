package com.test1.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun NowPlayingScreen(vm: PlayerViewModel, art: Bitmap?, onClose: () -> Unit) {
    val song = vm.current ?: return
    val backdrop = rememberLayerBackdrop {
        drawRect(Color(0xFF0B0B10))
        drawContent()
    }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(vm.positionMs()) }
    var dragging by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            pos = vm.positionMs()
            delay(200)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            ArtworkBackdrop(art)
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 68.dp)) {
                if (showLyrics) {
                    LyricsView(vm, song, Modifier.weight(1f).fillMaxWidth())
                } else {
                    Box(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        ArtworkImage(art, Modifier.fillMaxWidth().aspectRatio(1f), 28.dp)
                    }
                }
            }
        }

        Box(
            Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .glass(backdrop, CircleShape)
                .clickable(onClick = onClose)
                .size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = Color.White)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(14.dp)
                .glass(backdrop, RoundedCornerShape(36.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "Lyrics",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (showLyrics) 0.32f else 0.12f))
                        .clickable { showLyrics = !showLyrics }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Slider(
                value = dragging ?: pos.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { vm.seekTo(it.toLong()) }
                    dragging = null
                },
                valueRange = 0f..maxOf(1f, vm.durationMs.toFloat()),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime((dragging ?: pos.toFloat()).toLong()), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Text(formatTime(vm.durationMs), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Box(
                    Modifier
                        .glass(backdrop, CircleShape, Color.White.copy(alpha = 0.22f))
                        .clickable(onClick = vm::togglePlay)
                        .size(68.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Play / pause",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}
