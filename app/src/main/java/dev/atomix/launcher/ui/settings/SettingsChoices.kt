package dev.atomix.launcher.ui.settings

import dev.atomix.launcher.prefs.AccentId
import dev.atomix.launcher.prefs.ClockChoice
import dev.atomix.launcher.prefs.FocusStyle
import dev.atomix.launcher.prefs.HomeLayout
import dev.atomix.launcher.prefs.Look
import dev.atomix.launcher.prefs.TextSize
import dev.atomix.launcher.prefs.ThemeId
import dev.atomix.launcher.prefs.shownClock

/** Everything the owner has chosen in Settings that a row can show or change. */
data class Chosen(val look: Look, val layout: HomeLayout = HomeLayout())

/** How a row is worked with the remote. */
enum class RowKind {
    /** States something; cannot take focus. */
    Statement,
    /** OK does something (opens a page, starts a search). */
    Action,
    /** OK turns it on or off. */
    Switch,
    /** Left and Right step through its choices in the row (pills, colour dots). */
    Stepped,
    /** OK opens its choices as a short list on the row. */
    List,
}

/** [reduced] is whether Reduced effects is on: Focus highlight then has nothing to choose and is a statement. */
fun kindOf(item: SettingsItem, reduced: Boolean = false): RowKind = when (item) {
    SettingsItem.Server, SettingsItem.Version -> RowKind.Statement
    SettingsItem.ReducedEffects -> RowKind.Switch
    SettingsItem.Accent, SettingsItem.TextSize, SettingsItem.Clock -> RowKind.Stepped
    SettingsItem.Theme -> RowKind.List
    SettingsItem.FocusHighlight -> if (reduced) RowKind.Statement else RowKind.List
    SettingsItem.HomeButton, SettingsItem.LiveSources, SettingsItem.TestChannel, SettingsItem.ManageSources,
    SettingsItem.FindServer, SettingsItem.SwitchProfile, SettingsItem.TvPages -> RowKind.Action
}

/** The words of an item's choices, in order; empty for an item that is not a choice between several things. */
fun choiceOptions(item: SettingsItem): List<String> = when (item) {
    SettingsItem.Theme -> ThemeId.entries.map { it.label }
    SettingsItem.Accent -> AccentId.entries.map { it.label }
    SettingsItem.TextSize -> TextSize.entries.map { it.label }
    SettingsItem.Clock -> ClockChoice.entries.map { it.label }
    SettingsItem.FocusHighlight -> FocusStyle.entries.map { it.label }
    else -> emptyList()
}

/**
 * Which of [choiceOptions] is the one chosen now; -1 for an item that has none. [tvIs24] is the TV's own clock
 * setting, which the Clock row shows as chosen until the owner chooses.
 */
fun chosenIndex(item: SettingsItem, now: Chosen, tvIs24: Boolean = true): Int = when (item) {
    SettingsItem.Theme -> now.look.theme.ordinal
    SettingsItem.Accent -> now.look.accent.ordinal
    SettingsItem.TextSize -> now.look.textSize.ordinal
    SettingsItem.Clock -> shownClock(now.look.clock, tvIs24).ordinal
    SettingsItem.FocusHighlight -> now.look.focus.ordinal
    else -> -1
}

/** [now] with choice [index] of [item] made. An index the item does not have changes nothing. */
fun withChoice(item: SettingsItem, index: Int, now: Chosen): Chosen {
    if (index !in choiceOptions(item).indices) return now
    return when (item) {
        SettingsItem.Theme -> now.copy(look = now.look.copy(theme = ThemeId.entries[index]))
        SettingsItem.Accent -> now.copy(look = now.look.copy(accent = AccentId.entries[index]))
        SettingsItem.TextSize -> now.copy(look = now.look.copy(textSize = TextSize.entries[index]))
        SettingsItem.Clock -> now.copy(look = now.look.copy(clock = ClockChoice.entries[index]))
        SettingsItem.FocusHighlight -> now.copy(look = now.look.copy(focus = FocusStyle.entries[index]))
        else -> now
    }
}

/** What Left and Right do on a stepped row, for the hint bar; null for a row they do not change. */
fun stepHintFor(item: SettingsItem): String? = when (item) {
    SettingsItem.Accent -> "Pick a colour"
    SettingsItem.TextSize -> "Change size"
    SettingsItem.Clock -> "Change"
    else -> null
}

/** One labelled group of rows in a section's pane. */
data class PaneGroup(val label: String, val items: List<SettingsItem>)

/** The groups of a section whose pane is drawn from them, top to bottom; empty for the other sections. */
fun groupsOf(section: SettingsSection): List<PaneGroup> = when (section) {
    SettingsSection.Appearance -> listOf(
        PaneGroup("LOOK", listOf(SettingsItem.Theme, SettingsItem.Accent, SettingsItem.TextSize, SettingsItem.Clock)),
        PaneGroup("FOCUS AND MOTION", listOf(SettingsItem.FocusHighlight, SettingsItem.ReducedEffects)),
    )
    else -> emptyList()
}

/** A row is 92 high and rows are 12 apart; a group's label takes 36; a new group starts 72 under the last row. */
private const val ROW_PITCH = 92 + 12
private const val LABEL_H = 36
private const val GROUP_GAP = 72

/** Where a row's top is in its pane (px at 1920, from the pane's top), for placing its list of choices; null if not there. */
fun rowTop(item: SettingsItem, groups: List<PaneGroup>): Int? {
    var y = 0
    groups.forEachIndexed { g, group ->
        y += if (g == 0) LABEL_H else GROUP_GAP - 12
        group.items.forEach { if (it == item) return y; y += ROW_PITCH }
    }
    return null
}

/** A row's name, as its row and the head of its list of choices show it. */
fun titleOf(item: SettingsItem): String = when (item) {
    SettingsItem.Theme -> "Theme"
    SettingsItem.Accent -> "Accent colour"
    SettingsItem.TextSize -> "Text size"
    SettingsItem.Clock -> "Clock"
    SettingsItem.FocusHighlight -> "Focus highlight"
    SettingsItem.ReducedEffects -> "Reduced effects"
    else -> ""
}
