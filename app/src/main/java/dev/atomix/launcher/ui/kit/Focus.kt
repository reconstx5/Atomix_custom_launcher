package dev.atomix.launcher.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import dev.atomix.launcher.ui.theme.AtomixColors
import dev.atomix.launcher.ui.theme.px

/** How far the focus glow reaches beyond the ring (px at 1920) and how strong it starts. */
enum class FocusGlow(val reach: Int, val alpha: Float) { Card(30, 0.42f), Row(24, 0.40f) }

/**
 * The launcher's one focus treatment: a 4 px ring just outside the item and, with full effects, a soft glow beyond it.
 * No scale and no animation. The caller changes its own surface to [AtomixColors.Raised].
 *
 * The glow is four concentric outlines of falling strength rather than a blur, which this TV cannot afford.
 */
@Composable
fun Modifier.tvFocus(focused: Boolean, cornerPx: Float = 18f, glow: FocusGlow = FocusGlow.Row): Modifier {
    if (!focused) return this
    val showGlow = LocalEffects.current.glow
    val density = LocalDensity.current
    val ring = with(density) { px(4).toPx() }
    val corner = with(density) { px(cornerPx).toPx() }
    val band = with(density) { px(glow.reach / 4f).toPx() }
    val strength = glow.alpha
    return drawWithContent {
        if (showGlow) {
            for (i in 1..4) {
                val e = ring + (i - 0.5f) * band
                drawRoundRect(
                    AtomixColors.Ice.copy(alpha = strength * (5 - i) / 10f),
                    Offset(-e, -e), Size(size.width + 2 * e, size.height + 2 * e), CornerRadius(corner + e), style = Stroke(band),
                )
            }
        }
        drawContent()
        val r = ring / 2
        drawRoundRect(AtomixColors.Ring, Offset(-r, -r), Size(size.width + 2 * r, size.height + 2 * r), CornerRadius(corner + r), style = Stroke(ring))
    }
}
