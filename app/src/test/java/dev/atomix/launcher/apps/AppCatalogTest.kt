package dev.atomix.launcher.apps

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AppCatalogTest {
    private fun app(pkg: String) = AppEntry(pkg, pkg, null, ColorDrawable(), Intent())
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test fun emptyUntilLoaded_thenLoadsOnce() {
        var loads = 0
        val catalog = AppCatalog({ loads++; listOf(app("a")) }, scope, dispatcher)
        assertEquals(emptyList<AppEntry>(), catalog.apps.value)
        scope.advanceUntilIdle()
        assertEquals(listOf("a"), catalog.apps.value.map { it.packageName })
        catalog.apps.value; catalog.byPackage("a")
        assertEquals(1, loads)
    }

    @Test fun invalidate_reloads() {
        var loads = 0
        val catalog = AppCatalog({ loads++; listOf(app("a"), app("b")).take(loads) }, scope, dispatcher)
        scope.advanceUntilIdle()
        catalog.invalidate()
        scope.advanceUntilIdle()
        assertEquals(2, loads)
        assertEquals(listOf("a", "b"), catalog.apps.value.map { it.packageName })
    }

    @Test fun loadFailure_keepsPreviousList() {
        var loads = 0
        val catalog = AppCatalog({ if (++loads > 1) error("pm died") else listOf(app("a")) }, scope, dispatcher)
        scope.advanceUntilIdle()
        catalog.invalidate()
        scope.advanceUntilIdle()
        assertEquals(listOf("a"), catalog.apps.value.map { it.packageName })
    }

    @Test fun byPackage_findsOrNull() {
        val catalog = AppCatalog({ listOf(app("a")) }, scope, dispatcher)
        scope.advanceUntilIdle()
        assertEquals("a", catalog.byPackage("a")?.packageName)
        assertNull(catalog.byPackage("zz"))
    }
}
