package com.test1.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun ArtworkBackdrop(art: Bitmap?) {
    Crossfade(targetState = art, animationSpec = tween(800), label = "bg") { b ->
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(48.dp),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF1B1B2F), Color(0xFF0B0B10)))
                )
            )
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
}

@Composable
fun PlayerApp(vm: PlayerViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<Song?>(null) }
    var showNew by remember { mutableStateOf(false) }

    val current = vm.current
    val art by produceState<Bitmap?>(null, current?.id) {
        value = current?.let { vm.artwork.get(it) }
    }
    val backdrop = rememberLayerBackdrop {
        drawRect(Color(0xFF0B0B10))
        drawContent()
    }

    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
    BackHandler(enabled = !showNowPlaying && openPlaylist != null) { openPlaylist = null }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B10))) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            ArtworkBackdrop(art)
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                when (tab) {
                    0 -> SongsScreen(vm) { addTarget = it }
                    else -> PlaylistsScreen(
                        vm = vm,
                        openId = openPlaylist,
                        onOpen = { openPlaylist = it },
                        onNew = { showNew = true },
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (current != null) MiniPlayer(vm, current, backdrop, art) { showNowPlaying = true }
            GlassTabs(backdrop, tab) {
                tab = it
                openPlaylist = null
            }
        }

        AnimatedVisibility(
            visible = showNowPlaying,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(vm, art) { showNowPlaying = false }
        }
    }

    // Add-to-playlist picker
    addTarget?.let { song ->
        AlertDialog(
            onDismissRequest = { addTarget = null },
            title = { Text("Add to playlist") },
            text = {
                LazyColumn {
                    items(vm.playlists, key = { it.id }) { p ->
                        Text(
                            p.name,
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.addToPlaylist(p.id, song.id)
                                    addTarget = null
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNew = true }) { Text("New playlist") } },
            dismissButton = { TextButton(onClick = { addTarget = null }) { Text("Cancel") } },
        )
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = vm.createPlaylist(name)
                    addTarget?.let { vm.addToPlaylist(p.id, it.id) }
                    addTarget = null
                    showNew = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MiniPlayer(
    vm: PlayerViewModel,
    song: Song,
    backdrop: Backdrop,
    art: Bitmap?,
    onOpen: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(backdrop, RoundedCornerShape(28.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(art, Modifier.size(48.dp), 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                song.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = vm::togglePlay) {
            Icon(
                if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play / pause",
                tint = Color.White,
            )
        }
        IconButton(onClick = vm::next) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White)
        }
    }
}

@Composable
private fun GlassTabs(backdrop: Backdrop, selected: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf("Songs" to Icons.Rounded.MusicNote, "Playlists" to Icons.Rounded.LibraryMusic)
    Row(
        Modifier
            .fillMaxWidth()
            .glass(backdrop, CircleShape)
            .height(64.dp)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { i, (label, icon) ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (selected == i) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, null, tint = Color.White)
                    Text(label, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
