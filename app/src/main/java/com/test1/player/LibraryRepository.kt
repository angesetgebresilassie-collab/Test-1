package com.test1.player

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media

class LibraryRepository(private val resolver: ContentResolver) {

    /**
     * Reads every playable audio file from MediaStore.
     * No IS_MUSIC filter: downloads, messenger audio and many m4a files are not flagged as
     * music even though they are songs. Ringtones/alarms/notifications and clips under
     * 15 seconds are skipped instead.
     */
    fun songs(): List<Song> {
        val out = mutableListOf<Song>()
        val collection = Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            Media._ID,
            Media.TITLE,
            Media.ARTIST,
            Media.ALBUM,
            Media.ALBUM_ID,
            Media.TRACK,
            Media.DURATION,
        )
        val selection = "${Media.IS_RINGTONE} = 0 AND ${Media.IS_NOTIFICATION} = 0 AND " +
            "${Media.IS_ALARM} = 0 AND ${Media.DURATION} >= 15000"
        runCatching {
            resolver.query(collection, projection, selection, null, "${Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                val id = c.getColumnIndexOrThrow(Media._ID)
                val title = c.getColumnIndexOrThrow(Media.TITLE)
                val artist = c.getColumnIndexOrThrow(Media.ARTIST)
                val album = c.getColumnIndexOrThrow(Media.ALBUM)
                val albumId = c.getColumnIndexOrThrow(Media.ALBUM_ID)
                val track = c.getColumnIndexOrThrow(Media.TRACK)
                val duration = c.getColumnIndexOrThrow(Media.DURATION)
                while (c.moveToNext()) {
                    val songId = c.getLong(id)
                    out += Song(
                        id = songId,
                        title = c.getString(title)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                        artist = c.getString(artist)?.takeUnless { it == "<unknown>" || it.isBlank() } ?: "Unknown artist",
                        album = c.getString(album)?.takeUnless { it.isBlank() } ?: "Unknown album",
                        albumId = c.getLong(albumId),
                        track = c.getInt(track),
                        durationMs = c.getLong(duration),
                        uri = ContentUris.withAppendedId(collection, songId),
                    )
                }
            }
        }
        return out
    }
}
