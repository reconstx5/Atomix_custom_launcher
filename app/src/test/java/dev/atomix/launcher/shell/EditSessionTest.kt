package dev.atomix.launcher.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSessionTest {
    private fun abc() = EditSession(listOf("a", "b", "c"))

    @Test fun pickUpMovePlace_reorders() {
        val s = abc()
        s.pickUp("b")
        assertEquals("b", s.held)
        s.move(+1)
        assertEquals(listOf("a", "c", "b"), s.order)
        s.place()
        assertEquals(listOf("a", "c", "b"), s.order)
        assertNull(s.held)
        assertTrue(s.changed)
    }

    @Test fun cancelMove_restoresTheOrderFromPickUp() {
        val s = abc()
        s.pickUp("b"); s.move(-1)
        assertEquals(listOf("b", "a", "c"), s.order)
        s.cancelMove()
        assertEquals(listOf("a", "b", "c"), s.order)
        assertNull(s.held)
        assertFalse(s.changed)
    }

    @Test fun cancelMove_keepsEarlierPlacedMoves() {
        val s = abc()
        s.pickUp("a"); s.move(+1); s.place()
        s.pickUp("c"); s.move(-2); s.cancelMove()
        assertEquals(listOf("b", "a", "c"), s.order)
    }

    @Test fun moveAtTheEnds_isNoOp() {
        val s = abc()
        s.pickUp("c"); s.move(+1)
        assertEquals(listOf("a", "b", "c"), s.order)
        s.pickUp("a"); s.move(-1)
        assertEquals(listOf("a", "b", "c"), s.order)
    }

    @Test fun moveWithNothingHeld_isNoOp() {
        val s = abc()
        s.move(+1)
        assertEquals(listOf("a", "b", "c"), s.order)
    }

    @Test fun position_isOneBasedNofM() {
        val s = abc()
        assertNull(s.position())
        s.pickUp("b")
        assertEquals(2 to 3, s.position())
        s.move(+1)
        assertEquals(3 to 3, s.position())
    }

    @Test fun remove_dropsAndMarksChanged() {
        val s = abc()
        s.remove("a")
        assertEquals(listOf("b", "c"), s.order)
        assertTrue(s.changed)
    }

    @Test fun removeHeld_clearsHeld() {
        val s = abc()
        s.pickUp("b"); s.remove("b")
        assertNull(s.held)
        assertEquals(listOf("a", "c"), s.order)
    }

    @Test fun pickUpUnknownKey_holdsNothing() {
        val s = abc()
        s.pickUp("z")
        assertNull(s.held)
    }
}
