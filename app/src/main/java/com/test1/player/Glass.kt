package com.test1.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.cos
import kotlin.math.sin

val LiquidBlue = Color(0xFF2E8BFF)

/** Real Liquid Glass: samples the layer backdrop, blurs, saturates and refracts at the edges. */
@Composable
fun GlassSurface(
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    strong: Boolean = true,
    tint: Color = Color.White.copy(alpha = 0.12f),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { RoundedCornerShape(corner) },
            effects = {
                vibrancy()
                blur(if (strong) 8.dp.toPx() else 5.dp.toPx())
                lens(
                    if (strong) 16.dp.toPx() else 8.dp.toPx(),
                    if (strong) 32.dp.toPx() else 16.dp.toPx(),
                )
            },
            onDrawSurface = { drawRect(tint) },
        ),
        content = content,
    )
}

/** 0 → 1 with a springy overshoot while the button is held down (the "liquid" swell). */
@Composable
fun pressProgress(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "press",
    )
    return progress
}

@Composable
fun GlassButton(
    backdrop: LayerBackdrop,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.10f),
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val p = pressProgress(source)
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val s = 1f + 0.12f * p
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        corner = size / 2,
        tint = tint.copy(alpha = (tint.alpha + 0.14f * p).coerceAtMost(1f)),
        content = content,
    )
}

enum class LiquidStyle { Transparent, Surface, Tinted }

/**
 * The three liquid button styles: Transparent (pure refraction), Surface (frosted) and
 * Tinted (solid colour with glass edges). All of them swell when pressed.
 */
@Composable
fun LiquidButton(
    backdrop: LayerBackdrop,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: LiquidStyle = LiquidStyle.Surface,
    color: Color = LiquidBlue,
    height: Dp = 52.dp,
) {
    val source = remember { MutableInteractionSource() }
    val p = pressProgress(source)
    val tint = when (style) {
        LiquidStyle.Transparent -> Color.White.copy(alpha = 0.06f + 0.14f * p)
        LiquidStyle.Surface -> Color.White.copy(alpha = 0.20f + 0.14f * p)
        LiquidStyle.Tinted -> color.copy(alpha = (0.88f + 0.12f * p).coerceAtMost(1f))
    }
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .height(height)
            .graphicsLayer {
                val s = 1f + 0.10f * p
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(height / 2))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        corner = height / 2,
        tint = tint,
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
            }
            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, color = Color.White)
        }
    }
}

@Composable
fun GlassPill(
    backdrop: LayerBackdrop,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
    style: LiquidStyle = LiquidStyle.Surface,
) {
    LiquidButton(backdrop, label, onClick, modifier, icon, style, LiquidBlue, height)
}

@Composable
fun ArtworkBox(bitmap: ImageBitmap?, modifier: Modifier, corner: Dp = 14.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                "♪",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 22.sp,
            )
        }
    }
}

@Composable
fun ArtworkImage(bitmap: ImageBitmap?, size: Dp, corner: Dp = 14.dp) {
    ArtworkBox(bitmap, Modifier.size(size), corner)
}

private fun hueShift(c: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Dark backdrop for the glass to refract: near-black with faint drifting colour glows
 * (tinted by the current artwork) and a dim, blurred copy of the artwork.
 */
@Composable
fun LiquidBackground(accent: Color?, art: ImageBitmap?) {
    val palette = remember(accent) {
        if (accent != null) {
            listOf(accent, hueShift(accent, 35f), hueShift(accent, -45f), hueShift(accent, 150f))
        } else {
            listOf(Color(0xFF3D5AFE), Color(0xFF7C4DFF), Color(0xFF00B0FF), Color(0xFF1DE9B6))
        }
    }
    val transition = rememberInfiniteTransition(label = "liquid")
    val a by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "a",
    )
    val b by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(37000, easing = LinearEasing)),
        label = "b",
    )

    Box(Modifier.fillMaxSize().background(Color(0xFF040406))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tau = (2.0 * Math.PI).toFloat()
            fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                val center = Offset(cx, cy)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.30f), Color.Transparent),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                    center = center,
                )
            }
            blob(palette[0], w * (0.25f + 0.25f * sin(a * tau)), h * (0.15f + 0.10f * cos(b * tau)), w * 0.95f)
            blob(palette[1], w * (0.80f + 0.18f * cos(a * tau)), h * (0.42f + 0.12f * sin(b * tau)), w * 0.85f)
            blob(palette[2], w * (0.20f + 0.20f * sin(b * tau)), h * (0.70f + 0.10f * cos(a * tau)), w * 0.90f)
            blob(palette[3], w * (0.75f + 0.15f * sin(a * tau + 1f)), h * (0.95f + 0.05f * cos(b * tau)), w * 0.80f)
        }
        Crossfade(targetState = art, label = "artBackground") { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(50.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f,
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.72f))))
        )
    }
}
