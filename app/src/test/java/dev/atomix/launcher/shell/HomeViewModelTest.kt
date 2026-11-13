package dev.atomix.launcher.shell

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.apps.AppCatalog
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.data.SessionStore
import dev.atomix.launcher.livetv.source.FakeLiveClient
import dev.atomix.launcher.livetv.source.LiveRepository
import dev.atomix.launcher.livetv.source.LiveState
import dev.atomix.launcher.livetv.source.tv
import dev.atomix.launcher.prefs.FavouriteApps
import dev.atomix.launcher.prefs.LauncherPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private lateinit var fake: FakeLiveClient
    private lateinit var prefs: LauncherPrefs
    private lateinit var vm: HomeViewModel
    private var clock = 1_000_000L
    private fun app(pkg: String) = AppEntry(pkg, pkg, null, ColorDrawable(), Intent())

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = SessionStore(ctx, encrypted = false).also { it.cachedChannelsJson = null; it.cachedFavouriteIds = null }
        val sp = ctx.getSharedPreferences("home_vm_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        prefs = LauncherPrefs(sp) { null }
        prefs.favouriteApps = listOf("b", "gone", "a")
        fake = FakeLiveClient(channels = listOf(tv("1", 1), tv("2", 2)), favourites = mutableListOf("2"))
        val repo = LiveRepository({ fake }, store, relocate = {})
        val catalog = AppCatalog({ listOf(app("a"), app("b"), app("c")) }, scope, dispatcher)
        vm = HomeViewModel(repo, FavouriteApps(prefs) { catalog.apps.value }, catalog, scope, now = { clock })
        scope.runCurrent()
    }

    @Test fun refreshesEveryMinuteWhileVisible_stopsWhenHidden() {
        vm.onVisible()
        scope.runCurrent()
        assertEquals(1, fake.refreshes)
        scope.advanceTimeBy(60_001)
        assertEquals(2, fake.refreshes)
        vm.onHidden()
        scope.advanceTimeBy(180_000)
        assertEquals(2, fake.refreshes)
    }

    @Test fun visibleAgain_refreshesAtOnce() {
        vm.onVisible(); scope.runCurrent()
        vm.onHidden()
        vm.onVisible(); scope.runCurrent()
        assertEquals(2, fake.refreshes)
    }

    @Test fun liveState_flowsIntoUi() {
        vm.onVisible(); scope.runCurrent()
        val live = vm.ui.value.live as LiveState.Ready
        assertEquals(listOf("2"), live.pinned.map { it.id })
    }

    @Test fun appsRow_isVisibleFavourites_inOrder() {
        scope.runCurrent()
        assertEquals(listOf("b", "a"), vm.ui.value.apps.map { it.packageName })
        prefs.favouriteApps = listOf("c")
        vm.refreshApps()
        assertEquals(listOf("c"), vm.ui.value.apps.map { it.packageName })
    }

    @Test fun saveChannelOrder_writesTheServer_droppingRemoved() {
        fake.favourites = mutableListOf("1", "2")
        vm.onVisible(); scope.runCurrent()
        vm.saveChannelOrder(listOf("2"))
        scope.runCurrent()
        assertEquals(listOf("2"), fake.lastOrder)
        assertEquals(listOf("2"), (vm.ui.value.live as LiveState.Ready).pinned.map { it.id })
    }

    @Test fun saveChannelOrder_refused_saysSo_insteadOfSilentlyReverting() {
        fake.favourites = mutableListOf("1", "2")
        vm.onVisible(); scope.runCurrent()
        fake.failWrites = true
        var told = false
        vm.saveChannelOrder(listOf("2")) { told = true }
        scope.runCurrent()
        assertTrue("the user is told the save was refused", told)
        assertEquals(listOf("1", "2"), (vm.ui.value.live as LiveState.Ready).pinned.map { it.id })
    }

    @Test fun saveAppOrder_appliesAtOnce_keepingHiddenFavourites() {
        scope.runCurrent()
        vm.saveAppOrder(listOf("a"))
        assertEquals(listOf("a"), vm.ui.value.apps.map { it.packageName })
        assertEquals(listOf("a", "gone"), prefs.favouriteApps)
    }

    @Test fun clockTicks() {
        vm.onVisible(); scope.runCurrent()
        val first = vm.ui.value.nowMs
        clock += 30_000
        scope.advanceTimeBy(15_001)
        assertTrue(vm.ui.value.nowMs > first)
    }
}
