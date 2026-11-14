package dev.atomix.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CardTextTest {
    @Test fun withArtwork_theWordsKeepToTheLeftOfIt() = assertEquals(330, smallCardTextRoom(cardW = 646, hasArt = true, textScale = 1f))

    @Test fun withArtwork_largeTextGetsTheRoomItNeeds() = assertEquals(379, smallCardTextRoom(646, hasArt = true, textScale = 1.15f))

    /** "No programme information" is longer than the room beside artwork, and there is none to stay clear of. */
    @Test fun withNoArtwork_theWordsTakeTheCardsWidth() {
        assertEquals(646 - 60, smallCardTextRoom(646, hasArt = false, textScale = 1f))
        assertEquals(646 - 60, smallCardTextRoom(646, hasArt = false, textScale = 1.15f))
    }
}
