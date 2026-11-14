package dev.atomix.launcher.livetv.player

import dev.atomix.launcher.livetv.source.PlayReply
import dev.atomix.launcher.livetv.source.tv
import dev.atomix.launcher.ui.kit.HintSpot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerLogicTest {
    private val list = listOf(tv("a", 1), tv("b", 2), tv("c", 3), tv("d", 12))

    // Zapping

    @Test fun threeQuickPresses_tuneOnce_toTheThirdChannel() = runTest {
        val tuned = mutableListOf<String>()
        val zap = ZapQueue(backgroundScope, delayMs = 500) { tuned += it.id }
        var shown = zap.step(list, list[0], +1)
        advanceTimeBy(200); shown = zap.step(list, shown, +1)
        advanceTimeBy(200); shown = zap.step(list, shown, +1)
        assertEquals("d", shown.id)
        advanceTimeBy(499); runCurrent()
        assertEquals(emptyList<String>(), tuned)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf("d"), tuned)
    }

    @Test fun step_withOnlyOneChannel_staysPutAndDoesNotRetune() = runTest {
        val tuned = mutableListOf<String>()
        val one = listOf(tv("a", 1))
        val zap = ZapQueue(backgroundScope, delayMs = 500) { tuned += it.id }
        assertEquals("a", zap.step(one, one[0], +1).id)
        assertEquals("a", zap.step(one, one[0], -1).id)
        advanceTimeBy(1_000); runCurrent()
        assertEquals("the picture is not thrown away to re-tune the same channel", emptyList<String>(), tuned)
    }

    @Test fun step_wrapsAroundBothWays() = runTest {
        val zap = ZapQueue(backgroundScope) {}
        assertEquals("a", zap.step(list, list.last(), +1).id)
        assertEquals("d", zap.step(list, list.first(), -1).id)
    }

    @Test fun step_fromChannelNotInList_startsAtTheEnds() = runTest {
        val zap = ZapQueue(backgroundScope) {}
        assertEquals("a", zap.step(list, tv("zz"), +1).id)
        assertEquals("d", zap.step(list, tv("zz"), -1).id)
    }

    @Test fun zapList_pinnedOrAllWhenNothingPinned() {
        assertEquals(list, zapList(ZapSource.Pinned, emptyList(), list))
        assertEquals(list.take(2), zapList(ZapSource.Pinned, list.take(2), list))
        assertEquals(list, zapList(ZapSource.All, list.take(2), list))
    }

    // Layers

    @Test fun back_closesTopLayerInOrder_thenFalse() {
        val layers = Layers()
        layers.show(Layer.Banner); layers.show(Layer.Controls); layers.show(Layer.ChannelList); layers.show(Layer.NumberEntry)
        assertEquals(Layer.NumberEntry, layers.top())
        assertTrue(layers.back()); assertEquals(Layer.ChannelList, layers.top())
        assertTrue(layers.back()); assertEquals(Layer.Controls, layers.top())
        assertTrue(layers.back()); assertEquals(Layer.Banner, layers.top())
        assertTrue(layers.back()); assertNull(layers.top())
        assertFalse(layers.back())
    }

    @Test fun top_followsPriorityNotShowOrder() {
        val layers = Layers()
        layers.show(Layer.ChannelList); layers.show(Layer.Banner)
        assertEquals(Layer.ChannelList, layers.top())
        layers.hide(Layer.ChannelList)
        assertEquals(Layer.Banner, layers.top())
    }

    // Controls

    @Test fun controls_onlyForRewindableDashWithAMinuteOfBuffer() {
        val dash = PlayReply.Dash("s", null, null, null, "/m.mpd", rewind = true)
        assertFalse(controlsSupported(dash, 59_999))
        assertTrue(controlsSupported(dash, 60_000))
        assertFalse(controlsSupported(dash, null))
        assertFalse(controlsSupported(dash.copy(rewind = false), 600_000))
        assertFalse(controlsSupported(PlayReply.Stream("s", null, null, null, "/x.m3u8", "hls"), 600_000))
    }

    // Retry

    @Test fun retry_delaysThenGivesUp() {
        val p = RetryPolicy()
        assertEquals(1_000L, p.nextDelay(0, 0))
        assertEquals(3_000L, p.nextDelay(1, 1_000))
        assertEquals(8_000L, p.nextDelay(2, 4_000))
        assertNull(p.nextDelay(3, 12_000))
    }

    @Test fun retry_givesUpOnceTwentySecondsHavePassed() {
        assertNull(RetryPolicy().nextDelay(0, 20_000))
        assertNull(RetryPolicy().nextDelay(1, 25_000))
    }

    // Number entry

    @Test fun numberEntry_commitsAfterQuietPeriod() = runTest {
        val committed = mutableListOf<Int>()
        val entry = NumberEntry(backgroundScope, commitMs = 1_500) { committed += it }
        entry.digit(1); advanceTimeBy(1_000); entry.digit(2)
        assertEquals("12", entry.typed.value)
        advanceTimeBy(1_499); runCurrent()
        assertEquals(emptyList<Int>(), committed)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf(12), committed)
        assertEquals("", entry.typed.value)
    }

    @Test fun numberEntry_ignoresAFifthDigit() = runTest {
        val committed = mutableListOf<Int>()
        val entry = NumberEntry(backgroundScope) { committed += it }
        listOf(1, 2, 3, 4, 5).forEach(entry::digit)
        assertEquals("1234", entry.typed.value)
        advanceTimeBy(1_501); runCurrent()
        assertEquals(listOf(1234), committed)
    }

    @Test fun findByNumber_matchesOrNull() {
        assertEquals("d", findByNumber(list, 12)?.id)
        assertNull(findByNumber(list, 99))
    }

    @Test fun numberMatch_addsTheChannelOnlyOnAMatch() {
        val list = listOf(tv("a", 2), tv("b", 20))
        assertEquals("a", numberMatch("2", list)?.id)
        assertEquals("b", numberMatch("20", list)?.id)
        assertEquals("a", numberMatch("02", list)?.id)
        assertNull(numberMatch("", list)); assertNull(numberMatch("0", list)); assertNull(numberMatch("9999", list))
    }

    @Test fun playerSpot_followsWhatIsOnScreen() {
        val banner = setOf(Layer.Banner, Layer.Controls)
        assertEquals(HintSpot.PlayerList("TVNZ 2"), playerSpot(null, false, setOf(Layer.ChannelList), "TVNZ 2"))
        assertEquals(HintSpot.Pill("Close"), playerSpot(null, false, setOf(Layer.ChannelList), null))
        assertEquals(HintSpot.PlayerError("Try again", bannerUp = false), playerSpot("Try again", false, emptySet(), null))
        assertEquals(HintSpot.PlayerError("Next channel", bannerUp = true), playerSpot("Next channel", false, banner, null))
        assertEquals(HintSpot.PlayerBanner, playerSpot(null, true, banner, null))     // Back hides the banner first
        assertEquals(HintSpot.PlayerTuning, playerSpot(null, true, emptySet(), null))
        assertEquals(HintSpot.PlayerBanner, playerSpot(null, false, banner, null))
        assertNull(playerSpot(null, false, emptySet(), null))                          // a clean picture has nothing over it
        assertNull(playerSpot(null, false, setOf(Layer.NumberEntry), null))
    }

    @Test fun liveBadge_onlyWhilePlayingAtTheLiveEdge() {
        assertTrue(isLiveNow(LiveInfo(paused = false, behindMs = 0)))
        assertTrue(isLiveNow(LiveInfo(paused = false, behindMs = 10_000)))
        assertFalse(isLiveNow(LiveInfo(paused = false, behindMs = 10_001)))
        assertFalse(isLiveNow(LiveInfo(paused = true, behindMs = 0)))
    }
}
