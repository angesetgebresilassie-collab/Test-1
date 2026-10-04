package com.test1.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.math.roundToInt

private val BaseDark = Color(0xFF040406)

// Dark frosted glass used by the bottom bar, search button and mini player.
private val BarTint = Color(0xFF2B2B30).copy(alpha = 0.42f)
private val BarBlur = 28.dp

private enum class HomeTab(val label: String, val icon: ImageVector) {
    Songs("Songs", Icons.Filled.MusicNote),
    Albums("Albums", Icons.Filled.Album),
    Artists("Artists", Icons.Filled.Person),
    Playlists("Playlists", Icons.Filled.QueueMusic),
}

private data class AlbumItem(val id: Long, val name: String, val artist: String, val songs: List<Song>)
private data class ArtistItem(val name: String, val songs: List<Song>)
private data class DetailData(
    val title: String,
    val subtitle: String,
    val meta: String,
    val songs: List<Song>,
    val playlistId: String?,
    val numbered: Boolean,
)

private sealed interface Detail {
    data class AlbumD(val id: Long) : Detail
    data class ArtistD(val name: String) : Detail
    data class PlaylistD(val id: String) : Detail
}

private sealed interface Dlg {
    data class SongMenu(val song: Song, val playlistId: String?) : Dlg
    data class Pick(val songs: List<Song>) : Dlg
    data class NewPlaylist(val songs: List<Song>) : Dlg
}

private fun metaOf(list: List<Song>): String {
    val minutes = list.sumOf { it.durationMs } / 60_000
    return "${list.size} songs · $minutes min"
}

/** The song to take cover art from: the first one that already has looked-up artwork. */
private fun List<Song>.coverSong(): Song? = firstOrNull { it.artworkUrl != null } ?: firstOrNull()

@Composable
fun Home(vm: PlayerViewModel, granted: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    val rawSongs by vm.songs.collectAsState()
    val found by vm.found.collectAsState()
    val st by vm.state.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val scanning by vm.scanning.collectAsState()

    // Songs as shown in the lists: title / artist / artwork from the file-name lookup once it arrives.
    val songs = remember(rawSongs, found) {
        rawSongs.map { s ->
            val m = found[s.id]
            if (m == null) s else s.copy(
                title = m.title.ifBlank { s.title },
                artist = m.artist.ifBlank { s.artist },
                artworkUrl = m.artworkUrl,
            )
        }
    }

    val backdrop = rememberLayerBackdrop()
    val art = rememberArtwork(st.current, st.metadata?.artworkUrl)
    val accent = remember(art) { art?.accentColor() }
    // The whole UI follows the colour of the track that is playing (blue when nothing is).
    val accentColor by animateColorAsState(
        targetValue = accent ?: LiquidBlue,
        animationSpec = tween(700),
        label = "accent",
    )

    var tab by remember { mutableStateOf(HomeTab.Songs) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    val lastDetail = remember { mutableStateOf<Detail?>(null) }
    if (detail != null) lastDetail.value = detail

    // The UI behind the full-screen player / sheets scales back a little (depth effect).
    val depth by animateFloatAsState(
        targetValue = if ((nowPlayingOpen && st.current != null) || dialog != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "depth",
    )

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

    val bottomPad = if (st.current != null) 280.dp else 140.dp

    fun resolve(d: Detail): DetailData? = when (d) {
        is Detail.AlbumD -> albums.firstOrNull { it.id == d.id }
            ?.let { DetailData(it.name, it.artist, metaOf(it.songs), it.songs, null, true) }
        is Detail.ArtistD -> artists.firstOrNull { it.name == d.name }?.let {
            val albumCount = it.songs.map { s -> s.albumId }.distinct().size
            DetailData(it.name, "$albumCount albums", metaOf(it.songs), it.songs, null, false)
        }
        is Detail.PlaylistD -> playlists.firstOrNull { it.id == d.id }?.let { p ->
            val list = p.songIds.mapNotNull { songById[it] }
            DetailData(p.name, "Playlist", metaOf(list), list, p.id, false)
        }
    }

    fun toggleSearch() {
        searching = !searching
        if (!searching) query = ""
    }

    BackHandler(enabled = detail != null) { detail = null }
    BackHandler(enabled = searching) { toggleSearch() }

    CompositionLocalProvider(LocalAccent provides accentColor) {
        Box(Modifier.fillMaxSize().background(BaseDark)) {
            // Everything except the full-screen player and sheets scales back while those are open.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - 0.07f * depth
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.2f * depth
                    }
            ) {
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
                        onRefresh = vm::rescan,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            !granted -> MessageCard(
                                backdrop,
                                "Allow access to your music",
                                "The player needs permission to read audio files on this device. Nothing leaves your phone.",
                            ) {
                                GlassPill(backdrop, "Allow access", onGrant, Modifier.fillMaxWidth(), style = LiquidStyle.Tinted)
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
                                GlassPill(
                                    backdrop,
                                    "Rescan storage",
                                    { vm.rescan() },
                                    Modifier.fillMaxWidth(),
                                    Icons.Filled.Refresh,
                                    style = LiquidStyle.Tinted,
                                )
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
                                    HomeTab.Albums -> AlbumGrid(shownAlbums, bottomPad) { detail = Detail.AlbumD(it.id) }
                                    HomeTab.Artists -> ArtistList(shownArtists, bottomPad) { detail = Detail.ArtistD(it.name) }
                                    HomeTab.Playlists -> PlaylistList(
                                        playlists = shownPlaylists,
                                        songById = songById,
                                        bottomPad = bottomPad,
                                        onNew = { dialog = Dlg.NewPlaylist(emptyList()) },
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
                            st = st,
                            bottomPad = bottomPad,
                            onBack = { detail = null },
                            onPlay = { vm.play(it, data.songs) },
                            onPlayAll = { vm.playAll(data.songs) },
                            onShuffle = { vm.shuffleAll(data.songs) },
                            onAddAll = { dialog = Dlg.Pick(data.songs) },
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

                // Floating mini player, then the tab bar + search button.
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
                    BottomBar(
                        selected = tab,
                        searching = searching,
                        backdrop = backdrop,
                        onTab = {
                            tab = it
                            detail = null
                        },
                        onSearch = {
                            detail = null
                            toggleSearch()
                        },
                    )
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
}

// ---------------------------------------------------------------------------------------------
// Header, search, bottom bar, mini player
// ---------------------------------------------------------------------------------------------

@Composable
private fun Header(
    title: String,
    backdrop: LayerBackdrop,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
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

/**
 * Liquid glass tab bar: a heavily blurred, dark frosted pill with a lighter glass "lens" under
 * the selected tab that slides between tabs with a spring and swells while you drag it.
 * The selected tab takes the accent colour of the playing track.
 */
@Composable
private fun BottomBar(
    selected: HomeTab,
    searching: Boolean,
    backdrop: LayerBackdrop,
    onTab: (HomeTab) -> Unit,
    onSearch: () -> Unit,
) {
    val accent = LocalAccent.current
    val tabs = HomeTab.values()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(Modifier.weight(1f).height(68.dp)) {
            val inner = 5.dp
            val tabW = (maxWidth - inner * 2) / tabs.size
            val tabWpx = with(LocalDensity.current) { tabW.toPx() }
            val maxPx = tabWpx * (tabs.size - 1)

            var dragging by remember { mutableStateOf(false) }
            var dragPx by remember { mutableFloatStateOf(0f) }
            val animPx by animateFloatAsState(
                targetValue = selected.ordinal * tabWpx,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                label = "tabX",
            )
            val swell by animateFloatAsState(
                targetValue = if (dragging) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
                label = "swell",
            )
            val indicatorPx = if (dragging) dragPx else animPx
            val shown = (indicatorPx / tabWpx + 0.5f).toInt().coerceIn(0, tabs.lastIndex)

            // Blurred frosted bar.
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.fillMaxSize(),
                corner = 34.dp,
                tint = BarTint,
                blurRadius = BarBlur,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(34.dp))
                )
            }

            // Sliding glass lens under the selected tab.
            GlassSurface(
                backdrop = backdrop,
                modifier = Modifier
                    .padding(inner)
                    .offset { IntOffset(indicatorPx.roundToInt(), 0) }
                    .width(tabW)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val s = 1f + 0.10f * swell
                        scaleX = s
                        scaleY = s
                    },
                corner = 29.dp,
                tint = Color.White.copy(alpha = 0.20f + 0.10f * swell),
                blurRadius = 16.dp,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(29.dp))
                )
            }

            // Icons + labels (also handle taps and drag-to-select).
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .pointerInput(selected, tabWpx) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragPx = selected.ordinal * tabWpx
                            },
                            onDragEnd = {
                                val idx = (dragPx / tabWpx + 0.5f).toInt().coerceIn(0, tabs.lastIndex)
                                dragging = false
                                onTab(tabs[idx])
                            },
                            onDragCancel = { dragging = false },
                            onHorizontalDrag = { change, delta ->
                                change.consume()
                                dragPx = (dragPx + delta).coerceIn(0f, maxPx)
                            },
                        )
                    },
            ) {
                tabs.forEachIndexed { i, t ->
                    val color = if (i == shown) accent else Color.White.copy(alpha = 0.62f)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(29.dp))
                            .clickable { onTab(t) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(t.icon, contentDescription = t.label, modifier = Modifier.size(24.dp), tint = color)
                        Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = color)
                    }
                }
            }
        }
        GlassButton(
            backdrop = backdrop,
            size = 68.dp,
            onClick = onSearch,
            tint = if (searching) accent.copy(alpha = 0.35f) else BarTint,
            blurRadius = BarBlur,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            )
            Icon(
                Icons.Filled.Search,
                contentDescription = "Search",
                modifier = Modifier.align(Alignment.Center).size(26.dp),
                tint = accent,
            )
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
        modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp)).clickable(onClick = onOpen),
        corner = 30.dp,
        tint = BarTint,
        blurRadius = BarBlur,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(30.dp))
        )
        Row(
            Modifier.fillMaxSize().padding(start = 9.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkBox(art, Modifier.size(42.dp), 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    st.metadata?.title ?: song.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                )
                Text(
                    st.metadata?.artist ?: song.artist,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            IconButton(onClick = vm::toggle) {
                Icon(
                    if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (st.playing) "Pause" else "Play",
                    modifier = Modifier.size(28.dp),
                    tint = Color.White,
                )
            }
            IconButton(onClick = vm::next) {
                Icon(
                    Icons.Filled.FastForward,
                    contentDescription = "Next",
                    modifier = Modifier.size(28.dp),
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
// Shared pieces: hairline, flat track row
// ---------------------------------------------------------------------------------------------

@Composable
private fun Hairline(start: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = start)
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

/** Flat row with a hairline divider (no glass box per row). */
@Composable
private fun TrackRow(
    song: Song,
    index: Int?,
    subtitle: String?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (index != null) {
                Text(
                    "$index",
                    modifier = Modifier.width(36.dp),
                    fontSize = 15.sp,
                    color = if (isCurrent) accent else Color.White.copy(alpha = 0.55f),
                )
            } else {
                val art = rememberArtwork(song, song.artworkUrl)
                ArtworkBox(art, Modifier.size(48.dp), 8.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) accent else Color.White,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = Color.White.copy(alpha = 0.6f))
            }
        }
        Hairline(if (index != null) 36.dp else 60.dp)
    }
}

// ---------------------------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------------------------

private fun sectionLetter(title: String): String =
    title.trimStart().firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"

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
        contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 4.dp, bottom = bottomPad),
    ) {
        if (songs.isEmpty()) {
            item(key = "empty") { EmptyHint("No matching songs") }
        } else {
            item(key = "controls") {
                Row(
                    Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LiquidButton(
                        backdrop, "Play", onPlayAll, Modifier.weight(1f),
                        icon = Icons.Filled.PlayArrow, style = LiquidStyle.Tinted, height = 48.dp,
                    )
                    LiquidButton(
                        backdrop, "Shuffle", onShuffle, Modifier.weight(1f),
                        icon = Icons.Filled.Shuffle, style = LiquidStyle.Surface, height = 48.dp,
                    )
                }
            }
        }
        sections.forEach { (letter, list) ->
            if (letter.isNotEmpty()) {
                item(key = "header-$letter") {
                    Text(
                        letter,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
            }
            items(list, key = { it.id }) { song ->
                TrackRow(
                    song = song,
                    index = null,
                    subtitle = "${song.artist} · ${song.album}",
                    isCurrent = st.current?.id == song.id,
                    onClick = { onPlay(song) },
                    onMenu = { onMenu(song) },
                )
            }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<AlbumItem>, bottomPad: Dp, onOpen: (AlbumItem) -> Unit) {
    if (albums.isEmpty()) {
        EmptyHint("No matching albums")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            val cover = album.songs.coverSong()
            val art = rememberArtwork(cover, cover?.artworkUrl)
            Column(Modifier.fillMaxWidth().clickable { onOpen(album) }) {
                ArtworkBox(art, Modifier.fillMaxWidth().aspectRatio(1f), 14.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    album.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    album.artist,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<ArtistItem>, bottomPad: Dp, onOpen: (ArtistItem) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomPad),
    ) {
        if (artists.isEmpty()) item(key = "empty") { EmptyHint("No matching artists") }
        items(artists, key = { it.name }) { artist ->
            val cover = artist.songs.coverSong()
            val art = rememberArtwork(cover, cover?.artworkUrl)
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(artist) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkBox(art, Modifier.size(56.dp), 28.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            artist.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${artist.songs.size} songs",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
                Hairline(70.dp)
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    songById: Map<Long, Song>,
    bottomPad: Dp,
    onNew: () -> Unit,
    onOpen: (Playlist) -> Unit,
) {
    val accent = LocalAccent.current
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomPad),
    ) {
        item(key = "new") {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onNew).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = accent)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("New playlist", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = accent)
                }
                Hairline(70.dp)
            }
        }
        if (playlists.isEmpty()) {
            item(key = "empty") {
                EmptyHint("Create a playlist, then add songs from the ⋯ menu on any song.")
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            val cover = playlist.songIds.mapNotNull { songById[it] }.coverSong()
            val art = rememberArtwork(cover, cover?.artworkUrl)
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(playlist) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtworkBox(art, Modifier.size(56.dp), 10.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            playlist.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${playlist.songIds.size} songs",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
                Hairline(70.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Detail page (album / artist / playlist): full-bleed artwork hero
// ---------------------------------------------------------------------------------------------

@Composable
private fun DetailScreen(
    data: DetailData,
    st: PlayerState,
    bottomPad: Dp,
    onBack: () -> Unit,
    onPlay: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddAll: () -> Unit,
    onMenu: (Song) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val cover = data.songs.coverSong()
    val art = rememberArtwork(cover, cover?.artworkUrl)
    val backdrop = rememberLayerBackdrop() // glass on this page refracts the hero artwork
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Box(Modifier.layerBackdrop(backdrop).fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(BaseDark))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .graphicsLayer {
                        // Hero fades out as the list scrolls up over it.
                        alpha = if (listState.firstVisibleItemIndex > 0) {
                            0f
                        } else {
                            (1f - listState.firstVisibleItemScrollOffset / 700f).coerceIn(0f, 1f)
                        }
                    }
            ) {
                if (art != null) {
                    Image(
                        bitmap = art,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color(0xFF1B2030), BaseDark))
                        )
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Black.copy(alpha = 0.30f),
                                0.45f to Color.Transparent,
                                1f to BaseDark,
                            )
                        )
                    )
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 12.dp, bottom = bottomPad),
        ) {
            item(key = "spacer") { Spacer(Modifier.height(290.dp)) }
            item(key = "hero") {
                Column(
                    Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        data.title,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                    )
                    Text(
                        data.subtitle,
                        fontSize = 20.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(data.meta, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(18.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassButton(backdrop, 56.dp, onShuffle) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = "Shuffle",
                                modifier = Modifier.align(Alignment.Center),
                                tint = Color.White,
                            )
                        }
                        LiquidButton(
                            backdrop, "Play", onPlayAll, Modifier.width(150.dp),
                            icon = Icons.Filled.PlayArrow, style = LiquidStyle.Tinted, height = 56.dp,
                        )
                        GlassButton(backdrop, 56.dp, onDelete ?: onAddAll) {
                            Icon(
                                if (onDelete != null) Icons.Filled.Delete else Icons.Filled.Add,
                                contentDescription = if (onDelete != null) "Delete playlist" else "Add to playlist",
                                modifier = Modifier.align(Alignment.Center),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
            if (data.songs.isEmpty()) {
                item(key = "empty") { EmptyHint("No songs yet. Add some from the ⋯ menu on any song.") }
            }
            itemsIndexed(data.songs, key = { _, song -> song.id }) { i, song ->
                TrackRow(
                    song = song,
                    index = if (data.numbered) i + 1 else null,
                    subtitle = if (data.numbered) null else song.artist,
                    isCurrent = st.current?.id == song.id,
                    onClick = { onPlay(song) },
                    onMenu = { onMenu(song) },
                )
            }
        }

        // Floating back button.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            GlassButton(backdrop, 44.dp, onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.align(Alignment.Center),
                    tint = Color.White,
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
            .background(Color.Black.copy(alpha = 0.55f))
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
            tint = Color(0xFF8A8F94).copy(alpha = 0.30f),
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
                        MenuItem("Add to playlist") { onSwitch(Dlg.Pick(listOf(dialog.song))) }
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
                                val already = dialog.songs.all { it.id in p.songIds }
                                MenuItem(if (already) "${p.name}  ✓" else p.name) {
                                    vm.addAllToPlaylist(p.id, dialog.songs.map { it.id })
                                    onDismiss()
                                }
                            }
                        }
                        MenuItem("New playlist…") { onSwitch(Dlg.NewPlaylist(dialog.songs)) }
                    }
                    is Dlg.NewPlaylist -> NewPlaylistContent(dialog.songs, backdrop, vm, onDismiss)
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
private fun NewPlaylistContent(songs: List<Song>, backdrop: LayerBackdrop, vm: PlayerViewModel, onDismiss: () -> Unit) {
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
            vm.createPlaylist(name, songs.map { it.id })
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth(),
        style = LiquidStyle.Tinted,
    )
}
