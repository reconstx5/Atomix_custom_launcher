package dev.atomix.launcher.prefs

/** The ground and surfaces the launcher is drawn in. Their colours are in ui/theme/Palettes.kt. */
enum class ThemeId(val label: String) { Midnight("Midnight"), Black("Black"), Graphite("Graphite"), Plum("Plum") }

/** The colour of the focus ring and glow, the tab underline, progress bars, the chosen pill and a switch that is on. */
enum class AccentId(val label: String, val argb: Long) {
    Ice("Ice", 0xFFA9DCFB),
    Mint("Mint", 0xFF9BEBBB),
    Amber("Amber", 0xFFFFD08A),
    Coral("Coral", 0xFFFF9E8F),
    Violet("Violet", 0xFFC7B6FF),
}

/** How big text is drawn. Large changes text only; boxes, margins and positions stay where they are. */
enum class TextSize(val label: String, val scale: Float) { Standard("Standard", 1f), Large("Large", 1.15f) }

enum class ClockChoice(val label: String) { H24("24-hour"), H12("12-hour"), Hidden("Hidden") }

enum class FocusStyle(val label: String) { RingAndGlow("Ring and glow"), RingOnly("Ring only") }

/**
 * How the launcher looks. The defaults are the look of the approved boards. [clock] is null until the owner chooses:
 * the launcher then follows the TV's own 12- or 24-hour setting.
 */
data class Look(
    val theme: ThemeId = ThemeId.Midnight,
    val accent: AccentId = AccentId.Ice,
    val textSize: TextSize = TextSize.Standard,
    val clock: ClockChoice? = null,
    val focus: FocusStyle = FocusStyle.RingAndGlow,
)

/** The enum value saved under [name], or [default] when nothing is saved or the name is not one this build knows. */
inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default
