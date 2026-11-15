package dev.atomix.launcher.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LauncherPrefsTest {
    private fun prefs(): SharedPreferences =
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("launcher_prefs_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }

    @Test fun migratesLegacyDock_withAtomixFirst() {
        val p = LauncherPrefs(prefs(), legacyDock = { listOf("a", "b") })
        assertEquals(listOf(ATOMIX_APP_KEY, "a", "b"), p.favouriteApps)
    }

    @Test fun noLegacyDock_usesDefaultsWithAtomixFirst() {
        assertEquals(listOf(ATOMIX_APP_KEY) + DEFAULT_DOCK, LauncherPrefs(prefs(), { null }).favouriteApps)
    }

    @Test fun legacyAlreadyHasAtomix_keepsItsPosition() {
        assertEquals(listOf("a", ATOMIX_APP_KEY), LauncherPrefs(prefs(), { listOf("a", ATOMIX_APP_KEY) }).favouriteApps)
    }

    @Test fun savedFavourites_winOverTheDock() {
        val sp = prefs().also { it.edit().putString("favourite_apps", "z").commit() }
        assertEquals(listOf("z"), LauncherPrefs(sp, { listOf("a") }).favouriteApps)
    }

    @Test fun emptySavedFavourites_readAsEmpty() {
        val sp = prefs().also { it.edit().putString("favourite_apps", "").commit() }
        assertEquals(emptyList<String>(), LauncherPrefs(sp, { listOf("a") }).favouriteApps)
    }

    // Phase 3 reads the old keys and never writes them (spec §5.3).
    @Test fun readingFavourites_writesNothing() {
        val sp = prefs()
        LauncherPrefs(sp, { listOf("a") }).favouriteApps
        assertNull(sp.getString("favourite_apps", null))
    }

    @Test fun legacyRowOrder_readsTheOldKey_unknownIsAppsFirst() {
        val sp = prefs()
        assertEquals(RowOrder.AppsFirst, LauncherPrefs(sp, { null }).legacyRowOrder)
        sp.edit().putString("home_row_order", "OnNowFirst").commit()
        assertEquals(RowOrder.OnNowFirst, LauncherPrefs(sp, { null }).legacyRowOrder)
        sp.edit().putString("home_row_order", "Sideways").commit()
        assertEquals(RowOrder.AppsFirst, LauncherPrefs(sp, { null }).legacyRowOrder)
    }

    @Test fun reducedEffects_defaultsOff_andPersists() {
        val sp = prefs()
        assertFalse(LauncherPrefs(sp, { null }).reducedEffects)
        LauncherPrefs(sp, { null }).reducedEffects = true
        assertTrue(LauncherPrefs(sp, { null }).reducedEffects)
    }

    @Test fun focusKey_roundTripsPerScreen() {
        val sp = prefs()
        val p = LauncherPrefs(sp, { null })
        p.setFocusKey("home", "onNow|c1|0")
        p.setFocusKey("appsTab", "x")
        assertEquals("onNow|c1|0", LauncherPrefs(sp, { null }).focusKey("home"))
        assertEquals("x", p.focusKey("appsTab"))
        p.setFocusKey("home", null)
        assertNull(p.focusKey("home"))
    }

    @Test fun look_roundTrips_andSurvivesANewInstance() {
        val sp = prefs()
        val want = Look(ThemeId.Plum, AccentId.Coral, TextSize.Large, ClockChoice.Hidden, FocusStyle.RingOnly)
        LauncherPrefs(sp, { null }).look = want
        assertEquals(want, LauncherPrefs(sp, { null }).look)
    }

    @Test fun look_nothingSaved_isTodaysLook() = assertEquals(Look(), LauncherPrefs(prefs(), { null }).look)

    @Test fun look_unknownSavedNames_fallBackToDefaults() {
        val sp = prefs()
        sp.edit().putString("look_theme", "Sepia").putString("look_accent", "").putString("look_clock", "").putString("look_text", "Huge").putString("look_focus", "Sparkle").commit()
        assertEquals(Look(), LauncherPrefs(sp, { null }).look)
    }

    @Test fun look_clockNotChosen_staysNotChosen() {
        val sp = prefs()
        LauncherPrefs(sp, { null }).look = Look(theme = ThemeId.Black)
        assertNull(LauncherPrefs(sp, { null }).look.clock)
    }

    @Test fun look_aChosenClock_canBeUnchosenAgain() {
        val sp = prefs()
        LauncherPrefs(sp, { null }).look = Look(clock = ClockChoice.H24)
        LauncherPrefs(sp, { null }).look = Look(clock = null)
        assertNull(LauncherPrefs(sp, { null }).look.clock)
    }

    @Test fun homeLayout_roundTrips() {
        val sp = prefs()
        val want = HomeLayout(TileSize.Large, TileShape.FullBanner, Spacing.Roomy, false, RowOrder.OnNowFirst, OnNowLayout.SixSmall)
        LauncherPrefs(sp, { null }).homeLayout = want
        assertEquals(want, LauncherPrefs(sp, { null }).homeLayout)
    }

    @Test fun homeLayout_nothingSaved_isTodaysHome() = assertEquals(HomeLayout(), LauncherPrefs(prefs(), { null }).homeLayout)

    @Test fun homeLayout_unknownSavedNames_fallBackToDefaults() {
        val sp = prefs()
        sp.edit().putString("home_tile_size", "Huge").putString("home_on_now", "").putString("home_tile_shape", "Round").putString("home_spacing", "x").putString("home_row_order", "y").commit()
        assertEquals(HomeLayout(), LauncherPrefs(sp, { null }).homeLayout)
    }
}
