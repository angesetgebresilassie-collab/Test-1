package com.test1.player

/**
 * Playlist editing helpers (rename / reorder). Kept as extensions so the player code stays
 * untouched; they publish the new list the same way PlayerViewModel does and persist it.
 */
private fun PlayerViewModel.commitPlaylists(list: List<Playlist>) {
    playlists.value = list
    PlaylistStore(getApplication()).save(list)
}

fun PlayerViewModel.renamePlaylist(playlistId: String, name: String) {
    val clean = name.trim()
    if (clean.isEmpty()) return
    commitPlaylists(playlists.value.map { if (it.id == playlistId) it.copy(name = clean) else it })
}

/** Moves [songId] one step up ([delta] = -1) or down ([delta] = 1) inside the playlist. */
fun PlayerViewModel.moveInPlaylist(playlistId: String, songId: Long, delta: Int) {
    commitPlaylists(
        playlists.value.map { p ->
            if (p.id != playlistId) return@map p
            val ids = p.songIds.toMutableList()
            val from = ids.indexOf(songId)
            val to = (from + delta).coerceIn(0, (ids.size - 1).coerceAtLeast(0))
            if (from < 0 || from == to) return@map p
            ids.add(to, ids.removeAt(from))
            p.copy(songIds = ids)
        },
    )
}
