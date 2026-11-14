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

/**
 * Whether times are written in 24-hour form. A choice of 24- or 12-hour wins; until the owner chooses, and with the
 * clock hidden, the TV's own setting ([tvIs24]) decides, as it always has.
 */
fun clockIs24(choice: ClockChoice?, tvIs24: Boolean): Boolean = when (choice) {
    ClockChoice.H24 -> true
    ClockChoice.H12 -> false
    ClockChoice.Hidden, null -> tvIs24
}

/** The choice the Clock row shows as made: the owner's, or the TV's own format while they have not chosen. */
fun shownClock(choice: ClockChoice?, tvIs24: Boolean): ClockChoice = choice ?: if (tvIs24) ClockChoice.H24 else ClockChoice.H12

/** Hidden takes the clock off the top bar; nothing else does. */
fun topBarShowsClock(choice: ClockChoice?): Boolean = choice != ClockChoice.Hidden

/** What the Focus highlight row says and whether it can be changed. */
data class FocusRow(val value: String, val description: String?, val canFocus: Boolean)

/**
 * Reduced effects removes the glow whatever is chosen here, so while it is on the row states "Ring only", says why,
 * and cannot take focus: a choice that would change nothing is not offered.
 */
fun focusHighlightRow(style: FocusStyle, reduced: Boolean): FocusRow =
    if (reduced) FocusRow(FocusStyle.RingOnly.label, "Reduced effects is on", canFocus = false)
    else FocusRow(style.label, null, canFocus = true)
