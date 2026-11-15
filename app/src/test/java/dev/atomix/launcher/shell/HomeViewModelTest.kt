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
import dev.atomix.launcher.prefs.HiddenApps
import dev.atomix.launcher.prefs.LauncherPrefs
import dev.atomix.launcher.prefs.ROW_APPS
import dev.atomix.launcher.prefs.ROW_ON_NOW
import dev.atomix.launcher.prefs.withApp
import dev.atomix.launcher.prefs.withRowAdded
import dev.atomix.launcher.prefs.RowStore
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
    private lateinit var rows: RowStore
    private lateinit var vm: HomeViewModel
    private var clock = 1_000_000L
    private fun app(pkg: String) = AppEntry(pkg, pkg, null, ColorDrawable(), Intent())

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = SessionStore(ctx, encrypted = false).also { it.cachedChannelsJson = null; it.cachedFavouriteIds = null }
        val sp = ctx.getSharedPreferences("home_vm_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        sp.edit().putString("favourite_apps", listOf("b", "gone", "a").joinToString("\n")).commit()
        val prefs = LauncherPrefs(sp) { null }
        rows = RowStore(prefs)
        fake = FakeLiveClient(channels = listOf(tv("1", 1), tv("2", 2)), favourites = mutableListOf("2"))
        val repo = LiveRepository({ fake }, store, relocate = {})
        val catalog = AppCatalog({ listOf(app("a"), app("b"), app("c")) }, scope, dispatcher)
        vm = HomeViewModel(repo, rows, HiddenApps(prefs), catalog, scope, now = { clock })
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
        assertEquals(listOf("b", "a"), yourApps())
        rows.update { r -> r.map { if (it.id == ROW_APPS) it.copy(apps = listOf("c")) else it } }
        scope.runCurrent()
        assertEquals(listOf("c"), yourApps())
    }

    // Edit on Home only rearranges: the save writes the new order and takes nothing off Home.
    @Test fun saveChannelOrder_writesTheNewOrderToTheServer() {
        fake.favourites = mutableListOf("1", "2")
        vm.onVisible(); scope.runCurrent()
        vm.saveChannelOrder(listOf("2", "1"))
        scope.runCurrent()
        assertEquals(listOf("2", "1"), fake.lastOrder)
        assertEquals(listOf("2", "1"), (vm.ui.value.live as LiveState.Ready).pinned.map { it.id })
    }

    @Test fun saveChannelOrder_leavingAChannelOut_doesNotTakeItOffHome() {
        fake.favourites = mutableListOf("1", "2")
        vm.onVisible(); scope.runCurrent()
        vm.saveChannelOrder(listOf("2"))
        scope.runCurrent()
        assertEquals(listOf("2", "1"), fake.lastOrder)
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
        vm.saveAppOrder(ROW_APPS, listOf("a"))
        assertEquals(listOf("a"), yourApps())
        assertEquals(listOf("a", "gone"), rows.rows.value.first { it.id == ROW_APPS }.apps)
    }

    private fun yourApps() = vm.ui.value.rows.first { it.spec.id == ROW_APPS }.apps.map { it.packageName }

    @Test fun rows_areTheDrawnRows_inOrder() {
        scope.runCurrent()
        assertEquals(listOf(ROW_APPS, ROW_ON_NOW), vm.ui.value.rows.map { it.spec.id })
    }

    @Test fun saveAppOrder_touchesOnlyThatRow() {
        rows.update { it.withRowAdded("Tools").withApp("row-1", "a", true).withApp("row-1", "b", true) }
        scope.runCurrent()
        vm.saveAppOrder("row-1", listOf("b", "a"))
        assertEquals(listOf("b", "a"), vm.ui.value.rows.first { it.spec.id == "row-1" }.apps.map { it.packageName })
        assertEquals(listOf("b", "a"), yourApps())
        vm.saveAppOrder(ROW_APPS, listOf("a", "b"))
        assertEquals(listOf("b", "a"), vm.ui.value.rows.first { it.spec.id == "row-1" }.apps.map { it.packageName })
    }

    @Test fun clockTicks() {
        vm.onVisible(); scope.runCurrent()
        val first = vm.ui.value.nowMs
        clock += 30_000
        scope.advanceTimeBy(15_001)
        assertTrue(vm.ui.value.nowMs > first)
    }
}
