package dev.atomix.launcher.livetv.player

import androidx.media3.common.C
import dev.atomix.launcher.livetv.source.PlayReply
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaFactoryTest {
    private val factory = MediaFactory(OkHttpClient()) { "http://server$it" }

    @Test fun hlsLive_startsFourSecondsFromTheEdge() {
        // The server's live remux keeps only ~12 s (6 × 2 s); the default start (3 × 4 s target) is its oldest piece.
        val item = factory.build(PlayReply.Stream("s", null, null, null, "/api/hls/s/index.m3u8", "hls"))!!.mediaItem
        assertEquals("http://server/api/hls/s/index.m3u8", item.localConfiguration!!.uri.toString())
        assertEquals(4_000L, item.liveConfiguration.targetOffsetMs)
    }

    @Test fun dash_keepsTheManifestsOwnOffset() {
        val item = factory.build(PlayReply.Dash("s", null, null, null, "/m.mpd", rewind = true))!!.mediaItem
        assertEquals(C.TIME_UNSET, item.liveConfiguration.targetOffsetMs)
    }

    @Test fun unresolvablePath_isNull() {
        val none = MediaFactory(OkHttpClient()) { null }
        assertNull(none.build(PlayReply.Stream("s", null, null, null, "/x", "hls")))
    }
}
