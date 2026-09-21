package com.pairpurge.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pairpurge.app.R

/**
 * Both families ship as single variable fonts, so every weight is one instance of the
 * same file with its `wght` axis pinned. The [FontWeight] is what the font matcher
 * selects on; the variation is what the renderer actually draws, and they have to
 * agree or a request for 600 resolves to a synthetically-bolded 400.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Headings and buttons. Only the bold weight is used. */
val SpaceGrotesk = FontFamily(variableFont(R.font.space_grotesk, 700))

/** Everything else: regular for prose, 600 for list rows, 700 for nav labels. */
val Figtree = FontFamily(
    variableFont(R.font.figtree, 400),
    variableFont(R.font.figtree, 600),
    variableFont(R.font.figtree, 700),
)

// Compose leaves the font's own ascent/descent padding on by default, which adds a few
// stray pixels above every line and would break the tight measurements the design
// works in. Material3 disables it for its own defaults; these are hand-built, so they
// have to say so themselves.
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun heading(size: Int, lineHeight: Int) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.W700,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = (-0.015).em,
    platformStyle = NoFontPadding,
)

private fun body(size: Double, lineHeight: Int, weight: FontWeight = FontWeight.W400) = TextStyle(
    fontFamily = Figtree,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    platformStyle = NoFontPadding,
)

val Typography = Typography(
    // State-screen headlines.
    displaySmall = heading(size = 30, lineHeight = 34),
    // Dialog titles.
    headlineMedium = heading(size = 25, lineHeight = 28),
    // The page title in the header.
    headlineSmall = heading(size = 24, lineHeight = 28),

    // Device name in a list row.
    titleMedium = body(size = 16.0, lineHeight = 20, weight = FontWeight.W600),
    // The count line above the list.
    titleSmall = body(size = 15.0, lineHeight = 20, weight = FontWeight.W600),

    // State-screen prose.
    bodyLarge = body(size = 15.0, lineHeight = 23),
    // Dialog prose.
    bodyMedium = body(size = 14.5, lineHeight = 22),
    // The blurb under the protected-list title.
    bodySmall = body(size = 13.5, lineHeight = 20),

    // Pill buttons.
    labelLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W700,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        platformStyle = NoFontPadding,
    ),
    // In-row text actions: Unpair, Remove, Clear.
    labelMedium = body(size = 13.5, lineHeight = 18, weight = FontWeight.W600),
    // Bottom-nav labels.
    labelSmall = body(size = 11.5, lineHeight = 14, weight = FontWeight.W700),
)

/**
 * MAC addresses, monospaced so the octets line up down the column — which is what
 * makes checking the list against system settings practical rather than a word search.
 */
val MacAddressTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.5.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.02.em,
    platformStyle = NoFontPadding,
)
