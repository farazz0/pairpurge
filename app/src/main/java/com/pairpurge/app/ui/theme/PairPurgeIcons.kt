package com.pairpurge.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The design's icon set, drawn from its own path data.
 *
 * Material's icons are filled glyphs on a 24dp grid; these are open 2.75-weight
 * strokes, and the difference is not decoration — the bottom nav, the state badges
 * and the action bar all rely on that line weight to sit at the same visual density
 * as the Space Grotesk headings. Pulling in `material-icons-extended` to get a
 * Bluetooth glyph would have brought thousands of icons in the wrong style, so the
 * ten the app uses are transcribed here instead.
 *
 * Colour is left black on purpose: [androidx.compose.material3.Icon] paints over the
 * whole vector with its `tint`, so these follow the call site.
 */
object PairPurgeIcons {

    private const val STROKE = 2.75f

    /** The Bluetooth rune. Also the "Devices" tab. */
    val Bluetooth: ImageVector = stroked("Bluetooth", BLUETOOTH_RUNE)

    /** Bluetooth, struck through: the adapter is off. */
    val BluetoothOff: ImageVector = stroked("BluetoothOff", BLUETOOTH_RUNE, "M3 3l18 18")

    /** Protect. Outline while it is an offer, [StarFilled] once it is a fact. */
    val Star: ImageVector = stroked("Star", STAR_POINTS)

    val StarFilled: ImageVector = stroked("StarFilled", STAR_POINTS, filled = true)

    /** Unpair. */
    val Trash: ImageVector = stroked(
        "Trash",
        "M3 6h18",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    )

    val Settings: ImageVector = stroked(
        "Settings", "M4 7h16", "M4 17h16", "M9 4v6", "M15 14v6",
    )

    val Refresh: ImageVector = stroked(
        "Refresh",
        "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8",
        "M21 3v5h-5",
    )

    /** Two arrows head to tail: the sort control. */
    val Sort: ImageVector = stroked(
        "Sort",
        "m21 16-4 4-4-4",
        "M17 20V4",
        "m3 8 4-4 4 4",
        "M7 4v16",
    )

    /** The tick inside a selected row's box — a shade heavier so it holds at 14dp. */
    val Check: ImageVector = stroked("Check", "M20 6 9 17l-5-5", strokeWidth = 3.25f)

    /** Permission not yet granted. */
    val Lock: ImageVector = stroked(
        "Lock",
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
        "M7 11V7a5 5 0 0 1 10 0v4",
    )

    /** Permission refused for good. */
    val Alert: ImageVector = stroked(
        "Alert",
        "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20z",
        "M12 8v4",
        "M12 16h.01",
    )

    private fun stroked(
        name: String,
        vararg paths: String,
        filled: Boolean = false,
        strokeWidth: Float = STROKE,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

private const val BLUETOOTH_RUNE = "m7 7 10 10-5 5V2l5 5L7 17"

private const val STAR_POINTS =
    "M12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 " +
        "5.82 21.02 7 14.14 2 9.27 8.91 8.26Z"
