package dev.atomix.launcher.atomix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AtomixProcessTest {
    @Test fun findsTheAtomixProcessAmongTheAppsOwn() {
        val running = listOf("dev.atomix.launcher" to 100, "dev.atomix.launcher:atomix" to 200)
        assertEquals(200, atomixPid(running))
    }

    @Test fun nullWhenItIsNotRunning() {
        assertNull(atomixPid(listOf("dev.atomix.launcher" to 100)))
        assertNull(atomixPid(emptyList()))
    }

    @Test fun neverPicksTheMainProcess() {
        assertNull(atomixPid(listOf("dev.atomix.launcher" to 100, "other.app:atomix" to 300)))
    }
}
