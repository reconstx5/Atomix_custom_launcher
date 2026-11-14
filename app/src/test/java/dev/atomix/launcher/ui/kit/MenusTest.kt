package dev.atomix.launcher.ui.kit

import org.junit.Assert.assertEquals
import org.junit.Test

class MenusTest {
    @Test fun menuHeight_isItsPaddingTitleAndItems() = assertEquals(20 + 36 + 4 * 82 + 20, menuHeight(4))

    @Test fun menu_opensJustAboveItsAnchor() = assertEquals(76, menuTop(100, menuHeight(4)))

    @Test fun menu_neverStartsAboveThePage() = assertEquals(0, menuTop(10, menuHeight(4)))

    @Test fun menu_staysClearOfTheHintBar() = assertEquals(754 - menuHeight(4), menuTop(700, menuHeight(4)))

    @Test fun aMenuTallerThanThePage_startsAtTheTop() = assertEquals(0, menuTop(300, 900))
}
