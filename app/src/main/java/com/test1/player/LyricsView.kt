package com.test1.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LyricStyle = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)

@Composable
fun LyricsView(vm: PlayerViewModel, song: Song, modifier: Modifier = Modifier) {
    val state by produceState<LyricsState>(LyricsState.Loading, song.id) {
        value = vm.lyricsRepo.get(song)?.let { LyricsState.Ready(it) } ?: LyricsState.None
    }
    when (val s = state) {
        LyricsState.Loading -> Box(modifier, Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        LyricsState.None -> Box(modifier, Alignment.Center) {
            Text("No lyrics available", color = Color.White.copy(alpha = 0.6f))
        }
        is LyricsState.Ready -> LyricsList(vm, s.lines, modifier)
    }
}

@Composable
private fun LyricsList(vm: PlayerViewModel, lines: List<LyricLine>, modifier: Modifier) {
    val synced = (lines.firstOrNull()?.timeMs ?: -1L) >= 0
    var pos by remember { mutableLongStateOf(vm.positionMs()) }

    // Per-frame position so word highlighting glides instead of stepping.
    if (synced) {
        LaunchedEffect(vm) {
            while (true) {
                withFrameNanos { }
                pos = vm.positionMs()
            }
        }
    }

    val activeIndex by remember(lines) {
        derivedStateOf {
            var idx = -1
            if (synced) {
                for (i in lines.indices) {
                    if (lines[i].timeMs <= pos) idx = i else break
                }
            }
            idx
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 340.dp),
    ) {
        itemsIndexed(lines) { i, line ->
            val nextStart = lines.getOrNull(i + 1)?.timeMs ?: (line.timeMs + 4000)
            LyricLineItem(
                line = line,
                rel = if (synced) i - activeIndex else 0,
                synced = synced,
                pos = pos,
                nextStart = nextStart,
                onClick = { if (synced) vm.seekTo(line.timeMs) },
            )
        }
    }
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    rel: Int,
    synced: Boolean,
    pos: Long,
    nextStart: Long,
    onClick: () -> Unit,
) {
    val active = synced && rel == 0
    val targetAlpha = when {
        !synced -> 0.9f
        rel == 0 -> 1f
        rel < 0 -> 0.5f
        else -> 0.35f
    }
    val lineAlpha by animateFloatAsState(targetAlpha, tween(500), label = "lineAlpha")
    val lineScale by animateFloatAsState(if (active || !synced) 1f else 0.92f, tween(500), label = "lineScale")
    val text = line.text.ifBlank { "\u266A" }

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = lineAlpha
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        if (active) {
            val timings = remember(line, nextStart) { wordTimings(line, nextStart) }
            if (timings.isEmpty()) {
                Text(text, style = LyricStyle, color = Color.White)
            } else {
                Text(
                    text = buildAnnotatedString {
                        for (w in timings) {
                            val raw = (pos - w.start).toFloat() / (w.end - w.start).coerceAtLeast(1L)
                            val p = smooth(raw.coerceIn(0f, 1f))
                            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.4f + 0.6f * p))) {
                                append(w.text)
                            }
                        }
                    },
                    style = LyricStyle,
                )
            }
        } else {
            Text(text, style = LyricStyle, color = Color.White)
        }
    }
}

private data class Timed(val text: String, val start: Long, val end: Long)

private fun smooth(p: Float) = p * p * (3f - 2f * p)

private fun String.withSpace() = if (endsWith(' ')) this else "$this "

/** Real word times from enhanced LRC if present, otherwise spread the words across the line. */
private fun wordTimings(line: LyricLine, nextStart: Long): List<Timed> {
    line.words?.takeIf { it.isNotEmpty() }?.let { ws ->
        return ws.mapIndexed { i, w ->
            Timed(w.text.withSpace(), w.timeMs, ws.getOrNull(i + 1)?.timeMs ?: nextStart)
        }
    }
    val parts = line.text.split(' ').filter { it.isNotEmpty() }
    if (parts.isEmpty()) return emptyList()
    val span = nextStart - line.timeMs
    val sing = minOf(span, maxOf(800L, line.text.length * 80L)).coerceAtLeast(300L)
    val totalWeight = parts.sumOf { it.length + 1 }
    var t = line.timeMs
    return parts.mapIndexed { i, p ->
        val d = sing * (p.length + 1) / totalWeight
        val w = Timed(if (i < parts.lastIndex) "$p " else p, t, t + d)
        t += d
        w
    }
}
