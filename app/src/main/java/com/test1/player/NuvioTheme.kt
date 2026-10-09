/*
 * Spacing, radii, text colours and type scale ported from Nuvio Mobile
 * (https://github.com/NuvioMedia/NuvioMobile, licensed GPL-3.0): core/ui/Tokens.kt and
 * core/ui/Theme.kt. Original copyright belongs to the Nuvio authors; distributed under GPL-3.0.
 *
 * Nuvio ships the JetBrains Sans font; the font files are not part of this port, so the app's
 * default font is used with Nuvio's sizes, line heights, weights and letter spacing.
 */
package com.test1.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** NuvioTokens.Space plus the NuvioSpacingTokens defaults. */
internal object NvSpace {
    val hairline = 0.5.dp
    val s2 = 2.dp
    val s4 = 4.dp
    val s6 = 6.dp
    val s8 = 8.dp
    val s10 = 10.dp
    val s12 = 12.dp
    val s14 = 14.dp
    val s16 = 16.dp
    val s18 = 18.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s28 = 28.dp
    val s32 = 32.dp
    val s40 = 40.dp
    val s48 = 48.dp
    val s56 = 56.dp

    val screenHorizontal = s16
    val screenTop = s10
    val screenBottom = s18
    val sectionGap = s24
    val listGap = s12
    val railGap = s14
    val controlGap = s8
    val cardPadding = s18
    val sheetPadding = s20
}

/** NuvioTokens.Radius. */
internal object NvRadius {
    val xs = 4.dp
    val sm = 6.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val xxl = 24.dp
    val poster = lg
    val button = xl
    val sheet = xxl
    val dialog = 28.dp
}

/** Nuvio's text colours. */
internal object NvColor {
    val textPrimary = Color(0xFFF5F7F8)
    val textSecondary = Color(0xFFB8BEC5)
    val textMuted = Color(0xFF969CA3)
    val surface = Color.White.copy(alpha = 0.08f)
}

/** Nuvio's Material typography (Theme.kt) and type scale (NuvioTokens.Type / LineHeight). */
internal object NvType {
    val displayLarge = TextStyle(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp)
    val displaySmallHero = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black)
    val displaySm = TextStyle(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    val headlineLarge = TextStyle(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
    val titleMd = TextStyle(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val bodyLg = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val bodyApp = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val bodySm = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    val labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
}
