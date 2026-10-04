package com.test1.player

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** Liquid Glass surface (Kyant0/AndroidLiquidGlass). Place sizing modifiers after this one. */
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape = CircleShape,
    surface: Color = Color.White.copy(alpha = 0.14f),
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(6f.dp.toPx())
        lens(16f.dp.toPx(), 32f.dp.toPx())
    },
    onDrawSurface = { drawRect(surface) },
)
