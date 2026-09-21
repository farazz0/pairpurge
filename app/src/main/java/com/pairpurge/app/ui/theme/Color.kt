package com.pairpurge.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Organic palette from the design doc, in the roles the screens actually use.
 *
 * Material's own scheme carries only part of this: there is no M3 slot for "the tint a
 * selected row takes" or "the colour that means protected", and mapping them onto
 * tertiary/error would make the call sites lie about what they mean. So the full set
 * lives here and [androidx.compose.material3.ColorScheme] mirrors the parts M3
 * components read for themselves (ripples, text defaults, the dialog scrim).
 */
@Immutable
data class OrganicColors(
    /** Page ground, and the colour drawn on top of [accent]. */
    val background: Color,
    /** Raised-but-quiet fill: sort pill, skeleton bars, neutral state badges. */
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val divider: Color,
    /** Border of an unticked selection box — heavier than [divider], which is hairline. */
    val checkboxOutline: Color,
    /** Terracotta. Destructive-adjacent: fills the selection box and the Unpair button. */
    val accent: Color,
    val onAccent: Color,
    /** Terracotta as *text* on the page ground, where [accent] itself is too light. */
    val accentStrong: Color,
    /** The wash a selected row takes. */
    val accentSoft: Color,
    /** Behind an icon in a state badge or an active nav pill. */
    val accentBadge: Color,
    /** Sage. Means protected, so it never reads as a destructive action. */
    val protect: Color,
    val protectSoft: Color,
    val protectStrong: Color,
    /** Dialogs sit a shade off the page so the scrim has something to separate. */
    val dialogSurface: Color,
    val scrim: Color,
)

val LightOrganicColors = OrganicColors(
    background = Color(0xFFF5EAD8),
    surface = Color(0xFFEBDDC5),
    ink = Color(0xFF201E1D),
    muted = Color(0xFF645C50),
    divider = Color(0x29201E1D),
    checkboxOutline = Color(0x59201E1D),
    accent = Color(0xFFC67139),
    onAccent = Color(0xFFF5EAD8),
    accentStrong = Color(0xFF8C491A),
    accentSoft = Color(0xFFFFF2EB),
    accentBadge = Color(0xFFFFE1D0),
    protect = Color(0xFF7A8A5E),
    protectSoft = Color(0xFFF0FAE1),
    protectStrong = Color(0xFF56633F),
    dialogSurface = Color(0xFFF9F4ED),
    scrim = Color(0x73201E1D),
)

/**
 * Warm neutral-900 rather than grey, per the dark-mode turn: terracotta lifts to a
 * 400 so it holds against the dark ground, and selected rows tint with a 900.
 *
 * The turn only drew the device list, so the tones it never showed — dialog ground,
 * badge fills, the sage pair — are carried over at the same relationship to the
 * ground that the light palette uses.
 */
val DarkOrganicColors = OrganicColors(
    background = Color(0xFF2E2B25),
    surface = Color(0xFF474238),
    ink = Color(0xFFF9F4ED),
    muted = Color(0xFFC0B6A5),
    divider = Color(0x24F9F4ED),
    checkboxOutline = Color(0x66F9F4ED),
    accent = Color(0xFFF6A06B),
    onAccent = Color(0xFF402310),
    accentStrong = Color(0xFFF6A06B),
    accentSoft = Color(0xFF402310),
    accentBadge = Color(0xFF4A2A14),
    protect = Color(0xFFAEBF92),
    protectSoft = Color(0xFF363D2B),
    protectStrong = Color(0xFFAEBF92),
    dialogSurface = Color(0xFF3A352D),
    scrim = Color(0x8C000000),
)

val LocalOrganicColors = staticCompositionLocalOf { LightOrganicColors }
