package dev.atomix.launcher.data

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AtomixApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AtomixApi

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = AtomixApi(server.url("/"), { "abc" }, OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun home_sendsBearer() = runTest {
        server.enqueue(MockResponse().setBody("""{"hero":null,"rows":[]}"""))
        api.home()
        assertEquals("Bearer abc", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun unauthorized_throwsAuthExpired() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Sign in"}"""))
        try { api.home(); throw AssertionError("expected AuthExpiredException") } catch (_: AuthExpiredException) {}
    }

    @Test fun poll_approved_readsCookieToken() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"approved"}""").addHeader("Set-Cookie", "atomix_session=tok123; Path=/; HttpOnly"))
        assertEquals(PollResult.Approved("tok123"), api.quickConnectPoll("p"))
    }

    @Test fun poll_waiting_isPending() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"waiting"}"""))
        assertEquals(PollResult.Pending, api.quickConnectPoll("p"))
    }

    @Test fun poll_pending_isPending() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"pending"}"""))
        assertEquals(PollResult.Pending, api.quickConnectPoll("p"))
    }

    @Test fun poll_expired() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"expired"}"""))
        assertEquals(PollResult.Expired, api.quickConnectPoll("p"))
    }

    @Test fun selectProfile_sendsPin() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":2,"name":"K"}"""))
        api.selectProfile(2, "1234")
        val req = server.takeRequest()
        assertEquals("/api/profiles/2/select", req.path)
        assertTrue(req.body.readUtf8().contains("\"pin\":\"1234\""))
    }

    @Test fun imageUrl_resolvesRelative() {
        val url = api.imageUrl("/api/items/5/image/backdrop?v=1")!!
        assertEquals(server.url("/api/items/5/image/backdrop?v=1"), url)
    }
}
