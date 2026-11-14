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

    @Test fun artwork_isNarrowerOnANarrowerCard_andNeverCrowdsTheWords() {
        assertEquals(370, smallArtWidth(646))
        assertEquals(331, smallArtWidth(561))
        assertEquals(203, smallArtWidth(433))
    }

    @Test fun onANarrowerCard_theWordsKeepAtLeast200() {
        assertEquals(284, smallCardTextRoom(561, hasArt = true, textScale = 1f))
        assertEquals(284, smallCardTextRoom(433, hasArt = true, textScale = 1f))
        assertEquals(433 - 60, smallCardTextRoom(433, hasArt = false, textScale = 1f))
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
}
