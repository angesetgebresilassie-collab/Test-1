package com.test1.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private enum class HomeTab(val label: String) {
    Songs("Songs"),
    Albums("Albums"),
    Artists("Artists"),
    Playlists("Playlists"),
}

private data class AlbumItem(val id: Long, val name: String, val artist: String, val songs: List<Song>)
private data class ArtistItem(val name: String, val songs: List<Song>)
private data class DetailData(val title: String, val subtitle: String, val songs: List<Song>, val playlistId: String?)

private sealed interface Detail {
    data class AlbumD(val id: Long) : Detail
    data class ArtistD(val name: String) : Detail
    data class PlaylistD(val id: String) : Detail
}

private sealed interface Dlg {
    data class SongMenu(val song: Song, val playlistId: String?) : Dlg
    data class Pick(val song: Song) : Dlg
    data class NewPlaylist(val song: Song?) : Dlg
}

@Composable
fun Home(vm: PlayerViewModel, granted: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    val songs by vm.songs.collectAsState()
    val st by vm.state.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val scanning by vm.scanning.collectAsState()

    val backdrop = rememberLayerBackdrop()
    val art = rememberArtwork(st.current, st.metadata?.artworkUrl)
    val accent = remember(art) { art?.accentColor() }

    var tab by remember { mutableStateOf(HomeTab.Songs) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    val lastDetail = remember { mutableStateOf<Detail?>(null) }
    if (detail != null) lastDetail.value = detail

    val q = query.trim()
    val albums = remember(songs) {
        songs.groupBy { it.albumId }.map { (id, list) ->
            AlbumItem(id, list.first().album, list.first().artist, list.sortedBy { it.track % 1000 })
        }.sortedBy { it.name.lowercase() }
    }
    val artists = remember(songs) {
        songs.groupBy { it.artist }.map { (name, list) -> ArtistItem(name, list) }.sortedBy { it.name.lowercase() }
    }
    val songById = remember(songs) { songs.associateBy { it.id } }

    val shownSongs = remember(songs, q) {
        if (q.isEmpty()) songs
        else songs.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
    }
    val shownAlbums = remember(albums, q) {
        if (q.isEmpty()) albums else albums.filter { it.name.contains(q, true) || it.artist.contains(q, true) }
    }
    val shownArtists = remember(artists, q) {
        if (q.isEmpty()) artists else artists.filter { it.name.contains(q, true) }
    }
    val shownPlaylists = remember(playlists, q) {
        if (q.isEmpty()) playlists else playlists.filter { it.name.contains(q, true) }
    }

    val bottomPad = if (st.current != null) 240.dp else 130.dp

    fun resolve(d: Detail): DetailData? = when (d) {
        is Detail.AlbumD -> albums.firstOrNull { it.id == d.id }
            ?.let { DetailData(it.name, it.artist, it.songs, null) }
        is Detail.ArtistD -> artists.firstOrNull { it.name == d.name }
            ?.let { DetailData(it.name, "${it.songs.size} songs", it.songs, null) }
        is Detail.PlaylistD -> playlists.firstOrNull { it.id == d.id }?.let { p ->
            DetailData(p.name, "${p.songIds.size} songs", p.songIds.mapNotNull { songById[it] }, p.id)
        }
    }

    BackHandler(enabled = detail != null) { detail = null }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    Box(Modifier.fillMaxSize()) {
        // Everything the glass refracts lives in this layer.
        Box(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
            LiquidBackground(accent, art)
        }

        // Main content (kept composed but hidden while a detail page is open, so scroll positions survive).
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .graphicsLayer { alpha = if (detail != null) 0f else 1f }
        ) {
            Header(
                title = tab.label,
                backdrop = backdrop,
                searching = searching,
                query = query,
                onQuery = { query = it },
                onSearch = {
                    searching = !searching
                    if (!searching) query = ""
                },
                onRefresh = vm::rescan,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !granted -> MessageCard(
                        backdrop,
                        "Allow access to your music",
                        "The player needs permission to read audio files on this device. Nothing leaves your phone.",
                    ) {
                        GlassPill(backdrop, "Allow access", onGrant, Modifier.fillMaxWidth())
                        GlassPill(backdrop, "Open app settings", onOpenSettings, Modifier.fillMaxWidth())
                    }
                    !loaded || (scanning && songs.isEmpty()) -> MessageCard(
                        backdrop,
                        "Looking for your music…",
                        "Reading your device's media library.",
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                    songs.isEmpty() -> MessageCard(
                        backdrop,
                        "No music found",
                        "Nothing playable showed up in the media library. If you just copied songs onto the phone, tap rescan so Android indexes them.",
                    ) {
                        GlassPill(backdrop, "Rescan storage", { vm.rescan() }, Modifier.fillMaxWidth(), Icons.Filled.Refresh)
                    }
                    else -> Crossfade(targetState = tab, label = "tab") { t ->
                        when (t) {
                            HomeTab.Songs -> SongList(
                                songs = shownSongs,
                                grouped = q.isEmpty(),
                                backdrop = backdrop,
                                st = st,
                                bottomPad = bottomPad,
                                onPlay = { vm.play(it, shownSongs) },
                                onPlayAll = { vm.playAll(shownSongs) },
                                onShuffle = { vm.shuffleAll(shownSongs) },
                                onMenu = { dialog = Dlg.SongMenu(it, null) },
                            )
                            HomeTab.Albums -> AlbumGrid(shownAlbums, backdrop, bottomPad) { detail = Detail.AlbumD(it.id) }
                            HomeTab.Artists -> ArtistList(shownArtists, backdrop, bottomPad) { detail = Detail.ArtistD(it.name) }
                            HomeTab.Playlists -> PlaylistList(
                                playlists = shownPlaylists,
                                songById = songById,
                                backdrop = backdrop,
                                bottomPad = bottomPad,
                                onNew = { dialog = Dlg.NewPlaylist(null) },
                                onOpen = { detail = Detail.PlaylistD(it.id) },
                            )
                        }
                    }
                }
            }
        }

        // Album / artist / playlist page.
        AnimatedVisibility(
            visible = detail != null,
            enter = fadeIn(tween(250)) + slideInVertically(tween(350)) { it / 8 },
            exit = fadeOut(tween(200)),
        ) {
            val data = lastDetail.value?.let { resolve(it) }
            if (data != null) {
                DetailScreen(
                    data = data,
                    backdrop = backdrop,
                    st = st,
                    bottomPad = bottomPad,
                    onBack = { detail = null },
                    onPlay = { vm.play(it, data.songs) },
                    onPlayAll = { vm.playAll(data.songs) },
                    onShuffle = { vm.shuffleAll(data.songs) },
                    onMenu = { dialog = Dlg.SongMenu(it, data.playlistId) },
                    onDelete = data.playlistId?.let { id ->
                        {
                            vm.deletePlaylist(id)
                            detail = null
                        }
                    },
                )
            }
        }

        // Mini player + tab bar.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            st.current?.let { song ->
                MiniPlayer(song, st, backdrop, vm) { nowPlayingOpen = true }
            }
            TabBar(tab, backdrop) {
                tab = it
                detail = null
            }
        }

        // Full-screen player.
        AnimatedVisibility(
            visible = nowPlayingOpen && st.current != null,
            enter = slideInVertically(tween(450)) { it } + fadeIn(tween(450)),
            exit = slideOutVertically(tween(350)) { it } + fadeOut(tween(350)),
        ) {
            st.current?.let { song ->
                NowPlayingScreen(song, st, vm) { nowPlayingOpen = false }
            }
        }

        // Sheets: song menu, playlist picker, new playlist.
        dialog?.let { d ->
            DialogHost(
                dialog = d,
                backdrop = backdrop,
                playlists = playlists,
                vm = vm,
                onDismiss = { dialog = null },
                onSwitch = { dialog = it },
            )
            BackHandler { dialog = null }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Header, search, tab bar, mini player
// ---------------------------------------------------------------------------------------------

@Composable
private fun Header(
    title: String,
    backdrop: LayerBackdrop,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            GlassButton(backdrop, 44.dp, onRefresh) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Rescan",
                    modifier = Modifier.align(Alignment.Center).size(22.dp),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(10.dp))
            GlassButton(backdrop, 44.dp, onSearch) {
                Icon(
                    if (searching) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = "Search",
                    modifier = Modifier.align(Alignment.Center).size(22.dp),
                    tint = Color.White,
                )
            }
        }
        AnimatedVisibility(visible = searching) {
            SearchField(backdrop, query, onQuery)
        }
    }
}

@Composable
private fun SearchField(backdrop: LayerBackdrop, query: String, onQuery: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp),
        corner = 24.dp,
        strong = false,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .focusRequester(focus),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Search your music", color = Color.White.copy(alpha = 0.5f), fontSize = 17.sp)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun TabBar(selected: HomeTab, backdrop: LayerBackdrop, onSelect: (HomeTab) -> Unit) {
    GlassSurface(backdrop, Modifier.fillMaxWidth(), corner = 32.dp) {
        Row(Modifier.padding(6.dp)) {
            HomeTab.values().forEach { t ->
                val isSelected = t == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (isSelected) Color.White.copy(alpha = 0.20f) else Color.Transparent)
                        .clickable { onSelect(t) }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    st: PlayerState,
    backdrop: LayerBackdrop,
    vm: PlayerViewModel,
    onOpen: () -> Unit,
) {
    val art = rememberArtwork(song, st.metadata?.artworkUrl)
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).clickable(onClick = onOpen),
        corner = 32.dp,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkBox(art, Modifier.size(56.dp), 18.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    st.metadata?.title ?: song.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    st.metadata?.artist ?: song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }
            IconButton(onClick = vm::toggle) {
                Icon(
                    painter = painterResource(
                        if (st.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    ),
                    contentDescription = if (st.playing) "Pause" else "Play",
                    tint = Color.White,
                )
            }
            IconButton(onClick = vm::next) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_media_next),
                    contentDescription = "Next",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun MessageCard(
    backdrop: LayerBackdrop,
    title: String,
    body: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassSurface(backdrop, Modifier.fillMaxWidth(), corner = 32.dp) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(body, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                actions()
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        textAlign = TextAlign.Center,
        color = Color.White.copy(alpha = 0.6f),
    )
}

// ---------------------------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------------------------

private fun sectionLetter(title: String): String =
    title.trimStart().firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"

@Composable
private fun PlayShuffleRow(backdrop: LayerBackdrop, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassPill(backdrop, "Play", onPlay, Modifier.weight(1f), Icons.Filled.PlayArrow)
        GlassPill(backdrop, "Shuffle", onShuffle, Modifier.weight(1f))
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    grouped: Boolean,
    backdrop: LayerBackdrop,
    st: PlayerState,
    bottomPad: Dp,
    onPlay: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onMenu: (Song) -> Unit,
) {
    val sections = remember(songs, grouped) {
        if (grouped) songs.groupBy { sectionLetter(it.title) }.toList() else listOf("" to songs)
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (songs.isEmpty()) {
            item(key = "empty") { EmptyHint("No matching songs") }
        } else {
            item(key = "controls") { PlayShuffleRow(backdrop, onPlayAll, onShuffle) }
        }
        sections.forEach { (letter, list) ->
            if (letter.isNotEmpty()) {
                item(key = "header-$letter") {
                    Text(
                        letter,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 6.dp, top = 10.dp),
                    )
                }
            }
            items(list, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    backdrop = backdrop,
                    isCurrent = st.current?.id == song.id,
                    playing = st.playing,
                    onClick = { onPlay(song) },
                    onMenu = { onMenu(song) },
                )
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    backdrop: LayerBackdrop,
    isCurrent: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val art = rememberArtwork(song, null)
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        corner = 22.dp,
        strong = false,
    ) {
        Row(
            Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkBox(art, Modifier.size(52.dp), 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontSize = 16.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
            if (isCurrent && playing) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Playing",
                    modifier = Modifier.size(20.dp),
                    tint = Color.White.copy(alpha = 0.9f),
                )
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<AlbumItem>,
    backdrop: LayerBackdrop,
    bottomPad: Dp,
    onOpen: (AlbumItem) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyHint("No matching albums")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            val art = rememberArtwork(album.songs.first(), null)
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable { onOpen(album) },
                corner = 26.dp,
                strong = false,
            ) {
                Column(Modifier.padding(10.dp)) {
                    ArtworkBox(art, Modifier.fillMaxWidth().aspectRatio(1f), 18.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        album.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        album.artist,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistList(
    artists: List<ArtistItem>,
    backdrop: LayerBackdrop,
    bottomPad: Dp,
    onOpen: (ArtistItem) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (artists.isEmpty()) item(key = "empty") { EmptyHint("No matching artists") }
        items(artists, key = { it.name }) { artist ->
            val art = rememberArtwork(artist.songs.first(), null)
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { onOpen(artist) },
                corner = 22.dp,
                strong = false,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtworkBox(art, Modifier.size(56.dp), 28.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            artist.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${artist.songs.size} songs",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    songById: Map<Long, Song>,
    backdrop: LayerBackdrop,
    bottomPad: Dp,
    onNew: () -> Unit,
    onOpen: (Playlist) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "new") {
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onNew),
                corner = 22.dp,
                strong = false,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text("New playlist", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (playlists.isEmpty()) {
            item(key = "empty") {
                EmptyHint("Create a playlist, then add songs from the ⋯ menu on any song.")
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            val first = playlist.songIds.firstNotNullOfOrNull { songById[it] }
            val art = rememberArtwork(first, null)
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { onOpen(playlist) },
                corner = 22.dp,
                strong = false,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtworkBox(art, Modifier.size(56.dp), 16.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            playlist.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${playlist.songIds.size} songs",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Detail page (album / artist / playlist)
// ---------------------------------------------------------------------------------------------

@Composable
private fun DetailScreen(
    data: DetailData,
    backdrop: LayerBackdrop,
    st: PlayerState,
    bottomPad: Dp,
    onBack: () -> Unit,
    onPlay: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onMenu: (Song) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val art = rememberArtwork(data.songs.firstOrNull(), null)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "top") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GlassButton(backdrop, 44.dp, onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.align(Alignment.Center),
                            tint = Color.White,
                        )
                    }
                    if (onDelete != null) {
                        GlassPill(backdrop, "Delete", onDelete, icon = Icons.Filled.Delete, height = 44.dp)
                    }
                }
            }
            item(key = "hero") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtworkBox(art, Modifier.size(220.dp), 32.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        data.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(data.subtitle, color = Color.White.copy(alpha = 0.65f))
                    Spacer(Modifier.height(16.dp))
                    PlayShuffleRow(backdrop, onPlayAll, onShuffle)
                }
            }
            if (data.songs.isEmpty()) {
                item(key = "empty") { EmptyHint("No songs yet. Add some from the ⋯ menu on any song.") }
            }
            items(data.songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    backdrop = backdrop,
                    isCurrent = st.current?.id == song.id,
                    playing = st.playing,
                    onClick = { onPlay(song) },
                    onMenu = { onMenu(song) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------------------------

@Composable
private fun DialogHost(
    dialog: Dlg,
    backdrop: LayerBackdrop,
    playlists: List<Playlist>,
    vm: PlayerViewModel,
    onDismiss: () -> Unit,
    onSwitch: (Dlg) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        GlassSurface(
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            corner = 32.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                when (dialog) {
                    is Dlg.SongMenu -> {
                        Text(
                            dialog.song.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            dialog.song.artist,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(12.dp))
                        MenuItem("Add to playlist") { onSwitch(Dlg.Pick(dialog.song)) }
                        if (dialog.playlistId != null) {
                            MenuItem("Remove from this playlist", destructive = true) {
                                vm.removeFromPlaylist(dialog.playlistId, dialog.song.id)
                                onDismiss()
                            }
                        }
                    }
                    is Dlg.Pick -> {
                        Text("Add to playlist", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            playlists.forEach { p ->
                                val already = dialog.song.id in p.songIds
                                MenuItem(if (already) "${p.name}  ✓" else p.name) {
                                    vm.addToPlaylist(p.id, dialog.song.id)
                                    onDismiss()
                                }
                            }
                        }
                        MenuItem("New playlist…") { onSwitch(Dlg.NewPlaylist(dialog.song)) }
                    }
                    is Dlg.NewPlaylist -> NewPlaylistContent(dialog.song, backdrop, vm, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = if (destructive) Color(0xFFFF6B6B) else Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
    )
}

@Composable
private fun NewPlaylistContent(song: Song?, backdrop: LayerBackdrop, vm: PlayerViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Text("New playlist", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        corner = 26.dp,
        strong = false,
    ) {
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .focusRequester(focus),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (name.isEmpty()) {
                        Text("Playlist name", color = Color.White.copy(alpha = 0.5f), fontSize = 17.sp)
                    }
                    inner()
                }
            },
        )
    }
    Spacer(Modifier.height(14.dp))
    GlassPill(
        backdrop = backdrop,
        label = "Create",
        onClick = {
            vm.createPlaylist(name, song?.id)
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
