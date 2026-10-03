package com.test1.player

/**
 * Turns a file name like "03 - Artist - Song Title (Official Video) [dQw4w9WgXcQ].mp3"
 * into something a metadata search can use. Embedded tags are ignored on purpose.
 */
object FileNames {
    private val junkBrackets = Regex(
        """[\(\[\{][^\)\]\}]*(official|lyric|audio|video|visuali[sz]er|\bhd\b|\bhq\b|4k|1080p|720p|remaster|explicit|clean version|\bmv\b|m/v|download|spotdl|youtube|ytmp3|\bmp3\b|320|kbps|free|full song|new song)[^\)\]\}]*[\)\]\}]""",
        RegexOption.IGNORE_CASE,
    )
    private val youtubeId = Regex("""\s*[\[\(][A-Za-z0-9_-]{11}[\]\)]\s*$""")
    private val domain = Regex("""\b[\w-]+\.(com|net|org|io|co|me|to|cc|ru)\b""", RegexOption.IGNORE_CASE)
    private val leadingNumber = Regex("""^\s*\(?\d{1,3}\)?\s*[-.)]\s+|^\s*0\d\s+""")
    private val topic = Regex("""\s*[-–—]\s*Topic\b""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""\s+""")
    private val splitter = Regex("""^(.+?)\s+[-–—]\s+(.+)$""")

    /** Cleaned name without extension, track numbers, "(Official Video)" style junk, ids and URLs. */
    fun clean(fileName: String): String {
        var s = fileName.substringBeforeLast('.', fileName)
        s = s.replace('_', ' ')
        s = youtubeId.replace(s, "")
        s = junkBrackets.replace(s, "")
        s = domain.replace(s, "")
        s = leadingNumber.replace(s, "")
        s = topic.replace(s, "")
        s = spaces.replace(s, " ").trim(' ', '-', '–', '—', '.')
        return s.ifBlank { fileName.substringBeforeLast('.', fileName).trim() }
    }

    /** (artist, title). Artist is null when the name has no "Artist - Title" shape. */
    fun parse(fileName: String): Pair<String?, String> {
        val s = clean(fileName)
        val m = splitter.find(s)
        return if (m != null) {
            m.groupValues[1].trim() to m.groupValues[2].trim().ifBlank { s }
        } else {
            null to s
        }
    }

    /** Text sent to the metadata search: "Artist Title" (the order in the file name doesn't matter). */
    fun searchQuery(fileName: String): String =
        clean(fileName).replace(Regex("""\s+[-–—]\s+"""), " ")
}
