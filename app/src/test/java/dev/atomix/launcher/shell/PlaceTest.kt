package dev.atomix.launcher.shell

import dev.atomix.launcher.ui.kit.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceTest {
    @Test fun back_goesUpOneLevel() {
        assertNull(Place.Home.back())
        for (p in listOf(Place.LiveTv, Place.Apps, Place.Inputs, Place.Settings)) assertEquals(Place.Home, p.back())
        assertEquals(Place.Apps, Place.ManageApps.back())
    }

    @Test fun topBar_marksATabOrARoundButton_neverBoth() {
        assertEquals(Tab.Apps, Place.ManageApps.tab); assertNull(Place.ManageApps.button)
        assertNull(Place.Settings.tab); assertEquals(TopButton.Settings, Place.Settings.button)
        assertNull(Place.Inputs.tab); assertEquals(TopButton.Inputs, Place.Inputs.button)
        assertEquals(Place.LiveTv, Place.of(Tab.LiveTv))
    }
}
