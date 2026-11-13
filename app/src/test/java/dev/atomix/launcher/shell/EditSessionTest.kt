package dev.atomix.launcher.shell

import org.junit.Assert.assertEquals
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

    @Test fun pickUpUnknownKey_holdsNothing() {
        val s = abc()
        s.pickUp("z")
        assertNull(s.held)
    }

    @Test fun ok_picksUp_arrowsMove_okPutsDown() {
        val s = abc()
        assertEquals(EditOutcome.Handled, s.onKey(EditKey.Ok, "b")); assertEquals("b", s.held)
        assertEquals(EditOutcome.Handled, s.onKey(EditKey.End, "b")); assertEquals(listOf("a", "c", "b"), s.order)
        assertEquals(EditOutcome.Placed, s.onKey(EditKey.Ok, "b")); assertNull(s.held)
    }

    @Test fun back_whileHolding_putsDownWhereItIs() {
        val s = abc(); s.onKey(EditKey.Ok, "c"); s.onKey(EditKey.Front, "c")
        assertEquals(EditOutcome.Placed, s.onKey(EditKey.Back, "c"))
        assertEquals(listOf("a", "c", "b"), s.order); assertNull(s.held)
    }

    @Test fun back_withNothingHeld_finishes() = assertEquals(EditOutcome.Finished, abc().onKey(EditKey.Back, "a"))

    @Test fun arrows_withNothingHeld_areLeftToFocus() {
        val s = abc()
        assertEquals(EditOutcome.Ignored, s.onKey(EditKey.End, "a")); assertEquals(listOf("a", "b", "c"), s.order)
    }

    @Test fun ok_onNothingOrAnUnknownItem_isIgnored() {
        assertEquals(EditOutcome.Ignored, abc().onKey(EditKey.Ok, null))
        assertEquals(EditOutcome.Ignored, abc().onKey(EditKey.Ok, "z"))
    }
}
