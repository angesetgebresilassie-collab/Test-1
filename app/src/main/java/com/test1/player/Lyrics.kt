package com.test1.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LyricWord(val text: String, val startMs: Long, val endMs: Long)

data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord>,
)

private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
private val WORD_TAG = Regex("""<\d+:\d+(?:[.:]\d+)?>""")

/**
 * Parses LRC. LRC only has line timestamps, so each line's duration is spread
 * across its words (weighted by word length) to get smooth word-by-word timing.
 */
fun parseLrc(lrc: String?): List<LyricLine> {
    if (lrc.isNullOrBlank()) return emptyList()
    val raw = mutableListOf<Pair<Long, String>>()
    for (line in lrc.lines()) {
        val tags = TIME_TAG.findAll(line).toList()
        if (tags.isEmpty()) continue
        val text = line.substring(tags.last().range.last + 1).replace(WORD_TAG, "").trim()
        for (m in tags) {
            val minutes = m.groupValues[1].toLong()
            val seconds = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            val millis = when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
            raw += (minutes * 60_000 + seconds * 1_000 + millis) to text
        }
    }
    raw.sortBy { it.first }
    val out = mutableListOf<LyricLine>()
    for (i in raw.indices) {
        val (start, text) = raw[i]
        if (text.isBlank()) continue
        val nextStart = raw.getOrNull(i + 1)?.first ?: (start + 5_000)
        out += buildLine(start, nextStart, text)
    }
    return out
}

private fun buildLine(start: Long, nextStart: Long, text: String): LyricLine {
    val parts = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val totalWeight = parts.sumOf { it.length + 1 }.coerceAtLeast(1)
    val gap = (nextStart - start).coerceAtLeast(1)
    val singing = (totalWeight * 95L + 500L).coerceAtMost(gap).coerceAtLeast(300L)
    var acc = 0
    val words = parts.map { w ->
        val weight = w.length + 1
        val s = start + singing * acc / totalWeight
        acc += weight
        val e = start + singing * acc / totalWeight
        LyricWord(w, s, e)
    }
    return LyricLine(start, nextStart, text, words)
}

private fun wordProgress(word: LyricWord, pos: Long): Float {
    val d = (word.endMs - word.startMs).coerceAtLeast(1).toFloat()
    val t = ((pos - word.startMs + 0.25f * d) / (d * 1.5f)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

@Composable
fun LyricsView(
    modifier: Modifier,
    lines: List<LyricLine>,
    plain: String?,
    pos: LongState,
    onSeek: (Long) -> Unit,
) {
    when {
        lines.isNotEmpty() -> SyncedLyrics(modifier, lines, pos, onSeek)
        !plain.isNullOrBlank() -> Column(
            modifier.verticalScroll(rememberScrollState()).padding(vertical = 24.dp)
        ) {
            Text(
                plain,
                fontSize = 22.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        else -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("No lyrics found", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun SyncedLyrics(
    modifier: Modifier,
    lines: List<LyricLine>,
    pos: LongState,
    onSeek: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    val activeIndex by remember(lines) {
        derivedStateOf {
            var idx = -1
            val p = pos.longValue
            for (i in lines.indices) {
                if (lines[i].startMs <= p) idx = i else break
            }
            idx
        }
    }
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val h = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(activeIndex, -(h / 3))
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(top = 120.dp, bottom = 240.dp),
    ) {
        itemsIndexed(lines, key = { i, l -> "$i-${l.startMs}" }) { index, line ->
            SyncedLine(
                line = line,
                relation = index.compareTo(activeIndex),
                pos = pos,
                onClick = { onSeek(line.startMs) },
            )
        }
    }
}

/** relation: <0 already sung, 0 active, >0 upcoming */
@Composable
private fun SyncedLine(line: LyricLine, relation: Int, pos: LongState, onClick: () -> Unit) {
    val active = relation == 0
    val scale by animateFloatAsState(if (active) 1f else 0.92f, tween(400), label = "lineScale")
    val baseAlpha by animateFloatAsState(
        when {
            active -> 1f
            relation < 0 -> 0.45f
            else -> 0.32f
        },
        tween(400),
        label = "lineAlpha",
    )
    val text: AnnotatedString = if (active) {
        val p = pos.longValue
        buildAnnotatedString {
            line.words.forEachIndexed { i, w ->
                val progress = wordProgress(w, p)
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.38f + 0.62f * progress))) {
                    append(w.text)
                }
                if (i < line.words.lastIndex) append(" ")
            }
        }
    } else {
        AnnotatedString(line.text)
    }
    Text(
        text = text,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = baseAlpha),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
