package dev.atomix.launcher.ui.home

import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.LiveState
import org.junit.Assert.assertEquals
import org.junit.Test

class OnNowAreaTest {
    private fun ch(id: String) = Channel(id = id, name = "Ch $id")
    private fun ready(vararg ids: String) = LiveState.Ready(ids.map(::ch), emptyList())

    @Test fun firstChannelIsLarge_theRestAreSmall_inOrder() {
        val a = onNowArea(ready("a", "b", "c", "d", "e")) as OnNowArea.Cards
        assertEquals("a", a.large.id)
        assertEquals(listOf("b", "c", "d", "e"), a.small.map { it.id })
    }

    @Test fun oneChannel_hasNoSmallCards() =
        assertEquals(emptyList<Channel>(), (onNowArea(ready("a")) as OnNowArea.Cards).small)

    @Test fun nothingOnHome_asksToChoose() = assertEquals(
        OnNowArea.Message("Choose channels for Home", "Turn on Show on Home for a channel in Live TV and it appears here.", listOf(OnNowAction.ChooseChannels)),
        onNowArea(ready()))

    @Test fun serverOff_saysSo_evenWithChannelsSaved() = assertEquals(
        OnNowArea.Message("Can't reach the TV server", "Your apps still work. Live TV comes back when the server does.", listOf(OnNowAction.Retry, OnNowAction.Settings)),
        onNowArea(LiveState.Offline(listOf(ch("a")))))

    @Test fun notSetUp_pointsAtSettings() = assertEquals(
        OnNowArea.Message("Live TV isn't set up on your TV server", "Your apps still work. Set up a Live TV source in Settings.", listOf(OnNowAction.OpenSettings)),
        onNowArea(LiveState.NotConfigured))

    @Test fun loadingAndSignedOut_drawNothing() {
        assertEquals(OnNowArea.Blank, onNowArea(LiveState.Loading))
        assertEquals(OnNowArea.Blank, onNowArea(LiveState.SignedOut))
    }
}
