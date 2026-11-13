package dev.atomix.launcher.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SessionStoreTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun clearAuth_keepsServerAndDock() {
        val s = SessionStore(ctx, encrypted = false)
        s.serverUrl = "http://192.168.8.65:8787/".toHttpUrl()
        s.token = "t"
        s.profileId = 1
        s.profileName = "Dallas"
        s.cachedChannelsJson = "[]"
        s.dock = listOf("a", "b")
        s.clearAuth()
        assertEquals("http://192.168.8.65:8787/".toHttpUrl(), s.serverUrl)
        assertEquals(listOf("a", "b"), s.dock)
        assertNull(s.token)
        assertNull(s.profileId)
        assertNull(s.profileName)
        assertNull(s.cachedChannelsJson)
    }

    @Test fun values_roundTrip() {
        val s = SessionStore(ctx, encrypted = false)
        s.lastKnownUrl = "http://10.0.0.6:8787/".toHttpUrl()
        s.profileId = 42
        val again = SessionStore(ctx, encrypted = false)
        assertEquals("http://10.0.0.6:8787/".toHttpUrl(), again.lastKnownUrl)
        assertEquals(42L, again.profileId)
    }
}
