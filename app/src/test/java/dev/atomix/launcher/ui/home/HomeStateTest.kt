package dev.atomix.launcher.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.data.AuthExpiredException
import dev.atomix.launcher.data.HomeResponse
import dev.atomix.launcher.data.Item
import dev.atomix.launcher.data.Row
import dev.atomix.launcher.data.SessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class HomeStateTest {
    private fun item(id: Long) = Item(id = id, kind = "movie", title = "T$id")
    private val cached = HomeResponse(hero = item(1))
    private val fresh = HomeResponse(hero = item(2))

    @Test fun networkError_withNoCache_isOffline() {
        assertEquals(HomeState.Status.Offline to null, reduce(null, Result.failure(IOException("down"))))
    }

    @Test fun networkError_withCache_staysOnlineWithCache() {
        assertEquals(HomeState.Status.Online to cached, reduce(cached, Result.failure(IOException("down"))))
        assertEquals(HomeState.Status.Online to fresh, reduce(cached, Result.success(fresh)))
        assertEquals(HomeState.Status.Loading to cached, reduce(cached, null))
    }

    @Test fun authExpired_routesToSignIn() {
        assertEquals(HomeState.Status.SignedOut, reduce(cached, Result.failure(AuthExpiredException())).first)
    }

    @Test fun noHero_usesFirstRowItem() {
        val resp = HomeResponse(rows = listOf(
            Row("live-now", title = "Live", style = "landscape", items = listOf(item(9))),
            Row("recent-1", title = "Recent", style = "poster", items = listOf(item(5), item(6))),
        ))
        assertEquals(5L, pickHero(resp)?.id)
    }

    @Test fun emptyEverything_showsAppsOnly() {
        val resp = HomeResponse(rows = listOf(Row("recent-1", title = "Recent", style = "poster")))
        assertNull(pickHero(resp))
        assertNull(pickRow(resp.rows))
        assertNull(pickHero(null))
    }

    @Test fun continuePreferred_overOtherRows() {
        val rows = listOf(
            Row("recent-1", title = "Recent", style = "poster", items = listOf(item(5))),
            Row("continue", title = "Continue watching", style = "landscape", items = listOf(item(7))),
        )
        assertEquals("continue", pickRow(rows)?.key)
        assertEquals("recent-1", pickRow(rows.map { if (it.key == "continue") it.copy(items = emptyList()) else it })?.key)
    }

    @Test fun focusDebounce_emitsLastAfter300ms() = runTest {
        val store = SessionStore(ApplicationProvider.getApplicationContext<Context>(), encrypted = false)
        val vm = HomeViewModel(api = { null }, store = store, dock = { emptyList() }, scope = backgroundScope)
        vm.onCardFocused(item(1)); advanceTimeBy(100)
        vm.onCardFocused(item(2)); advanceTimeBy(100)
        vm.onCardFocused(item(3)); advanceTimeBy(299); runCurrent()
        assertNull(vm.focusedItem.value)
        advanceTimeBy(2); runCurrent()
        assertEquals(3L, vm.focusedItem.value?.id)
    }

}
