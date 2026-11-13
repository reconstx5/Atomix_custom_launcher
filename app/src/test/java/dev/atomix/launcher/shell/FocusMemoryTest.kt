package dev.atomix.launcher.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.atomix.launcher.prefs.LauncherPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FocusMemoryTest {
    private lateinit var prefs: LauncherPrefs
    private lateinit var mem: FocusMemory

    @Before fun setUp() {
        val sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("focus_test", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        prefs = LauncherPrefs(sp) { null }
        mem = FocusMemory(prefs, "home")
    }

    private val rows = mapOf("onNow" to listOf("c1", "c2", "c3"), "apps" to listOf("a1", "a2"))

    @Test fun exactKey_restored() {
        mem.remember(FocusTarget("apps", "a2", 1))
        assertEquals(FocusTarget("apps", "a2", 1), mem.resolve(rows))
    }

    @Test fun exactKey_foundAtNewIndex() {
        mem.remember(FocusTarget("onNow", "c3", 0))
        assertEquals(FocusTarget("onNow", "c3", 2), mem.resolve(rows))
    }

    @Test fun missingKey_fallsBackToSameIndexClamped() {
        mem.remember(FocusTarget("onNow", "c9", 5))
        assertEquals(FocusTarget("onNow", "c3", 2), mem.resolve(mapOf("onNow" to listOf("c1", "c2", "c3"))))
    }

    @Test fun emptyRow_fallsToFirstNonEmptyRow() {
        mem.remember(FocusTarget("onNow", "c1", 0))
        assertEquals(FocusTarget("apps", "a1", 0), mem.resolve(mapOf("onNow" to emptyList(), "apps" to listOf("a1"))))
    }

    @Test fun nothingRemembered_isFirstItemOfFirstNonEmptyRow() {
        assertEquals(FocusTarget("onNow", "c1", 0), mem.resolve(rows))
    }

    @Test fun allEmpty_isNull() {
        mem.remember(FocusTarget("onNow", "c1", 0))
        assertNull(mem.resolve(mapOf("onNow" to emptyList(), "apps" to emptyList())))
    }

    @Test fun survivesNewInstance() {
        mem.remember(FocusTarget("apps", "a|b", 1))
        assertEquals(FocusTarget("apps", "a|b", 1), FocusMemory(prefs, "home").resolve(mapOf("apps" to listOf("x", "a|b"))))
    }

    @Test fun screensAreSeparate() {
        mem.remember(FocusTarget("apps", "a2", 1))
        assertEquals(FocusTarget("onNow", "c1", 0), FocusMemory(prefs, "liveTab").resolve(rows))
    }
}
