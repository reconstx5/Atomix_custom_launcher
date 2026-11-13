package dev.atomix.launcher.ui.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.apps.AppEntry
import dev.atomix.launcher.prefs.DEFAULT_DOCK
import dev.atomix.launcher.prefs.FavouriteApps
import dev.atomix.launcher.prefs.LauncherPrefs
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
    private lateinit var dock: FavouriteApps
    private fun app(pkg: String) = AppEntry(pkg, pkg, null, ColorDrawable(), Intent())
    private val apps = (DEFAULT_DOCK + "com.other").map(::app)

    @Before fun setUp() {
        val sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("apps_state_test", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        dock = FavouriteApps(LauncherPrefs(sp) { DEFAULT_DOCK }) { apps }
    }

    @Test fun addToDock_appendsWhenAbsent() {
        val state = AppsScreenState(dock) { apps }
        assertFalse(state.inDock("com.other"))
        state.toggleDock("com.other")
        assertTrue(state.inDock("com.other"))
        assertEquals("com.other", dock.visible().last().packageName)
    }

    @Test fun addToDock_noDuplicate() {
        val state = AppsScreenState(dock) { apps }
        state.addToDock("org.smarttube.stable")
        assertEquals(DEFAULT_DOCK, dock.visible().map { it.packageName })
        state.toggleDock("org.smarttube.stable")
        assertFalse(state.inDock("org.smarttube.stable"))
    }
}
