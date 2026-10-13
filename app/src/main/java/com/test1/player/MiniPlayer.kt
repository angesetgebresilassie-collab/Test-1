package com.test1.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// Small motion helpers shared by the whole UI
// ---------------------------------------------------------------------------------------------

/**
 * Springy press feedback. The scale is read inside graphicsLayer, so animating it only
 * invalidates the layer (no recomposition, no relayout).
 */
internal fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.96f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "press_scale",
    )
    Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** clickable() with the springy press scale. Put clip() before it if the ripple should be rounded. */
@Composable
internal fun Modifier.bouncyClickable(
    pressedScale: Float = 0.97f,
    ripple: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .pressScale(source, pressedScale)
        .clickable(
            interactionSource = source,
            indication = if (ripple) LocalIndication.current else null,
            onClick = onClick,
        )
}

/**
 * Vivid accent colour of the artwork, computed off the main thread. Keeps the previous colour
 * while the next cover is still loading so the UI does not flash back to the default.
 */
@Composable
internal fun rememberArtAccent(art: ImageBitmap?): Color? {
    val accent by produceState<Color?>(initialValue = null, art) {
        if (art == null) {
            delay(400)
            value = null
        } else {
            value = withContext(Dispatchers.Default) { art.accentColor() }
        }
    }
    return accent
}

// ---------------------------------------------------------------------------------------------
// Mini player
// ---------------------------------------------------------------------------------------------

/**
 * Mini player styled from the artwork: tinted gradient + glow in the cover's colour, accent
 * play button, live progress line, artwork that breathes with play/pause, and title / cover
 * transitions when the song changes. Colour and progress are read in the draw phase only.
 */
@Composable
internal fun MiniPlayer(song: Song, st: PlayerState, vm: PlayerViewModel, onOpen: () -> Unit) {
    val art = rememberArtwork(song, st.metadata?.artworkUrl, 192)
    val artAccent = rememberArtAccent(art)
    val target = artAccent ?: MaterialTheme.colorScheme.primary
    val onTarget = if (target.luminance() > 0.5f) Color.Black else Color.White
    val accent = animateColorAsState(target, tween(600), label = "mini_accent")
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val title = st.metadata?.title ?: song.title
    val artist = st.metadata?.artist ?: song.artist

    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(song.id, st.playing) {
        while (true) {
            val duration = song.durationMs
            progress = if (duration > 0) (vm.positionMs().toFloat() / duration).coerceIn(0f, 1f) else 0f
            if (!st.playing) break
            delay(250)
        }
    }

    val artScale by animateFloatAsState(
        targetValue = if (st.playing) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f),
        label = "mini_art_scale",
    )
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .pressScale(source, 0.97f)
            .shadow(12.dp, shape, clip = false, ambientColor = target, spotColor = target)
            .clip(shape)
            .drawBehind {
                val c = accent.value
                drawRect(
                    Brush.horizontalGradient(
                        listOf(lerp(base, c, 0.46f), lerp(base, c, 0.10f)),
                    ),
                )
                val barHeight = 3.dp.toPx()
                drawRect(
                    Color.White.copy(alpha = 0.08f),
                    topLeft = Offset(0f, size.height - barHeight),
                    size = Size(size.width, barHeight),
                )
                drawRect(
                    c,
                    topLeft = Offset(0f, size.height - barHeight),
                    size = Size(size.width * progress, barHeight),
                )
                drawRoundRect(
                    color = c.copy(alpha = 0.35f),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            .clickable(interactionSource = source, indication = null, onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crossfade(targetState = art, animationSpec = tween(350), label = "mini_art") { cover ->
                ArtworkBox(
                    cover,
                    Modifier.size(48.dp).graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    },
                    14.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                targetState = title to artist,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (slideInVertically(tween(280)) { it / 2 } + fadeIn(tween(280))) togetherWith
                        (slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(140)))
                },
                label = "mini_text",
            ) { (t, a) ->
                Column {
                    Text(
                        t,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        a,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .bouncyClickable(0.88f, ripple = false) { vm.toggle() }
                    .clip(CircleShape)
                    .drawBehind { drawCircle(accent.value) },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = st.playing, animationSpec = tween(180), label = "mini_play") { playing ->
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = onTarget,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(44.dp).bouncyClickable(0.8f, ripple = false) { vm.next() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}
