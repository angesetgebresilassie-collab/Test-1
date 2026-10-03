package com.test1.player

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media

class LibraryRepository(private val resolver: ContentResolver) {

    /**
     * Reads every playable audio file from MediaStore.
     *
     * Title and artist come from the FILE NAME, not the embedded tags (tags are often wrong).
     * Album is unknown until a metadata lookup fills it in.
     *
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
            Media.DURATION,
        )
        val selection = "${Media.IS_RINGTONE} = 0 AND ${Media.IS_NOTIFICATION} = 0 AND " +
            "${Media.IS_ALARM} = 0 AND ${Media.DURATION} >= 15000"
        runCatching {
            resolver.query(collection, projection, selection, null, "${Media.DISPLAY_NAME} COLLATE NOCASE ASC")?.use { c ->
                val id = c.getColumnIndexOrThrow(Media._ID)
                val name = c.getColumnIndexOrThrow(Media.DISPLAY_NAME)
                val duration = c.getColumnIndexOrThrow(Media.DURATION)
                while (c.moveToNext()) {
                    val songId = c.getLong(id)
                    val fileName = c.getString(name).orEmpty()
                    val (artist, title) = FileNames.parse(fileName)
                    out += Song(
                        id = songId,
                        title = title.ifBlank { fileName },
                        artist = artist?.takeIf { it.isNotBlank() } ?: "Unknown artist",
                        album = "Unknown album",
                        albumId = 0L,
                        track = 0,
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
