package com.test1.player

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media

class LibraryRepository(private val resolver: ContentResolver) {

    /**
     * Reads every playable audio file from MediaStore.
     * Title and artist come from the FILE NAME (embedded tags are often wrong or misleading);
     * a background metadata lookup later refines them.
     * No IS_MUSIC filter: downloads, messenger audio and many m4a files are not flagged as
     * music even though they are songs. Ringtones/alarms/notifications, sound/voice/call
     * recordings and clips under 15 seconds are skipped instead.
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
        val clauses = mutableListOf(
            "${Media.IS_RINGTONE} = 0",
            "${Media.IS_NOTIFICATION} = 0",
            "${Media.IS_ALARM} = 0",
            "${Media.DURATION} >= 15000",
        )
        // Android 12+ flags recordings made by the system recorder. The column doesn't exist
        // on older versions (querying it would fail), so only add it where it's available.
        if (Build.VERSION.SDK_INT >= 31) clauses += "${Media.IS_RECORDING} = 0"
        // Recorder apps (Google Recorder, Samsung Voice Recorder, call recorders) that don't set
        // the flag still save into well-known folders, so hide those folders too.
        val recordingFolders = listOf("%Recordings/%", "%Voice Recorder/%", "%Sound Recorder/%", "%Call/%", "%Call recordings/%")
        clauses += "(${Media.RELATIVE_PATH} IS NULL OR (" +
            recordingFolders.joinToString(" AND ") { "${Media.RELATIVE_PATH} NOT LIKE '$it'" } + "))"
        val selection = clauses.joinToString(" AND ")
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
