package dev.atomix.launcher.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.data.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DockStoreTest {
    private lateinit var store: SessionStore
    private fun app(pkg: String) = AppEntry(pkg, pkg, null, ColorDrawable(), Intent())

    @Before fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        store = SessionStore(ctx, encrypted = false).also { it.dock = null }
    }

    @Test fun defaults_whenNothingSaved() {
        val dock = DockStore(store) { DEFAULT_DOCK.map(::app) + app("com.other") }
        assertEquals(DEFAULT_DOCK, dock.dock().map { it.packageName })
    }

    @Test fun missingPackage_isFiltered() {
        val installed = DEFAULT_DOCK.filter { it != "tv.twitch.android.app" }.map(::app)
        val dock = DockStore(store) { installed }
        assertEquals(DEFAULT_DOCK - "tv.twitch.android.app", dock.dock().map { it.packageName })
    }

    @Test fun move_reordersAndPersists() {
        val apps = DEFAULT_DOCK.map(::app) + app("com.other")
        val dock = DockStore(store) { apps }
        dock.add("com.other")
        dock.move("com.other", 0)
        dock.remove("ar.tvplayer.tv")
        val expected = listOf("com.other") + DEFAULT_DOCK.filter { it != "ar.tvplayer.tv" }
        assertEquals(expected, dock.dock().map { it.packageName })
        assertEquals(expected, DockStore(store) { apps }.dock().map { it.packageName })
    }
}
