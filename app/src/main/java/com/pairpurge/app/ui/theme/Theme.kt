package com.pairpurge.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Pills and the 28dp dialog corner, in place of Material's rectangles. */
private val OrganicShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The subset of [OrganicColors] that Material components read for themselves, so
 * ripples, dialog scrims and text defaults land on the palette without every call
 * site restating it.
 */
private fun OrganicColors.toMaterialScheme(dark: Boolean) = with(this) {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentBadge,
        onPrimaryContainer = accentStrong,
        secondary = protect,
        onSecondary = background,
        secondaryContainer = protectSoft,
        onSecondaryContainer = protectStrong,
        background = background,
        onBackground = ink,
        surface = background,
        onSurface = ink,
        surfaceVariant = surface,
        onSurfaceVariant = muted,
        surfaceContainerHigh = dialogSurface,
        outline = muted,
        outlineVariant = divider,
        // The palette has no red. Terracotta already carries the destructive weight
        // here, and introducing a second warning colour would only blur that.
        error = accentStrong,
        onError = background,
        scrim = scrim,
    )
}

/**
 * Dynamic colour is deliberately not offered: the palette is doing the safety work —
 * terracotta reads as destructive, sage as protected — and letting the wallpaper
 * repaint them would take the star and the trash to the same hue.
 */
@Composable
fun PairPurgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val organic = if (darkTheme) DarkOrganicColors else LightOrganicColors

    CompositionLocalProvider(LocalOrganicColors provides organic) {
        MaterialTheme(
            colorScheme = organic.toMaterialScheme(darkTheme),
            typography = Typography,
            shapes = OrganicShapes,
            content = content,
        )
    }
}

/** Shorthand for the palette at a call site: `PairPurgeTheme.colors.accentStrong`. */
object PairPurgeTheme {
    val colors: OrganicColors
        @Composable get() = LocalOrganicColors.current
}
