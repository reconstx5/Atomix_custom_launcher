package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.atomix.launcher.ui.theme.AtomixColors

/**
 * A panel that looks like frosted glass: the pre-blurred backdrop copy, stretched to the whole screen and clipped to
 * this panel, under a Deep Space tint and a 1 px light edge. With reduced effects (or no backdrop yet) it is a solid
 * dark panel with the same edge. [tint] ≥ 0.62 keeps light text readable over the brightest artwork.
 */
@Composable
fun GlassPanel(
    blurred: ImageBitmap?,
    modifier: Modifier = Modifier,
    shape: Shape,
    tint: Float = 0.62f,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalEffects.current.glass && blurred != null
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val surface = if (glass) {
        Modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                drawImage(
                    blurred!!,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(blurred.width, blurred.height),
                    dstOffset = IntOffset(-origin.x.toInt(), -origin.y.toInt()),
                    dstSize = IntSize(view.width, view.height),
                )
                drawRect(AtomixColors.DeepSpace.copy(alpha = tint))
            }
    } else {
        Modifier.drawBehind { drawRect(AtomixColors.SolidPanel) }
    }
    Box(modifier.clip(shape).then(surface).border(1.dp, AtomixColors.Edge, shape), content = content)
}
