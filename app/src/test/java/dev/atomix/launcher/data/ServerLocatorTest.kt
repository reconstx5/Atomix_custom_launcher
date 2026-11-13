package dev.atomix.launcher.data

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ServerLocatorTest {
    private class FakeProbe(private val good: Set<String>) : ServerProbe {
        val asked = mutableListOf<String>()
        override suspend fun isAtomix(base: HttpUrl): Boolean {
            synchronized(asked) { asked += base.host }
            return base.host in good
        }
    }

    private val saved = "http://10.0.0.5:8787/".toHttpUrl()
    private val last = "http://10.0.0.6:8787/".toHttpUrl()

    @Test fun savedWins() = runTest {
        val probe = FakeProbe(setOf("10.0.0.5", "192.168.8.65"))
        val locator = ServerLocator(probe) { listOf("192.168.8.65") }
        assertEquals(saved, locator.locate(saved, last))
        assertEquals(listOf("10.0.0.5"), probe.asked)
    }

    @Test fun fallsBackToSubnet() = runTest {
        val locator = ServerLocator(FakeProbe(setOf("192.168.8.65"))) { listOf("192.168.8.2", "192.168.8.65") }
        assertEquals("http://192.168.8.65:8787/".toHttpUrl(), locator.locate(saved, last))
    }

    @Test fun allCandidatesFail_returnsNull() = runTest {
        val locator = ServerLocator(FakeProbe(emptySet())) { listOf("192.168.8.2") }
        assertNull(locator.locate(saved, last))
    }

    @Test fun subnetHosts_excludeSelf() {
        val hosts = subnetHostsOf("192.168.8.67")
        assertEquals(253, hosts.size)
        assertFalse("192.168.8.67" in hosts)
        assertEquals("192.168.8.1", hosts.first())
    }
}
