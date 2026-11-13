package dev.atomix.launcher.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebBackTest {
    private val base = "http://192.168.8.65:8787/"

    @Test fun atRoot_closes() {
        assertTrue(shouldClose("#/library/3", "${base}#/library/3", canGoBack = true))
    }

    @Test fun afterNavigation_goesBack() {
        assertFalse(shouldClose("#/library/3", "${base}#/play/9", canGoBack = true))
    }

    @Test fun noHistory_closes() {
        assertTrue(shouldClose("#/library/3", "${base}#/play/9", canGoBack = false))
    }
}
