package dev.atomix.launcher.livetv.player

import dev.atomix.launcher.livetv.source.FakeLiveClient
import dev.atomix.launcher.livetv.source.PlayCaps
import dev.atomix.launcher.livetv.source.PlayReply
import dev.atomix.launcher.livetv.source.tv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSessionTest {
    private val caps = PlayCaps(listOf("h264"), listOf("aac"), listOf("mp4"), hls = true)
    private val a = tv("a", 1)
    private val b = tv("b", 2)
    private val client = FakeLiveClient(channels = listOf(a, b))

    private fun dash(id: String) = PlayReply.Dash(id, null, null, null, "/m/$id.mpd", rewind = true)

    private fun TestScope.session(widevine: Boolean = true, firstPictureMs: Long = 20_000) =
        PlayerSession(client, caps, backgroundScope, isWidevineSupported = { widevine }, now = { testScheduler.currentTime }, firstPictureMs = firstPictureMs)

    private fun plays() = client.calls.filter { it.startsWith("play") }
    private fun pings() = client.calls.filter { it.startsWith("ping") }

    @Test fun tune_pingsEvery30s_whilePlaying() = runTest {
        client.playReplies.addLast { dash("s1") }
        val s = session()
        s.tune(a); runCurrent(); s.onPlayerReady()
        assertEquals(SessionState.Playing(a, dash("s1")), s.state.value)
        advanceTimeBy(29_999); runCurrent()
        assertEquals(0, pings().size)
        advanceTimeBy(2); runCurrent()
        assertEquals(listOf("ping s1"), pings())
        advanceTimeBy(30_000); runCurrent()
        assertEquals(2, pings().size)
    }

    @Test fun tuneAnother_sendsReplacesWithPreviousSessionId_withoutSeparateStop() = runTest {
        client.playReplies.addLast { dash("s1") }
        client.playReplies.addLast { dash("s2") }
        val s = session()
        s.tune(a); runCurrent()
        s.tune(b); runCurrent()
        assertEquals(listOf("play a replaces=null", "play b replaces=s1"), plays())
        assertTrue(client.calls.none { it.startsWith("stop") })
        assertEquals(SessionState.Playing(b, dash("s2")), s.state.value)
    }

    @Test fun close_stopsCurrentSession() = runTest {
        client.playReplies.addLast { dash("s1") }
        val s = session()
        s.tune(a); runCurrent()
        s.close(); runCurrent()
        assertEquals(listOf("stop s1"), client.calls.filter { it.startsWith("stop") })
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, pings().size)
    }

    @Test fun pingFalse_retriesThenFails() = runTest {
        repeat(5) { i -> client.playReplies.addLast { dash("s$i") } }
        client.pingResult = { false }
        val s = session()
        s.tune(a); runCurrent(); s.onPlayerReady()
        advanceTimeBy(30_001); runCurrent()
        assertEquals(SessionState.Tuning(a), s.state.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals("play a replaces=s0", plays()[1])
        assertEquals(SessionState.Playing(a, dash("s1")), s.state.value)
        advanceTimeBy(30_000); runCurrent()
        assertTrue(s.state.value is SessionState.Failed)
        assertTrue(client.calls.contains("stop s1"))
    }

    @Test fun playThrows_retriesWithBackoff_thenFailed() = runTest {
        repeat(4) { client.playReplies.addLast { throw IOException("tuner busy") } }
        val s = session()
        s.tune(a); runCurrent()
        assertEquals(1, plays().size)
        assertEquals(SessionState.Tuning(a), s.state.value)
        advanceTimeBy(1_000); runCurrent(); assertEquals(2, plays().size)
        advanceTimeBy(2_999); runCurrent(); assertEquals(2, plays().size)
        advanceTimeBy(1); runCurrent(); assertEquals(3, plays().size)
        advanceTimeBy(8_000); runCurrent(); assertEquals(4, plays().size)
        assertEquals(SessionState.Failed(a, PlayerSession.UNAVAILABLE), s.state.value)
    }

    @Test fun playerError_usesSamePolicy() = runTest {
        repeat(2) { i -> client.playReplies.addLast { dash("s$i") } }
        val s = session()
        s.tune(a); runCurrent()
        s.onPlayerError(); runCurrent()
        assertEquals(SessionState.Tuning(a), s.state.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(SessionState.Playing(a, dash("s1")), s.state.value)
    }

    @Test fun playerKeepsFailing_whileServerPingsSucceed_endsInErrorCard() = runTest {
        // The server keeps the session alive but never sends video: its pings succeed, the picture never starts.
        repeat(4) { i -> client.playReplies.addLast { dash("s$i") } }
        val s = session(firstPictureMs = 600_000)
        s.tune(a); runCurrent()
        advanceTimeBy(10_000); s.onPlayerError(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(SessionState.Playing(a, dash("s1")), s.state.value)
        advanceTimeBy(30_001); runCurrent()
        assertEquals(listOf("ping s1"), pings())
        advanceTimeBy(9_000); s.onPlayerError(); runCurrent()
        assertEquals(SessionState.Failed(a, PlayerSession.UNAVAILABLE), s.state.value)
    }

    @Test fun pictureStarting_endsTheFailureStreak() = runTest {
        repeat(4) { i -> client.playReplies.addLast { dash("s$i") } }
        val s = session()
        s.tune(a); runCurrent()
        advanceTimeBy(10_000); s.onPlayerError(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        s.onPlayerReady()
        advanceTimeBy(40_000); s.onPlayerError(); runCurrent()
        assertEquals(SessionState.Tuning(a), s.state.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(SessionState.Playing(a, dash("s2")), s.state.value)
    }

    @Test fun noPictureWithin20s_retriesThenErrorCard() = runTest {
        // The server answered play but holds the stream back; the player retries quietly for over a minute.
        repeat(4) { i -> client.playReplies.addLast { dash("s$i") } }
        val s = session()
        s.tune(a); runCurrent()
        advanceTimeBy(19_999); runCurrent()
        assertEquals(SessionState.Playing(a, dash("s0")), s.state.value)
        advanceTimeBy(2); runCurrent()
        assertEquals(SessionState.Tuning(a), s.state.value)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(SessionState.Playing(a, dash("s1")), s.state.value)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(SessionState.Failed(a, PlayerSession.UNAVAILABLE), s.state.value)
        assertTrue(client.calls.contains("stop s1"))
    }

    @Test fun pictureInTime_cancelsTheDeadline() = runTest {
        client.playReplies.addLast { dash("s0") }
        val s = session()
        s.tune(a); runCurrent()
        advanceTimeBy(15_000); s.onPlayerReady()
        advanceTimeBy(45_000); runCurrent()
        assertEquals(SessionState.Playing(a, dash("s0")), s.state.value)
        assertEquals(1, plays().size)
    }

    @Test fun drmUnsupported_isFailedWithMessage() = runTest {
        client.playReplies.addLast { PlayReply.Drm("d1", null, null, null, "/m.mpd", "/lic", "com.widevine.alpha") }
        val s = session(widevine = false)
        s.tune(a); runCurrent()
        assertEquals(SessionState.Failed(a, PlayerSession.CANT_PLAY_HERE), s.state.value)
        assertTrue(client.calls.contains("stop d1"))
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, plays().size)
    }
}
