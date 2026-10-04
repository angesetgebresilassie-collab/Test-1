package com.test1.player

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media

class LibraryRepository(private val resolver: ContentResolver) {

    /**
     * Reads every playable audio file from MediaStore.
     * Title and artist come from the FILE NAME (embedded tags are often wrong or misleading);
     * a background metadata lookup later refines them.
     * No IS_MUSIC filter: downloads, messenger audio and many m4a files are not flagged as
     * music even though they are songs. Ringtones/alarms/notifications and clips under
     * 15 seconds are skipped instead.
     */
    fun songs(): List<Song> {
        val out = mutableListOf<Song>()
        val collection = Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            Media._ID,
            Media.DISPLAY_NAME,
            Media.TITLE,
            Media.ALBUM,
            Media.ALBUM_ID,
            Media.TRACK,
            Media.DURATION,
        )
        val selection = "${Media.IS_RINGTONE} = 0 AND ${Media.IS_NOTIFICATION} = 0 AND " +
            "${Media.IS_ALARM} = 0 AND ${Media.DURATION} >= 15000"
        runCatching {
            resolver.query(collection, projection, selection, null, "${Media.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { c ->
                val id = c.getColumnIndexOrThrow(Media._ID)
                val display = c.getColumnIndexOrThrow(Media.DISPLAY_NAME)
                val tagTitle = c.getColumnIndexOrThrow(Media.TITLE)
                val album = c.getColumnIndexOrThrow(Media.ALBUM)
                val albumId = c.getColumnIndexOrThrow(Media.ALBUM_ID)
                val track = c.getColumnIndexOrThrow(Media.TRACK)
                val duration = c.getColumnIndexOrThrow(Media.DURATION)
                while (c.moveToNext()) {
                    val songId = c.getLong(id)
                    val fileName = c.getString(display).orEmpty()
                    val parsed = FileNames.parse(fileName.ifBlank { c.getString(tagTitle).orEmpty() })
                    out += Song(
                        id = songId,
                        title = parsed.title.ifBlank { "Unknown title" },
                        artist = parsed.artist ?: "Unknown artist",
                        album = c.getString(album)?.takeUnless { it.isBlank() } ?: "Unknown album",
                        albumId = c.getLong(albumId),
                        track = c.getInt(track),
                        durationMs = c.getLong(duration),
                        uri = ContentUris.withAppendedId(collection, songId),
                        fileName = fileName,
                    )
                }
            }
        }
        return out
    }
}
