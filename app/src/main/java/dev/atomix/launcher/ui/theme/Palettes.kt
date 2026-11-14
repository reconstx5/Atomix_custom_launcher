package dev.atomix.launcher.ui.theme

import dev.atomix.launcher.prefs.AccentId
import dev.atomix.launcher.prefs.ThemeId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A theme's colours, as opaque ARGB. Text, the status colours, the logo plate and the accent are not here: they are
 * the same in every theme. [wash] and [playerWash] are the three stops of the two full-screen backgrounds;
 * [overVideo] is the colour of the panels drawn over a playing picture, before their alpha.
 */
data class Palette(
    val ground: Long,
    val wash: List<Long>,
    val tile: Long,
    val raised: Long,
    val panel: Long,
    val inset: Long,
    val keyCap: Long,
    val roundButton: Long,
    val card: Long,
    val switchOff: Long,
    val label: Long,
    val overVideo: Long,
    val playerWash: List<Long>,
)

private val MIDNIGHT = Palette(
    ground = 0xFF0A1322, wash = listOf(0xFF14263F, 0xFF0B1626, 0xFF070E1A),
    tile = 0xFF16243A, raised = 0xFF1B2C46, panel = 0xFF101B2C, inset = 0xFF0E1828, keyCap = 0xFF24344D,
    roundButton = 0xFF1A2638, card = 0xFF111D2E, switchOff = 0xFF2A3A52, label = 0xFF9FB0C6,
    overVideo = 0xFF0A1220, playerWash = listOf(0xFF0F1C30, 0xFF070E1A, 0xFF05090F),
)

private val BLACK = Palette(
    ground = 0xFF000000, wash = listOf(0xFF0C0E12, 0xFF050607, 0xFF000000),
    tile = 0xFF15171B, raised = 0xFF1F2228, panel = 0xFF0E0F12, inset = 0xFF0B0C0F, keyCap = 0xFF262A31,
    roundButton = 0xFF17191D, card = 0xFF101215, switchOff = 0xFF2C3038, label = 0xFF98A1AD,
    overVideo = 0xFF060709, playerWash = listOf(0xFF0A0B0E, 0xFF030304, 0xFF000000),
)

private val GRAPHITE = Palette(
    ground = 0xFF14171C, wash = listOf(0xFF262B34, 0xFF181B21, 0xFF101216),
    tile = 0xFF262B34, raised = 0xFF303642, panel = 0xFF1C2027, inset = 0xFF191C22, keyCap = 0xFF363C48,
    roundButton = 0xFF262B34, card = 0xFF1C2027, switchOff = 0xFF3A414D, label = 0xFFA6AFBB,
    overVideo = 0xFF101216, playerWash = listOf(0xFF1A1D23, 0xFF101216, 0xFF0A0B0E),
)

private val PLUM = Palette(
    ground = 0xFF150F1E, wash = listOf(0xFF2C1E40, 0xFF191124, 0xFF0E0A15),
    tile = 0xFF271C38, raised = 0xFF322447, panel = 0xFF1B1428, inset = 0xFF1A1326, keyCap = 0xFF38294F,
    roundButton = 0xFF271C38, card = 0xFF1D152A, switchOff = 0xFF3B2D52, label = 0xFFAEA3C2,
    overVideo = 0xFF110C18, playerWash = listOf(0xFF1C1329, 0xFF0E0A15, 0xFF09060E),
)

fun paletteFor(theme: ThemeId): Palette = when (theme) {
    ThemeId.Midnight -> MIDNIGHT
    ThemeId.Black -> BLACK
    ThemeId.Graphite -> GRAPHITE
    ThemeId.Plum -> PLUM
}

private fun channel(argb: Long, shift: Int): Int = ((argb shr shift) and 0xFF).toInt()

/**
 * The focus ring for an accent: the accent moved about a quarter of the way to white, so the ring reads as a line
 * around the accent's own glow. Ice keeps the exact ring of the approved boards.
 */
fun ringArgb(accent: AccentId): Long {
    if (accent == AccentId.Ice) return 0xFFBFE6FF
    fun lift(shift: Int): Long { val c = channel(accent.argb, shift); return (c + (255 - c) * 0.26).roundToInt().toLong() shl shift }
    return 0xFF000000 or lift(16) or lift(8) or lift(0)
}

/** WCAG relative luminance of an opaque colour. */
private fun luminance(argb: Long): Double {
    fun linear(shift: Int): Double { val c = channel(argb, shift) / 255.0; return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4) }
    return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
}

/** The WCAG contrast ratio of two opaque colours, 1 to 21; the order does not matter. */
fun contrast(a: Long, b: Long): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
