package dev.atomix.launcher.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import dev.atomix.launcher.ui.theme.px

/**
 * The launcher's one focus treatment: a 6 px white ring just outside the item, a gentle scale and (full effects
 * only) a soft dark halo. 150 ms in, 100 ms out; instant with reduced effects.
 */
@Composable
fun Modifier.tvFocus(focused: Boolean, scale: Float = 1.08f, cornerPx: Float = 16f): Modifier {
    val effects = LocalEffects.current
    val target = if (focused) scale else 1f
    val ms = if (focused) effects.focusInMs else effects.focusOutMs
    val s by animateFloatAsState(target, if (ms == 0) tween(0) else tween(ms), label = "focus")
    val density = LocalDensity.current
    val ring = with(density) { px(6).toPx() }
    val gap = with(density) { px(4).toPx() }
    val corner = with(density) { px(cornerPx).toPx() }
    val halo = with(density) { px(18).toPx() }
    val shadows = effects.shadows
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .drawWithContent {
            if (focused && shadows) {
                for (i in 3 downTo 1) {
                    val e = halo * i / 3
                    drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(-e, -e + halo / 3), Size(size.width + 2 * e, size.height + 2 * e), CornerRadius(corner + e))
                }
            }
            drawContent()
            if (focused) {
                val r = gap + ring / 2
                drawRoundRect(Color.White, Offset(-r, -r), Size(size.width + 2 * r, size.height + 2 * r), CornerRadius(corner + r), style = Stroke(ring))
            }
        }
}
