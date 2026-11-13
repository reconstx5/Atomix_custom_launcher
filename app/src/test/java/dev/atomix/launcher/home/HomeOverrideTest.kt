package dev.atomix.launcher.home

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeOverrideTest {
    private val stateChanged = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

    @Test
    fun redirects_whenStockHomeComesToFront() {
        assertTrue(HomeOverride.shouldRedirect(stateChanged, "com.google.android.tvlauncher", "com.google.android.tvlauncher.MainActivity"))
    }

    @Test
    fun ignores_otherStockLauncherWindows() {
        assertFalse(HomeOverride.shouldRedirect(stateChanged, "com.google.android.tvlauncher", "android.widget.FrameLayout"))
        assertFalse(HomeOverride.shouldRedirect(stateChanged, "com.google.android.tvlauncher", null))
    }

    @Test
    fun ignores_otherAppsAndEventTypes() {
        assertFalse(HomeOverride.shouldRedirect(stateChanged, "dev.atomix.launcher", "dev.atomix.launcher.HomeActivity"))
        assertFalse(HomeOverride.shouldRedirect(stateChanged, null, null))
        assertFalse(HomeOverride.shouldRedirect(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, "com.google.android.tvlauncher", "com.google.android.tvlauncher.MainActivity"))
    }
}
