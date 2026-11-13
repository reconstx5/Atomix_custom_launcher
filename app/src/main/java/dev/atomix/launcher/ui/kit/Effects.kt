package dev.atomix.launcher.ui.kit

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How much visual effect to draw. Reduced effects (a manual setting, off by default) keeps layout and every function
 * but drops glass, shadows and animation; the focus ring stays.
 */
data class Effects(val reduced: Boolean) {
    val focusInMs: Int get() = if (reduced) 0 else 150
    val focusOutMs: Int get() = if (reduced) 0 else 100
    val crossfadeMs: Int get() = if (reduced) 0 else 250
    val glass: Boolean get() = !reduced
    val shadows: Boolean get() = !reduced
}

val LocalEffects = staticCompositionLocalOf { Effects(reduced = false) }
