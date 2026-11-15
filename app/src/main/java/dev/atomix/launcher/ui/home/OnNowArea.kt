package dev.atomix.launcher.ui.home

import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.prefs.OnNowLayout
import dev.atomix.launcher.prefs.SMALL_ROWS
import dev.atomix.launcher.ui.settings.SettingsSection
import kotlin.math.max
import kotlin.math.min

/** A button under a message in the "On now" area. */
enum class OnNowAction(val label: String) {
    OpenSettings("Open Settings"),
    Retry("Try again"),
    Settings("Settings"),
    ChooseChannels("Choose channels"),
}

/** What the "On now" area of Home shows. */
sealed interface OnNowArea {
    /** Nothing yet: the first load is running, or the sign-in has just been found to be gone. */
    data object Blank : OnNowArea

    /** A message where the cards would be, with its buttons. */
    data class Message(val title: String, val text: String, val actions: List<OnNowAction>) : OnNowArea

    /** The channels that are on Home: the first as the large card, the rest as small cards, in order. */
    data class Cards(val large: Channel, val small: List<Channel>) : OnNowArea
}

/**
 * Decides the "On now" area from Live TV's state. With the server unreachable the message replaces the cards even
 * when channels are saved: none of them could be played.
 */
fun onNowArea(live: LiveState): OnNowArea = when (live) {
    LiveState.Loading, LiveState.SignedOut -> OnNowArea.Blank
    LiveState.NotConfigured -> OnNowArea.Message(
        "Live TV isn't set up on your TV server",
        "Your apps still work. Set up a Live TV source in Settings.",
        listOf(OnNowAction.OpenSettings),
    )
    is LiveState.Offline -> OnNowArea.Message(
        "Can't reach the TV server",
        "Your apps still work. Live TV comes back when the server does.",
        listOf(OnNowAction.Retry, OnNowAction.Settings),
    )
    is LiveState.Ready ->
        if (live.pinned.isEmpty()) OnNowArea.Message(
            "Choose channels for Home",
            "Turn on Show on Home for a channel in Live TV and it appears here.",
            listOf(OnNowAction.ChooseChannels),
        )
        else OnNowArea.Cards(live.pinned.first(), live.pinned.drop(1))
}

/** The Settings section a message's button opens; null for a button that opens Settings where it usually opens. */
fun OnNowAction.settingsSection(): SettingsSection? = if (this == OnNowAction.OpenSettings) SettingsSection.LiveTv else null

/**
 * Which small card is the upper of the two that show, once small card [small] (of [count]) must be one of them and
 * the window began at [start]. With [page], stepping onto the next card turns the page (two at a time, stopping at
 * the ends); otherwise the window moves just far enough.
 */
fun smallWindowStart(start: Int, small: Int, count: Int, page: Boolean): Int {
    val last = max(count - 2, 0)
    val from = start.coerceIn(0, last)
    return when {
        small < from -> if (page) min(max(from - 2, 0), small) else small
        small > from + 1 -> if (page) max(min(from + 2, last), small - 1) else small - 1
        else -> from
    }
}

/** Where a channel's card goes in "On now": the large card, or a small one by its row and column. */
sealed interface Slot {
    data object Large : Slot
    data class Small(val row: Int, val column: Int) : Slot
}

/** How many of the channels take a small card. */
private fun smallCount(channels: Int, layout: OnNowLayout): Int = max(channels - (if (layout.large) 1 else 0), 0)

/**
 * The slot of the channel at [index] of those on Home: the first is the large card when the layout has one, and the
 * rest fill the small slots row by row.
 */
fun slotOf(index: Int, layout: OnNowLayout): Slot {
    if (layout.large && index == 0) return Slot.Large
    val small = if (layout.large) index - 1 else index
    return Slot.Small(small / layout.columns, small % layout.columns)
}

/** The index, among the channels on Home, of the small card at [row] and [column]; it may be past the last channel. */
fun indexAt(row: Int, column: Int, layout: OnNowLayout): Int = (if (layout.large) 1 else 0) + row * layout.columns + column

/** How many rows of small cards [channels] channels make. */
fun smallRowCount(channels: Int, layout: OnNowLayout): Int = (smallCount(channels, layout) + layout.columns - 1) / layout.columns

/**
 * The first row of small cards that shows: [wanted], kept inside the rows there are. A channel that leaves Home while
 * the cards are moved down pulls the window back with it.
 */
fun firstRowFor(wanted: Int, channels: Int, layout: OnNowLayout): Int =
    wanted.coerceIn(0, max(smallRowCount(channels, layout) - SMALL_ROWS, 0))

/** The index of the last channel whose card shows, with the small cards starting at [firstRow]; -1 with no channels. */
fun lastShownIndex(firstRow: Int, channels: Int, layout: OnNowLayout): Int {
    if (channels <= 0) return -1
    val smallsShown = min(smallCount(channels, layout), (firstRow + SMALL_ROWS) * layout.columns)
    return smallsShown - 1 + (if (layout.large) 1 else 0)
}

/** A card's place in the cards area (px at 1920), and whether it is one of those that show. */
data class CardPlace(val x: Int, val y: Int, val w: Int, val h: Int, val shown: Boolean)

/**
 * Where the card in [slot] goes with the small cards starting at [firstRow]. A small card outside the rows that show
 * is placed where it would be (above or below the area) and is not shown.
 */
fun cardPlace(slot: Slot, firstRow: Int, m: HomeMetrics): CardPlace = when (slot) {
    Slot.Large -> CardPlace(0, 0, m.largeW, m.cardsH, true)
    is Slot.Small -> CardPlace(
        x = m.smallX + slot.column * (m.smallW + CARD_GAP),
        y = (slot.row - firstRow) * (m.smallH + CARD_GAP),
        w = m.smallW, h = m.smallH,
        shown = slot.row in firstRow until firstRow + SMALL_ROWS,
    )
}

/**
 * Whether the card in [slot] is built: those that show, and the row just above and just below them, which are there
 * unseen so that Up and Down have somewhere to go.
 */
fun isBuilt(slot: Slot, firstRow: Int): Boolean = slot !is Slot.Small || slot.row in firstRow - 1..firstRow + SMALL_ROWS

/**
 * Which channel's card focus lands on when it comes into "On now" from outside: the large card when the layout has
 * one; otherwise the card last focused ([remembered]) while it shows, else the first card showing. Null with no cards.
 */
fun onNowEntryIndex(remembered: Int?, firstRow: Int, channels: Int, layout: OnNowLayout): Int? {
    if (channels <= 0) return null
    if (layout.large) return 0
    if (remembered != null && remembered in 0 until channels && (slotOf(remembered, layout) as Slot.Small).row in firstRow until firstRow + SMALL_ROWS) return remembered
    return indexAt(firstRow, 0, layout).coerceAtMost(channels - 1)
}

/**
 * The line under the cards that says channels sit beyond the last one showing: "4 more channels", "1 more channel".
 * [lastShown] is [lastShownIndex]. Null when every channel on Home is showing.
 */
fun moreChannelsLine(channels: Int, lastShown: Int): String? {
    val more = channels - (lastShown + 1)
    return when {
        more <= 0 -> null
        more == 1 -> "1 more channel"
        else -> "$more more channels"
    }
}
