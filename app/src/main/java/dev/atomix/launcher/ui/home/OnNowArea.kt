package dev.atomix.launcher.ui.home

import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveState

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
