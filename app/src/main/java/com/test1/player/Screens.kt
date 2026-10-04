package com.test1.player

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 230.dp)

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
fun ArtworkImage(bitmap: Bitmap?, modifier: Modifier, corner: Dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun SongRow(
    vm: PlayerViewModel,
    song: Song,
    playing: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val thumb by produceState<Bitmap?>(null, song.id) {
        value = vm.artwork.get(song, allowOnline = false)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(thumb, Modifier.size(52.dp), 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                song.title,
                color = if (playing) Color(0xFFFF6B8B) else Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
fun SongsScreen(vm: PlayerViewModel, onAdd: (Song) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Songs")
        if (vm.songs.isEmpty()) {
            EmptyState("No music found on this device")
        } else {
            LazyColumn(contentPadding = ListPadding) {
                itemsIndexed(vm.songs, key = { _, s -> s.id }) { i, s ->
                    SongRow(
                        vm = vm,
                        song = s,
                        playing = vm.current?.id == s.id,
                        onClick = { vm.play(vm.songs, i) },
                    ) {
                        IconButton(onClick = { onAdd(s) }) {
                            Icon(Icons.Rounded.Add, "Add to playlist", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsScreen(
    vm: PlayerViewModel,
    openId: String?,
    onOpen: (String?) -> Unit,
    onNew: () -> Unit,
) {
    val open = openId?.let { id -> vm.playlists.firstOrNull { it.id == id } }

    if (open == null) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Playlists", Modifier.weight(1f))
                IconButton(onClick = onNew) { Icon(Icons.Rounded.Add, "New playlist", tint = Color.White) }
                Box(Modifier.size(8.dp))
            }
            if (vm.playlists.isEmpty()) {
                EmptyState("No playlists yet. Tap + to create one.")
            } else {
                LazyColumn(contentPadding = ListPadding) {
                    items(vm.playlists, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpen(p.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.LibraryMusic, null, tint = Color.White) }
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(p.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${p.songIds.size} songs",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        val songs = open.songIds.mapNotNull { vm.songById(it) }
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onOpen(null) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                ScreenTitle(open.name, Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (songs.isNotEmpty()) {
                    Button(onClick = { vm.play(songs, 0) }) { Text("Play") }
                }
                TextButton(onClick = {
                    vm.deletePlaylist(open.id)
                    onOpen(null)
                }) { Text("Delete playlist") }
            }
            if (songs.isEmpty()) {
                EmptyState("Empty. Add songs from the Songs tab.")
            } else {
                LazyColumn(contentPadding = ListPadding) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                        SongRow(
                            vm = vm,
                            song = s,
                            playing = vm.current?.id == s.id,
                            onClick = { vm.play(songs, i) },
                        ) {
                            IconButton(onClick = { vm.removeFromPlaylist(open.id, s.id) }) {
                                Icon(Icons.Rounded.Delete, "Remove", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}
