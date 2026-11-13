package dev.atomix.launcher.ui.livetv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.data.SessionStore
import dev.atomix.launcher.livetv.source.FakeLiveClient
import dev.atomix.launcher.livetv.source.LiveRepository
import dev.atomix.launcher.livetv.source.radio
import dev.atomix.launcher.livetv.source.tv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LiveTvTabStateTest {
    private lateinit var fake: FakeLiveClient
    private lateinit var repo: LiveRepository

    @Before fun setUp() {
        val store = SessionStore(ApplicationProvider.getApplicationContext<Context>(), encrypted = false)
        store.cachedChannelsJson = null; store.cachedFavouriteIds = null
        fake = FakeLiveClient(channels = listOf(tv("tv1", 1), tv("tv2", 2), radio("r1")), favourites = mutableListOf("tv1"))
        repo = LiveRepository({ fake }, store, relocate = {})
    }

    @Test fun togglePin_unpinned_addsFavourite() = runTest {
        repo.refresh()
        val state = LiveTvTabState(repo, this)
        assertFalse(state.isPinned("tv2"))
        state.togglePin("tv2"); runCurrent()
        assertTrue("add tv2" in fake.calls)
        assertTrue(state.isPinned("tv2"))
    }

    @Test fun togglePin_pinned_removesFavourite() = runTest {
        repo.refresh()
        val state = LiveTvTabState(repo, this)
        state.togglePin("tv1"); runCurrent()
        assertTrue("remove tv1" in fake.calls)
        assertFalse(state.isPinned("tv1"))
    }

    @Test fun rows_listsTvChannelsOnly() = runTest {
        repo.refresh()
        assertEquals(listOf("tv1", "tv2"), LiveTvTabState(repo, this).rows().map { it.id })
    }

    @Test fun togglePin_refused_staysAsItWas_andReports() = runTest {
        repo.refresh()
        fake.failWrites = true
        var errors = 0
        val state = LiveTvTabState(repo, this) { errors++ }
        state.togglePin("tv2"); runCurrent()
        assertFalse(state.isPinned("tv2"))
        assertEquals(1, errors)
    }

    @Test fun showOnHome_thenSavingTheOrder_stillSendsRadioFavourites() = runTest {
        fake.favourites = mutableListOf("r1", "tv1")
        repo.refresh()
        val state = LiveTvTabState(repo, this)
        state.togglePin("tv2"); runCurrent()          // Show on Home: on
        repo.saveOrder(listOf("tv2", "tv1"))          // what Home's Edit saves
        assertEquals(listOf("tv2", "tv1", "r1"), fake.lastOrder)
        state.togglePin("tv1"); runCurrent()          // Show on Home: off
        repo.saveOrder(listOf("tv2"))
        assertEquals(listOf("tv2", "r1"), fake.lastOrder)
    }

    @Test fun switchTarget_isTheFocusedChannel_orNothingOnceItHasGone() = runTest {
        repo.refresh()
        val state = LiveTvTabState(repo, this)
        assertEquals("tv2", state.switchTarget("tv2")?.id)
        assertNull(state.switchTarget("gone"))
        assertNull(state.switchTarget(null))
    }
}
