package dev.atomix.launcher.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RowStoreTest {
    private lateinit var sp: SharedPreferences

    @Before fun setUp() {
        sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("row_store_test", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
    }

    @Test fun firstRead_buildsFromOldKeys_andLeavesThemAlone() {
        sp.edit().putString("favourite_apps", "a\nb").putString("home_row_order", "OnNowFirst").commit()
        val store = RowStore(LauncherPrefs(sp) { null })
        assertEquals(listOf(ROW_ON_NOW, ROW_APPS, ROW_INPUTS), store.rows.value.map { it.id })
        assertEquals(listOf("a", "b"), store.rows.value.first { it.id == ROW_APPS }.apps)
        store.update { it.withApp(ROW_APPS, "c", true) }
        assertEquals("a\nb", sp.getString("favourite_apps", null))
        assertEquals("OnNowFirst", sp.getString("home_row_order", null))
    }

    @Test fun update_savesAndEmits() {
        val store = RowStore(LauncherPrefs(sp) { null })
        store.update { it.withRowAdded("Tools") }
        assertEquals(4, store.rows.value.size)
        assertEquals(4, RowStore(LauncherPrefs(sp) { null }).rows.value.size)
    }

    @Test fun unreadableSavedRows_areRebuiltFromOldKeys_neverAnEmptyHome() {
        sp.edit().putString("home_rows", "{").putString("favourite_apps", "a").commit()
        assertEquals(listOf("a"), RowStore(LauncherPrefs(sp) { null }).rows.value.first().apps)
    }

    // Before phase 2's favourites were ever saved: the old dock, Atomix's own tile first, as phase 2's first start did.
    @Test fun noOldKeys_startsFromTheDock_withoutWritingTheOldKey() {
        val store = RowStore(LauncherPrefs(sp) { listOf("x") })
        assertEquals(listOf(ATOMIX_APP_KEY, "x"), store.rows.value.first().apps)
        assertEquals(null, sp.getString("favourite_apps", null))
    }
}
