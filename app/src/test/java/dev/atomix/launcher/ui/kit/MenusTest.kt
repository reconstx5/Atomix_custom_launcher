package dev.atomix.launcher.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Test

class MenusTest {
    @Test fun menuHeight_isItsPaddingTitleAndItems() = assertEquals(20 + 36 + 4 * 82 + 20, menuHeight(4))

    @Test fun menu_opensJustAboveItsAnchor() = assertEquals(76, menuTop(100, menuHeight(4)))

    @Test fun menu_neverStartsAboveThePage() = assertEquals(0, menuTop(10, menuHeight(4)))

    @Test fun menu_staysClearOfTheHintBar() = assertEquals(754 - menuHeight(4), menuTop(700, menuHeight(4)))

    @Test fun aMenuTallerThanThePage_startsAtTheTop() = assertEquals(0, menuTop(300, 900))

    @Test fun textRoom_isUnchangedAtStandardText() {
        assertEquals(470, textRoom(470, 1f))
        assertEquals(330, textRoom(330, 1f))
    }

    @Test fun textRoom_growsWithLargeText_soWordsThatFitStillFit() {
        assertEquals(540, textRoom(470, 1.15f))
        assertEquals(379, textRoom(330, 1.15f))
    }
}
