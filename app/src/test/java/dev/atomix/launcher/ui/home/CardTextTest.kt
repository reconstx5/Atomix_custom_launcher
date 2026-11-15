package dev.atomix.launcher.ui.home

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    /** The artwork gives way on a narrower card: it always starts 276 from the card's left, as on the board's card. */
    @Test fun artwork_isNarrowerOnANarrowerCard_soTheWordsKeepTheirRoom() {
        assertEquals(370, smallArtWidth(646))
        assertEquals(285, smallArtWidth(561))
        assertEquals(157, smallArtWidth(433))
    }

    @Test fun onANarrowerCard_theWordsHaveTheSameRoomAsOnTheBoardsCard() {
        assertEquals(330, smallCardTextRoom(561, hasArt = true, textScale = 1f))
        assertEquals(330, smallCardTextRoom(433, hasArt = true, textScale = 1f))
        assertEquals(379, smallCardTextRoom(433, hasArt = true, textScale = 1.15f))
        assertEquals(433 - 60, smallCardTextRoom(433, hasArt = false, textScale = 1f))
    }

    @Test fun theFadeOverArtwork_keepsItsDistances_onANarrowerCard() {
        // Solid to 2 px into the artwork, 55 % at 123 px further, clear at 252 px further: the board's card (0.43, 0.62, 0.82).
        assertEquals(listOf(0.43f, 0.62f, 0.82f), smallArtFade(646))
        val narrow = smallArtFade(433)
        assertEquals((433 - 157 + 2) / 433f, narrow[0], 0.0001f)
        assertEquals((433 - 157 + 2 + 123) / 433f, narrow[1], 0.0001f)
        assertEquals(1f, narrow[2], 0.0001f)
    }

    @Test fun smallCard_atItsUsualHeight_isPhase1s() = assertEquals(SmallCardFit(chipTop = 22, textTop = 82, showNext = true), smallCardFit(244, 1f))

    @Test fun smallCard_atItsUsualHeight_stillShowsNextWithLargeText() = assertEquals(SmallCardFit(22, 82, true), smallCardFit(244, 1.15f))

    @Test fun smallCard_aLittleShorter_movesUpToKeepTheNextLine() = assertEquals(SmallCardFit(14, 66, true), smallCardFit(215, 1f))

    @Test fun smallCard_shorterStill_dropsTheNextLine() = assertEquals(SmallCardFit(22, 82, false), smallCardFit(197, 1f))

    @Test fun smallCard_atItsShortest_dropsNextAndMovesUp() = assertEquals(SmallCardFit(14, 66, false), smallCardFit(176, 1f))

    @Test fun largeCard_hasRoomForATwoLineTitle_exceptAtItsShortestWithLargeText() {
        assertEquals(2, largeTitleLines(506, 1f))
        assertEquals(2, largeTitleLines(506, 1.15f))
        assertEquals(2, largeTitleLines(378, 1f))
        assertEquals(1, largeTitleLines(370, 1f))
        assertEquals(1, largeTitleLines(378, 1.15f))
    }

    /** On the board's card the "OK Watch" chip sits clear of the words, bottom right. A narrower card has no room there. */
    @Test fun theWatchHint_movesToTheTop_whenTheWordsWouldRunUnderIt() {
        assertFalse(watchHintOnTop(1050))
        assertFalse(watchHintOnTop(1018))
        assertTrue(watchHintOnTop(1017))
        assertTrue(watchHintOnTop(812))
    }
}
