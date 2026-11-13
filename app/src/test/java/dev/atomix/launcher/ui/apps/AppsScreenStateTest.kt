package dev.atomix.launcher.ui.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.apps.DEFAULT_DOCK
import dev.atomix.launcher.apps.DockStore
import dev.atomix.launcher.data.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppsScreenStateTest {
    private lateinit var dock: DockStore
    private fun app(pkg: String) = AppEntry(pkg, pkg, null, ColorDrawable(), Intent())
    private val apps = (DEFAULT_DOCK + "com.other").map(::app)

    @Before fun setUp() {
        val store = SessionStore(ApplicationProvider.getApplicationContext<Context>(), encrypted = false).also { it.dock = null }
        dock = DockStore(store) { apps }
    }

    @Test fun addToDock_appendsWhenAbsent() {
        val state = AppsScreenState(dock) { apps }
        assertFalse(state.inDock("com.other"))
        state.toggleDock("com.other")
        assertTrue(state.inDock("com.other"))
        assertEquals("com.other", dock.dock().last().packageName)
    }

    @Test fun addToDock_noDuplicate() {
        val state = AppsScreenState(dock) { apps }
        state.addToDock("org.smarttube.stable")
        assertEquals(DEFAULT_DOCK, dock.dock().map { it.packageName })
        state.toggleDock("org.smarttube.stable")
        assertFalse(state.inDock("org.smarttube.stable"))
    }
}
