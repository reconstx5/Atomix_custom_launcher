package dev.atomix.launcher.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.data.HttpException
import dev.atomix.launcher.data.SessionStore
import dev.atomix.launcher.livetv.source.FakeLiveClient
import dev.atomix.launcher.livetv.source.LiveRepository
import dev.atomix.launcher.livetv.source.LiveSource
import dev.atomix.launcher.livetv.source.LiveStatus
import dev.atomix.launcher.livetv.source.radio
import dev.atomix.launcher.livetv.source.tv
import dev.atomix.launcher.prefs.LauncherPrefs
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SettingsStateTest {
    private lateinit var fake: FakeLiveClient
    private lateinit var store: SessionStore
    private lateinit var prefs: LauncherPrefs
    private lateinit var repo: LiveRepository
    private var located: HttpUrl? = null
    private lateinit var state: SettingsState

    private val iptv = LiveSource(id = "s1", name = "IPTV", channels = 120, lastRefresh = 1_000L)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        store = SessionStore(ctx, encrypted = false).also {
            it.cachedChannelsJson = null; it.cachedFavouriteIds = null
            it.serverUrl = "http://192.168.8.65:8787/".toHttpUrl(); it.lastKnownUrl = null
        }
        val sp = ctx.getSharedPreferences("settings_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        prefs = LauncherPrefs(sp) { null }
        fake = FakeLiveClient(channels = listOf(radio("r1"), tv("1", 1), tv("2", 2)))
        repo = LiveRepository({ fake }, store, relocate = {})
        state = SettingsState({ fake }, repo, store, { located }, prefs)
    }

    @Test fun adminStatus_allowsRefresh() = runTest {
        fake.statusReply = LiveStatus(enabled = true, sources = listOf(iptv), admin = true)
        state.load()
        assertTrue(state.live.value.admin)
        assertEquals(listOf(iptv), state.live.value.sources)
        state.refreshSource("s1")
        assertTrue("refresh s1" in fake.calls)
        assertNull(state.live.value.error)
    }

    @Test fun nonAdminStatus_isNotAdmin() = runTest {
        fake.statusReply = LiveStatus(enabled = true, sources = listOf(iptv.copy(lastRefresh = null)), admin = false)
        state.load()
        assertFalse(state.live.value.admin)
        assertEquals(1, state.live.value.sources.size)
    }

    @Test fun refreshRefused_setsError_keepsSources() = runTest {
        fake.statusReply = LiveStatus(enabled = true, sources = listOf(iptv), admin = true)
        state.load()
        fake.refreshFailure = HttpException(403, "")
        state.refreshSource("s1")
        assertNotNull(state.live.value.error)
        assertEquals(listOf(iptv), state.live.value.sources)
    }

    @Test fun testChannel_prefersPinned_elseFirstTvChannel() = runTest {
        repo.refresh()
        assertEquals("1", state.testChannelId())
        fake.favourites = mutableListOf("2")
        repo.refresh()
        assertEquals("2", state.testChannelId())
    }

    @Test fun homeOverrideOn_readsTheEnabledServicesList() {
        assertTrue(state.homeOverrideOn("a/b:dev.atomix.launcher/dev.atomix.launcher.home.HomeOverrideService"))
        assertFalse(state.homeOverrideOn("a/b"))
        assertFalse(state.homeOverrideOn(null))
    }

    @Test fun homeOverrideOn_acceptsTheShortComponentForm() {
        assertTrue(state.homeOverrideOn("a/b:dev.atomix.launcher/.home.HomeOverrideService"))
    }

    @Test fun findServerAgain_storesTheLocatedUrl() = runTest {
        located = "http://192.168.8.70:8787/".toHttpUrl()
        assertTrue(state.findServerAgain())
        assertEquals(located, store.serverUrl)
        assertEquals("http://192.168.8.65:8787/".toHttpUrl(), store.lastKnownUrl)
    }

    @Test fun findServerAgain_nothingFound_keepsTheAddress() = runTest {
        located = null
        assertFalse(state.findServerAgain())
        assertEquals("http://192.168.8.65:8787/".toHttpUrl(), store.serverUrl)
    }

    @Test fun ago_readsInMinutesHoursAndDays() {
        val now = 10_000_000_000L
        assertEquals("just now", ago(now - 30_000, now))
        assertEquals("5 min ago", ago(now - 5 * 60_000, now))
        assertEquals("4 h ago", ago(now - 4 * 3_600_000 - 40 * 60_000, now))
        assertEquals("2 days ago", ago(now - 2 * 86_400_000L - 1, now))
        assertNull(ago(null, now))
    }

    @Test fun reducedEffects_isSaved() {
        assertFalse(state.reducedEffects)
        state.reducedEffects = true
        assertTrue(prefs.reducedEffects)
    }
}
