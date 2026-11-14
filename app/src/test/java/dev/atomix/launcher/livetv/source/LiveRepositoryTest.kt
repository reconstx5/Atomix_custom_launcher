package dev.atomix.launcher.livetv.source

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.data.SessionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LiveRepositoryTest {
    private lateinit var store: SessionStore
    private lateinit var fake: FakeLiveClient
    private var relocations = 0
    private lateinit var repo: LiveRepository

    @Before fun setUp() {
        store = SessionStore(ApplicationProvider.getApplicationContext<Context>(), encrypted = false)
        store.cachedChannelsJson = null; store.cachedFavouriteIds = null
        fake = FakeLiveClient(
            channels = listOf(tv("tv1", 1), tv("tv2", 2), tv("tv3", 3), radio("r1"), radio("r2")),
            favourites = mutableListOf("r1", "tv3", "tv1", "r2"),
        )
        relocations = 0
        repo = LiveRepository({ fake }, store, relocate = { relocations++ })
    }

    private fun ready() = repo.state.value as LiveState.Ready

    @Test fun ready_filtersRadio_keepsFavouriteOrder() = runTest {
        repo.refresh()
        assertEquals(listOf("tv3", "tv1"), ready().pinned.map { it.id })
        assertEquals(listOf("tv1", "tv2", "tv3"), ready().all.map { it.id })
    }

    @Test fun all_sortsByNumberThenName_unnumberedLast() = runTest {
        fake.channels = listOf(Channel("z", null, "Zed"), Channel("b", 5, "B"), Channel("a", null, "Alpha"), Channel("c", 2, "C"))
        repo.refresh()
        assertEquals(listOf("c", "b", "a", "z"), ready().all.map { it.id })
    }

    @Test fun offlineStaysOffline_whenAPinIsChanged() = runTest {
        repo.refresh()
        fake.down = true
        repo.refresh()
        assertTrue(repo.state.value is LiveState.Offline)
        runCatching { repo.pin("tv2") }
        assertTrue("a refused change must not promote a dead server back to Ready", repo.state.value is LiveState.Offline)
    }

    @Test fun saveOrder_neverSendsTheSameChannelTwice() = runTest {
        // No refresh: with no channel list nothing counts as TV, so every favourite lands in the "keep" half too.
        fake.favourites = mutableListOf("tv1")
        repo.saveOrder(listOf("tv1"))
        assertEquals(listOf("tv1"), fake.lastOrder)
    }

    @Test fun disabled_isNotConfigured() = runTest {
        fake.enabled = false
        repo.refresh()
        assertEquals(LiveState.NotConfigured, repo.state.value)
    }

    @Test fun networkFailure_showsCachedPinsOffline_andRelocatesOnce() = runTest {
        repo.refresh()
        fake.down = true
        repo.refresh(); repo.refresh()
        assertEquals(listOf("tv3", "tv1"), (repo.state.value as LiveState.Offline).pinned.map { it.id })
        assertEquals(1, relocations)
        fake.down = false
        repo.refresh()
        fake.down = true
        repo.refresh()
        assertEquals(2, relocations)
    }

    @Test fun cacheSurvivesNewRepository() = runTest {
        repo.refresh()
        fake.down = true
        val fresh = LiveRepository({ fake }, store, relocate = {})
        fresh.refresh()
        assertEquals(listOf("tv3", "tv1"), (fresh.state.value as LiveState.Offline).pinned.map { it.id })
    }

    @Test fun noCache_networkFailure_isOfflineWithEmptyList() = runTest {
        fake.down = true
        repo.refresh()
        assertEquals(LiveState.Offline(emptyList()), repo.state.value)
    }

    @Test fun noServer_isOffline() = runTest {
        val none = LiveRepository({ null }, store, relocate = { relocations++ })
        none.refresh()
        assertTrue(none.state.value is LiveState.Offline)
    }

    @Test fun saveOrder_keepsRadioFavouritesAfterTv() = runTest {
        repo.refresh()
        repo.saveOrder(listOf("tv1", "tv3"))
        assertEquals(listOf("tv1", "tv3", "r1", "r2"), fake.lastOrder)
        assertEquals(listOf("tv1", "tv3"), ready().pinned.map { it.id })
    }

    // Edit on Home only rearranges, so a save never takes a channel off Home: one it does not name stays, after those it does.
    @Test fun saveOrder_withATvFavouriteLeftOut_keepsItAfterTheOnesNamed() = runTest {
        repo.refresh()
        repo.saveOrder(listOf("tv1"))
        assertEquals(listOf("tv1", "tv3", "r1", "r2"), fake.lastOrder)
    }

    @Test fun saveOrder_keepsATvFavouriteAddedSinceTheEditBegan() = runTest {
        repo.refresh()
        fake.favourites.add("tv2")            // put on Home from another device while the row was being rearranged
        repo.refresh()
        repo.saveOrder(listOf("tv3", "tv1"))  // the order as it was when the edit began
        assertEquals(listOf("tv3", "tv1", "tv2", "r1", "r2"), fake.lastOrder)
    }

    @Test fun saveOrder_doesNotPutBackAChannelTakenOffHomeSinceTheEditBegan() = runTest {
        repo.refresh()
        fake.favourites.remove("tv3")
        repo.refresh()
        repo.saveOrder(listOf("tv3", "tv1"))
        assertEquals(listOf("tv1", "r1", "r2"), fake.lastOrder)
    }

    @Test fun pinnedChannelMissingFromServer_isHidden() = runTest {
        fake.channels = fake.channels.filter { it.id != "tv3" }
        repo.refresh()
        assertEquals(listOf("tv1"), ready().pinned.map { it.id })
    }

    @Test fun pin_appendsAndCallsServer() = runTest {
        repo.refresh()
        repo.pin("tv2")
        assertEquals(listOf("tv3", "tv1", "tv2"), ready().pinned.map { it.id })
        assertTrue("add tv2" in fake.calls)
    }

    @Test fun pinFailure_revertsState() = runTest {
        repo.refresh()
        fake.failWrites = true
        runCatching { repo.pin("tv2") }
        assertEquals(listOf("tv3", "tv1"), ready().pinned.map { it.id })
        runCatching { repo.unpin("tv1") }
        assertEquals(listOf("tv3", "tv1"), ready().pinned.map { it.id })
    }

    @Test fun unpin_removes() = runTest {
        repo.refresh()
        repo.unpin("tv3")
        assertEquals(listOf("tv1"), ready().pinned.map { it.id })
    }

    @Test fun channel_findsAnyById() = runTest {
        repo.refresh()
        assertEquals("Ch tv2", repo.channel("tv2")?.name)
    }
}
