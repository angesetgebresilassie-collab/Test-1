package com.test1.player

data class ParsedName(val artist: String?, val title: String)

/** Extracts artist / title from a song's file name and strips download junk. */
object FileNames {
    private val extension =
        Regex("""\.(mp3|m4a|aac|flac|ogg|opus|wav|wma|amr|mka)$""", RegexOption.IGNORE_CASE)

    // Bracketed groups that only hold junk: (Official Audio), [HD], (Lyrics), (www.site.com) ...
    private val junkGroup = Regex(
        """\s*[(\[{][^)\]}]*?(official|lyric|audio|video|visuali[sz]er|\bhd\b|\bhq\b|4k|remaster|explicit|\bmv\b|free download|download|kbps|www\.|\.com|\.net|\.org|prod\.)[^)\]}]*[)\]}]""",
        RegexOption.IGNORE_CASE,
    )
    private val site = Regex("""\b(?:www\.)?[a-z0-9-]+\.(?:com|net|org|ir|co|me|info)\b""", RegexOption.IGNORE_CASE)
    private val leadingTrack = Regex("""^\s*\d{1,2}\s*[-._)]\s*""")
    private val spaces = Regex("""\s+""")
    private val dash = Regex("""\s[-\u2013\u2014]\s""")

    fun parse(fileName: String): ParsedName {
        var s = fileName.replace(extension, "").replace('_', ' ')
        s = junkGroup.replace(s, "")
        s = site.replace(s, "")
        s = leadingTrack.replace(s, "")
        s = spaces.replace(s, " ").trim(' ', '-', '.')
        val parts = s.split(dash, limit = 2)
        return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            ParsedName(parts[0].trim(), parts[1].trim())
        } else {
            ParsedName(null, s.ifBlank { fileName })
        }
    }
}
