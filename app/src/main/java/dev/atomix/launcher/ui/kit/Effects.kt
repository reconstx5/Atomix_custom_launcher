package dev.atomix.launcher.ui.kit

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How much visual effect to draw. Reduced effects (a manual setting, off by default) keeps layout and every function
 * but drops the focus glow and all animation; the focus ring stays.
 */
data class Effects(val reduced: Boolean, val glowWanted: Boolean = true) {
    /** The glow shows with full effects and "Ring and glow" chosen in Settings ([glowWanted]). */
    val glow: Boolean get() = !reduced && glowWanted
    /** How long a transition may take; 0 means no animation at all. */
    val motionMs: Int get() = if (reduced) 0 else 150
}

val LocalEffects = staticCompositionLocalOf { Effects(reduced = false) }
