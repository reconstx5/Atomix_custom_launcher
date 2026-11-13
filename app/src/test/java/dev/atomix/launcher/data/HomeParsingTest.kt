package dev.atomix.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeParsingTest {
    private fun fixture(): String =
        javaClass.classLoader!!.getResource("home.json")!!.readText()

    @Test
    fun fixture_parses() {
        val home = AtomixJson.parseHome(fixture())
        val hero = assertNotNullAndGet(home.hero)
        assertEquals("Brothers", hero.title)
        assertNotNull(hero.backdrop)
        assertEquals("#997a38", hero.tint)
        assertEquals(6405.0, hero.progress!!.duration!!, 1.0)
        assertTrue(home.rows.any { it.key == "recent-1" && it.items.size == 2 })
        assertTrue(home.libraries.any { it.id == 1L })
    }

    @Test
    fun liveItems_withStringIds_areDropped_rowKept() {
        val home = AtomixJson.parseHome(fixture())
        val live = home.rows.first { it.key == "live-now" }
        assertTrue(live.items.isEmpty())
    }

    @Test
    fun missingOptionalFields_parse() {
        val home = AtomixJson.parseHome(
            """{"hero":null,"rows":[{"key":"x","title":"X","items":[{"id":1,"kind":"movie","title":"T"}]}]}""",
        )
        assertNull(home.hero)
        val item = home.rows.single().items.single()
        assertNull(item.backdrop)
        assertNull(item.logo)
        assertNull(item.progress)
    }

    private fun <T> assertNotNullAndGet(v: T?): T {
        assertNotNull(v); return v!!
    }
}
