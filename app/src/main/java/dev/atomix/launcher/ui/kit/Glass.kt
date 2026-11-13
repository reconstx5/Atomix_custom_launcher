package dev.atomix.launcher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import dev.atomix.launcher.ui.theme.AtomixColors

/** A solid panel. The frosted-glass drawing is gone; this goes too once its last callers are rebuilt (Task 9). */
@Suppress("UNUSED_PARAMETER")
@Composable
fun GlassPanel(
    blurred: ImageBitmap?,
    modifier: Modifier = Modifier,
    shape: Shape,
    tint: Float = 0.62f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.clip(shape).background(AtomixColors.Panel), content = content)
}
