package dev.atomix.launcher.livetv.source

import dev.atomix.launcher.data.AtomixHttp
import dev.atomix.launcher.data.AuthExpiredException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LiveApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: LiveApi
    private fun fixture(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val caps = PlayCaps(listOf("h264"), listOf("aac"), listOf("mp4"), hls = true)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = LiveApi(AtomixHttp(server.url("/"), { "abc" }, OkHttpClient()))
    }

    @After fun tearDown() = server.shutdown()

    @Test fun channels_parsesNumericIdsDrmListAndMissingFields() = runTest {
        server.enqueue(MockResponse().setBody(fixture("live-channels.json")))
        val (tvnz, bare, radio) = api.channels()
        assertEquals("477", tvnz.id)
        assertEquals(1, tvnz.number)
        assertTrue(tvnz.drm)
        assertTrue(tvnz.favourite)
        assertEquals("The Repair Shop", tvnz.now!!.title)
        assertEquals(1794551400000, tvnz.now!!.start)
        assertEquals(listOf("Science"), tvnz.now!!.categories)
        assertEquals("/api/live/programmes/270143/image", tvnz.now!!.image)
        assertEquals("George Clarkes Building Home", tvnz.next!!.title)
        assertEquals("x9", bare.id)
        assertNull(bare.number); assertNull(bare.logo); assertNull(bare.now); assertFalse(bare.drm)
        assertTrue(radio.radio)
        assertEquals("Bearer abc", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun status_detectsAdminFromFields() = runTest {
        server.enqueue(MockResponse().setBody(fixture("live-status.json")))
        val admin = api.status()
        assertTrue(admin.enabled); assertTrue(admin.admin)
        assertEquals("iptv-simple/playlist", admin.sources.single().id)
        assertEquals(460, admin.sources.single().channels)
        assertEquals("8 unmatched", admin.sources.single().warning)
        assertEquals(1794544000000, admin.sources.single().lastRefresh)
        server.enqueue(MockResponse().setBody("""{"enabled":true,"sources":[{"id":"s","name":"S","channels":3,"lastOk":1}]}"""))
        assertFalse(api.status().admin)
    }

    @Test fun play_dashReply_hasTunerPaths() = runTest {
        server.enqueue(MockResponse().setBody("""{"sessionId":"s1","delivery":"dash","mode":"remux","manifest":"/api/live/tuner/s1/manifest.mpd","rewind":true,"channel":{"id":477,"name":"TVNZ 1"},"programme":null,"next":null}"""))
        val reply = api.play("477", caps, null) as PlayReply.Dash
        assertEquals("/api/live/tuner/s1/manifest.mpd", reply.manifest)
        assertTrue(reply.rewind)
        assertEquals("/api/live/tuner/s1/ping", reply.pingPath)
        assertEquals("/api/live/tuner/s1/stop", reply.stopPath)
        assertEquals("TVNZ 1", reply.channel!!.name)
    }

    @Test fun play_streamReply_hasPlaybackPaths() = runTest {
        server.enqueue(MockResponse().setBody("""{"sessionId":"s2","mode":"remux","delivery":"hls","url":"/api/hls/s2/index.m3u8","rewind":false}"""))
        val reply = api.play("1", caps, null) as PlayReply.Stream
        assertEquals("hls", reply.delivery)
        assertEquals("/api/hls/s2/index.m3u8", reply.url)
        assertEquals("/api/playback/s2/ping", reply.pingPath)
    }

    @Test fun play_drmReply_hasDrmPaths() = runTest {
        server.enqueue(MockResponse().setBody("""{"sessionId":"s3","delivery":"drm","keySystem":"com.widevine.alpha","manifest":"/api/live/drm/s3/manifest.mpd","licence":"/api/live/drm/s3/licence"}"""))
        val reply = api.play("1", caps, null) as PlayReply.Drm
        assertEquals("/api/live/drm/s3/licence", reply.licence)
        assertEquals("com.widevine.alpha", reply.keySystem)
        assertEquals("/api/live/drm/s3/stop", reply.stopPath)
    }

    @Test fun play_sendsCapsAndReplaces() = runTest {
        server.enqueue(MockResponse().setBody("""{"sessionId":"s1","delivery":"dash","manifest":"/m","rewind":true}"""))
        api.play("477", caps, replaces = "old")
        val req = server.takeRequest()
        assertEquals("/api/live/channels/477/play", req.path)
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("h264", body["caps"]!!.jsonObject["video"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("old", body["replaces"]!!.jsonPrimitive.content)
    }

    @Test fun setFavouriteOrder_putsChannelIds() = runTest {
        server.enqueue(MockResponse().setBody("""{"channelIds":["a","b"]}"""))
        assertEquals(listOf("a", "b"), api.setFavouriteOrder(listOf("a", "b")))
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("""{"channelIds":["a","b"]}""", req.body.readUtf8())
    }

    @Test fun favourites_addAndRemove() = runTest {
        server.enqueue(MockResponse().setBody("{}")); server.enqueue(MockResponse().setBody("{}"))
        api.addFavourite("5"); api.removeFavourite("5")
        assertEquals("POST /api/live/favourites/5", server.takeRequest().let { "${it.method} ${it.path}" })
        assertEquals("DELETE /api/live/favourites/5", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test fun ping_okFalse_returnsFalse() = runTest {
        server.enqueue(MockResponse().setBody("""{"ok":false,"error":"gone"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        val reply = PlayReply.Dash("s1", null, null, null, "/m", true)
        assertFalse(api.ping(reply))
        assertTrue(api.ping(reply))
        assertEquals("/api/live/tuner/s1/ping", server.takeRequest().path)
    }

    @Test fun unauthorised_throwsAuthExpired() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        try { api.channels(); throw AssertionError("expected AuthExpiredException") } catch (_: AuthExpiredException) {}
    }

    @Test fun idsAreEncodedInPaths() = runTest {
        server.enqueue(MockResponse().setBody("{}")); server.enqueue(MockResponse().setBody("{}"))
        api.addFavourite("a/b")
        api.refreshSource("iptv-simple/playlist")
        assertEquals("/api/live/favourites/a%2Fb", server.takeRequest().path)
        assertEquals("/api/live/sources/iptv-simple%2Fplaylist/refresh", server.takeRequest().path)
    }

    @Test fun url_resolvesRelativePaths() {
        assertEquals(server.url("/api/live/channels/1/logo"), api.url("/api/live/channels/1/logo"))
        assertNull(api.url(null))
    }
}
