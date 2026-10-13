package com.test1.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample

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
    data class RenamePlaylist(val id: String, val name: String) : Dlg
}

private fun metaOf(list: List<Song>): String {
    val minutes = list.sumOf { it.durationMs } / 60_000
    return "${list.size} songs · $minutes min"
}

/** The song to take cover art from: the first one that already has looked-up artwork. */
private fun List<Song>.coverSong(): Song? = firstOrNull { it.artworkUrl != null } ?: firstOrNull()

/** Material 3 dark scheme whose primary / containers follow the colour of the playing track. */
private fun musicScheme(accent: Color): ColorScheme {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val container = Color(0xFF26272C)
    return darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerp(container, accent, 0.38f),
        onPrimaryContainer = Color.White,
        secondaryContainer = lerp(container, accent, 0.24f),
        onSecondaryContainer = Color.White,
        background = Color(0xFF101114),
        onBackground = Color(0xFFE6E6EA),
        surface = Color(0xFF101114),
        onSurface = Color(0xFFE6E6EA),
        surfaceVariant = Color(0xFF2A2B30),
        onSurfaceVariant = Color(0xFFB3B5BD),
        surfaceContainerLow = Color(0xFF16171A),
        surfaceContainer = Color(0xFF1B1C20),
        surfaceContainerHigh = Color(0xFF232429),
        surfaceContainerHighest = Color(0xFF2D2E34),
        outline = Color(0xFF8A8C94),
        outlineVariant = Color(0xFF3A3B41),
    )
}

@OptIn(FlowPreview::class)
@Composable
fun Home(vm: PlayerViewModel, granted: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    val rawSongs by vm.songs.collectAsState()
    // The background lookup publishes a new map every couple of seconds; sampling it keeps the
    // lists from being rebuilt and recomposed on every single result while you scroll.
    val found by remember(vm) { vm.found.sample(1200) }.collectAsState(initial = vm.found.value)
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

    // Blur source for the floating nav bar, and its scroll-aware label state.
    val hazeState = rememberHazeState()
    val navScroll = rememberNuvioNavBarScrollState()
    // Small decode + off-main-thread colour extraction: changing song no longer stalls the UI.
    val art = rememberArtwork(st.current, st.metadata?.artworkUrl, 96)
    val accent = rememberArtAccent(art)
    // The whole UI follows the colour of the track that is playing (blue when nothing is).
    // Not animated here on purpose: animating it would recompose the entire screen every frame.
    // The nav bar and mini player fade the colour locally instead.
    val accentColor = accent ?: LiquidBlue
    val scheme = remember(accentColor) { musicScheme(accentColor) }

    var tab by remember { mutableStateOf(HomeTab.Songs) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var dialog by remember { mutableStateOf<Dlg?>(null) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    val lastDetail = remember { mutableStateOf<Detail?>(null) }
    if (detail != null) lastDetail.value = detail
    val lastSong = remember { mutableStateOf<Song?>(null) }
    st.current?.let { lastSong.value = it }

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

    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalAccent provides accentColor) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    // Everything the floating nav bar blurs (background, lists, detail page) lives in
                    // this box; it also feeds the bar's scroll-aware labels.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState)
                            .nestedScroll(navScroll.nestedScrollConnection)
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

                        // Main content (kept composed but hidden while a detail page is open, so scroll positions survive).
                        Column(
                            Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .graphicsLayer { alpha = if (detail != null) 0f else 1f }
                        ) {
                            Header(
                                title = if (searching) "Search" else tab.label,
                                searching = searching,
                                query = query,
                                scanning = scanning,
                                onQuery = { query = it },
                                onRefresh = vm::rescan,
                            )
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when {
                                    !granted -> MessageCard(
                                        "Allow access to your music",
                                        "The player needs permission to read audio files on this device. Nothing leaves your phone.",
                                    ) {
                                        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                                            Text("Allow access")
                                        }
                                        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                                            Text("Open app settings")
                                        }
                                    }
                                    !loaded || (scanning && songs.isEmpty()) -> MessageCard(
                                        "Looking for your music…",
                                        "Reading your device's media library.",
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                    songs.isEmpty() -> MessageCard(
                                        "No music found",
                                        "Nothing playable showed up in the media library. If you just copied songs onto the phone, tap rescan so Android indexes them.",
                                    ) {
                                        Button(onClick = { vm.rescan() }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Rescan storage")
                                        }
                                    }
                                    searching && q.isNotEmpty() -> SearchResults(
                                        query = q,
                                        songs = shownSongs,
                                        albums = shownAlbums,
                                        artists = shownArtists,
                                        playlists = shownPlaylists,
                                        songById = songById,
                                        st = st,
                                        bottomPad = bottomPad,
                                        onPlay = { vm.play(it, shownSongs) },
                                        onMenu = { dialog = Dlg.SongMenu(it, null) },
                                        onAlbum = { detail = Detail.AlbumD(it.id) },
                                        onArtist = { detail = Detail.ArtistD(it.name) },
                                        onPlaylist = { detail = Detail.PlaylistD(it.id) },
                                    )
                                    else -> Crossfade(targetState = tab, animationSpec = tween(220), label = "tab") { t ->
                                        when (t) {
                                            HomeTab.Songs -> SongGrid(
                                                songs = shownSongs,
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
                                    onRename = data.playlistId?.let { id ->
                                        { dialog = Dlg.RenamePlaylist(id, data.title) }
                                    },
                                )
                            }
                        }
                    }

                    // Mini player (slides up when a song starts), then the Nuvio floating nav bar.
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp),
                    ) {
                        AnimatedVisibility(
                            visible = st.current != null,
                            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 380f)) { it } +
                                fadeIn(tween(200)),
                            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160)),
                        ) {
                            lastSong.value?.let { song ->
                                Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                                    MiniPlayer(song, st, vm) { nowPlayingOpen = true }
                                }
                            }
                        }
                        BottomBar(
                            selected = tab,
                            searching = searching,
                            hazeState = hazeState,
                            scrollState = navScroll,
                            onTab = {
                                tab = it
                                detail = null
                                if (searching) toggleSearch()
                            },
                            onSearch = {
                                detail = null
                                toggleSearch()
                            },
                        )
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

                    // Bottom sheets: song menu, playlist picker, new / rename playlist.
                    dialog?.let { d ->
                        DialogHost(
                            dialog = d,
                            playlists = playlists,
                            vm = vm,
                            onDismiss = { dialog = null },
                            onSwitch = { dialog = it },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Header, search, bottom bar
// ---------------------------------------------------------------------------------------------

@Composable
private fun Header(
    title: String,
    searching: Boolean,
    query: String,
    scanning: Boolean,
    onQuery: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = onRefresh) {
                RefreshIcon(scanning)
            }
        }
        AnimatedVisibility(visible = searching) {
            SearchField(query, onQuery)
        }
    }
}

/** Spins while the library is being rescanned. The transition only exists while scanning. */
@Composable
private fun RefreshIcon(scanning: Boolean) {
    if (scanning) {
        val transition = rememberInfiniteTransition(label = "rescan_spin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "rescan_angle",
        )
        Icon(
            Icons.Filled.Refresh,
            contentDescription = "Rescanning",
            modifier = Modifier.graphicsLayer { rotationZ = angle },
        )
    } else {
        Icon(Icons.Filled.Refresh, contentDescription = "Rescan")
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text("Songs, albums, artists, playlists") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(focus),
    )
}

/**
 * Nuvio's floating jelly nav bar (see JellyNavBar.kt), unchanged. Two things keep it cheap:
 * the item list is remembered (it only changes when the selection does, so Home recomposing
 * no longer rebuilds the bar), and the accent colour fades in here, so only the bar
 * recomposes during the fade instead of the whole screen.
 */
@Composable
private fun BottomBar(
    selected: HomeTab,
    searching: Boolean,
    hazeState: HazeState,
    scrollState: NuvioNavBarScrollState,
    onTab: (HomeTab) -> Unit,
    onSearch: () -> Unit,
) {
    val latestOnTab by rememberUpdatedState(onTab)
    val latestOnSearch by rememberUpdatedState(onSearch)
    val items = remember(selected, searching) {
        HomeTab.values().map { t ->
            FloatingNavigationItem(
                label = t.label,
                selected = !searching && t == selected,
                onClick = { latestOnTab(t) },
                icon = t.icon,
            )
        } + FloatingNavigationItem(
            label = "Search",
            selected = searching,
            onClick = { latestOnSearch() },
            icon = Icons.Filled.Search,
        )
    }
    val accent by animateColorAsState(LocalAccent.current, tween(600), label = "nav_accent")
    CompositionLocalProvider(LocalAccent provides accent) {
        FloatingNavigationBar(
            items = items,
            scrollState = scrollState,
            hazeState = hazeState,
        )
    }
}

@Composable
private fun MessageCard(
    title: String,
    body: String,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
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
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 16.dp, end = 16.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------------------------
// Shared pieces: grid card, list row
// ---------------------------------------------------------------------------------------------

/** Material card for grids: square artwork on top, title and subtitle underneath. */
@Composable
private fun MediaCard(
    art: ImageBitmap?,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val source = remember { MutableInteractionSource() }
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) primary else Color.Transparent,
        animationSpec = tween(250),
        label = "card_border",
    )
    Card(
        onClick = onClick,
        interactionSource = source,
        modifier = Modifier.fillMaxWidth().pressScale(source, 0.95f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (borderColor.alpha > 0.01f) BorderStroke(2.dp, borderColor) else null,
    ) {
        Box {
            ArtworkBox(art, Modifier.fillMaxWidth().aspectRatio(1f), 0.dp)
            if (onMenu != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .bouncyClickable(0.85f, onClick = onMenu),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (highlighted) primary else Color.Unspecified,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Material list item for detail pages. */
@Composable
private fun TrackRow(
    song: Song,
    index: Int?,
    subtitle: String?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    ListItem(
        headlineContent = {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) primary else Color.Unspecified,
            )
        },
        supportingContent = if (subtitle != null) {
            {
                Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else null,
        leadingContent = {
            if (index != null) {
                Text(
                    "$index",
                    modifier = Modifier.width(28.dp),
                    color = if (isCurrent) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val art = rememberArtwork(song, song.artworkUrl, 160)
                ArtworkBox(art, Modifier.size(48.dp), 10.dp)
            }
        },
        trailingContent = {
            IconButton(onClick = onMenu) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.bouncyClickable(0.97f, onClick = onClick),
    )
}

// ---------------------------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------------------------

@Composable
private fun SongGrid(
    songs: List<Song>,
    st: PlayerState,
    bottomPad: Dp,
    onPlay: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onMenu: (Song) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (songs.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { EmptyHint("No matching songs") }
        } else {
            item(key = "controls", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = onPlayAll, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Play")
                    }
                    FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle")
                    }
                }
            }
        }
        items(songs, key = { it.id }) { song ->
            // Decoded at card size, not full size: much less work and memory while scrolling.
            val art = rememberArtwork(song, song.artworkUrl, 400)
            MediaCard(
                art = art,
                title = song.title,
                subtitle = song.artist,
                highlighted = st.current?.id == song.id,
                onClick = { onPlay(song) },
                onMenu = { onMenu(song) },
            )
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            val cover = album.songs.coverSong()
            val art = rememberArtwork(cover, cover?.artworkUrl, 400)
            MediaCard(
                art = art,
                title = album.name,
                subtitle = album.artist,
                highlighted = false,
                onClick = { onOpen(album) },
            )
        }
    }
}

@Composable
private fun ArtistList(artists: List<ArtistItem>, bottomPad: Dp, onOpen: (ArtistItem) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = bottomPad),
    ) {
        if (artists.isEmpty()) item(key = "empty") { EmptyHint("No matching artists") }
        items(artists, key = { it.name }) { artist ->
            ArtistRow(artist, onOpen)
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistItem, onOpen: (ArtistItem) -> Unit) {
    val cover = artist.songs.coverSong()
    val art = rememberArtwork(cover, cover?.artworkUrl, 160)
    ListItem(
        headlineContent = {
            Text(
                artist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = { Text("${artist.songs.size} songs") },
        leadingContent = { ArtworkBox(art, Modifier.size(56.dp), 28.dp) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.bouncyClickable(0.97f) { onOpen(artist) },
    )
}

@Composable
private fun AlbumRow(album: AlbumItem, onOpen: (AlbumItem) -> Unit) {
    val cover = album.songs.coverSong()
    val art = rememberArtwork(cover, cover?.artworkUrl, 160)
    ListItem(
        headlineContent = {
            Text(
                album.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { ArtworkBox(art, Modifier.size(56.dp), 14.dp) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.bouncyClickable(0.97f) { onOpen(album) },
    )
}

@Composable
private fun PlaylistRow(playlist: Playlist, songById: Map<Long, Song>, onOpen: (Playlist) -> Unit) {
    val cover = playlist.songIds.mapNotNull { songById[it] }.coverSong()
    val art = rememberArtwork(cover, cover?.artworkUrl, 160)
    ListItem(
        headlineContent = {
            Text(
                playlist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = { Text("${playlist.songIds.size} songs") },
        leadingContent = { ArtworkBox(art, Modifier.size(56.dp), 14.dp) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.bouncyClickable(0.97f) { onOpen(playlist) },
    )
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    songById: Map<Long, Song>,
    bottomPad: Dp,
    onNew: () -> Unit,
    onOpen: (Playlist) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    LazyColumn(
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = bottomPad),
    ) {
        item(key = "new") {
            ListItem(
                headlineContent = {
                    Text("New playlist", fontWeight = FontWeight.Medium, color = primary)
                },
                leadingContent = {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.bouncyClickable(0.97f, onClick = onNew),
            )
        }
        if (playlists.isEmpty()) {
            item(key = "empty") {
                EmptyHint("Create a playlist, then add songs from the ⋮ menu on any song.")
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistRow(playlist, songById, onOpen)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Global search results
// ---------------------------------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String, count: Int) {
    Text(
        "$text · $count",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp),
    )
}

/** One list for the whole library: songs, albums, artists and playlists matching the query. */
@Composable
private fun SearchResults(
    query: String,
    songs: List<Song>,
    albums: List<AlbumItem>,
    artists: List<ArtistItem>,
    playlists: List<Playlist>,
    songById: Map<Long, Song>,
    st: PlayerState,
    bottomPad: Dp,
    onPlay: (Song) -> Unit,
    onMenu: (Song) -> Unit,
    onAlbum: (AlbumItem) -> Unit,
    onArtist: (ArtistItem) -> Unit,
    onPlaylist: (Playlist) -> Unit,
) {
    val empty = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
    LazyColumn(
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 0.dp, bottom = bottomPad),
    ) {
        if (empty) {
            item(key = "none") { EmptyHint("No results for \"$query\"") }
        }
        if (songs.isNotEmpty()) {
            item(key = "h_songs") { SectionTitle("Songs", songs.size) }
            items(songs.take(20), key = { "s${it.id}" }) { song ->
                TrackRow(
                    song = song,
                    index = null,
                    subtitle = song.artist,
                    isCurrent = st.current?.id == song.id,
                    onClick = { onPlay(song) },
                    onMenu = { onMenu(song) },
                )
            }
        }
        if (albums.isNotEmpty()) {
            item(key = "h_albums") { SectionTitle("Albums", albums.size) }
            items(albums.take(10), key = { "al${it.id}" }) { album -> AlbumRow(album, onAlbum) }
        }
        if (artists.isNotEmpty()) {
            item(key = "h_artists") { SectionTitle("Artists", artists.size) }
            items(artists.take(10), key = { "ar${it.name}" }) { artist -> ArtistRow(artist, onArtist) }
        }
        if (playlists.isNotEmpty()) {
            item(key = "h_playlists") { SectionTitle("Playlists", playlists.size) }
            items(playlists, key = { "p${it.id}" }) { playlist -> PlaylistRow(playlist, songById, onPlaylist) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Detail page (album / artist / playlist)
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
    onRename: (() -> Unit)?,
) {
    val cover = data.songs.coverSong()
    val art = rememberArtwork(cover, cover?.artworkUrl, 640)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = bottomPad),
        ) {
            item(key = "hero") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtworkBox(art, Modifier.size(220.dp), 28.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        data.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        data.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        data.meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = onPlayAll, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Play")
                        }
                        FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Shuffle")
                        }
                        if (onRename != null) {
                            FilledTonalIconButton(onClick = onRename, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename playlist")
                            }
                        }
                        FilledTonalIconButton(
                            onClick = onDelete ?: onAddAll,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(
                                if (onDelete != null) Icons.Filled.Delete else Icons.Filled.Add,
                                contentDescription = if (onDelete != null) "Delete playlist" else "Add to playlist",
                            )
                        }
                    }
                }
            }
            if (data.songs.isEmpty()) {
                item(key = "empty") { EmptyHint("No songs yet. Add some from the ⋮ menu on any song.") }
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
    }
}

// ---------------------------------------------------------------------------------------------
// Bottom sheets
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogHost(
    dialog: Dlg,
    playlists: List<Playlist>,
    vm: PlayerViewModel,
    onDismiss: () -> Unit,
    onSwitch: (Dlg) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            when (dialog) {
                is Dlg.SongMenu -> {
                    Text(
                        dialog.song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        dialog.song.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    MenuItem("Add to playlist") { onSwitch(Dlg.Pick(listOf(dialog.song))) }
                    if (dialog.playlistId != null) {
                        MenuItem("Move up in playlist") {
                            vm.moveInPlaylist(dialog.playlistId, dialog.song.id, -1)
                            onDismiss()
                        }
                        MenuItem("Move down in playlist") {
                            vm.moveInPlaylist(dialog.playlistId, dialog.song.id, 1)
                            onDismiss()
                        }
                        MenuItem("Remove from this playlist", destructive = true) {
                            vm.removeFromPlaylist(dialog.playlistId, dialog.song.id)
                            onDismiss()
                        }
                    }
                }
                is Dlg.Pick -> {
                    Text(
                        "Add to playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
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
                is Dlg.NewPlaylist -> NewPlaylistContent(dialog.songs, vm, onDismiss)
                is Dlg.RenamePlaylist -> RenamePlaylistContent(dialog.id, dialog.name, vm, onDismiss)
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text,
                fontWeight = FontWeight.Medium,
                color = if (destructive) Color(0xFFFF6B6B) else Color.Unspecified,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).bouncyClickable(0.97f, onClick = onClick),
    )
}

@Composable
private fun NewPlaylistContent(songs: List<Song>, vm: PlayerViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Text(
        "New playlist",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        label = { Text("Playlist name") },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = {
            vm.createPlaylist(name, songs.map { it.id })
            onDismiss()
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text("Create")
    }
}

@Composable
private fun RenamePlaylistContent(id: String, current: String, vm: PlayerViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Text(
        "Rename playlist",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        singleLine = true,
        label = { Text("Playlist name") },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = {
            vm.renamePlaylist(id, name)
            onDismiss()
        },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text("Save")
    }
}
