package dev.atomix.launcher.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class LookTest {
    @Test fun defaults_areTodaysLook() =
        assertEquals(Look(ThemeId.Midnight, AccentId.Ice, TextSize.Standard, null, FocusStyle.RingAndGlow), Look())

    @Test fun enumOr_fallsBackOnAnUnknownOrMissingName() {
        assertEquals(ThemeId.Plum, enumOr("Plum", ThemeId.Midnight))
        assertEquals(ThemeId.Midnight, enumOr("Sepia", ThemeId.Midnight))
        assertEquals(ThemeId.Midnight, enumOr("", ThemeId.Midnight))
        assertEquals(ThemeId.Midnight, enumOr(null, ThemeId.Midnight))
    }

    @Test fun labels_areTheWordsSettingsShows() {
        assertEquals(listOf("Midnight", "Black", "Graphite", "Plum"), ThemeId.entries.map { it.label })
        assertEquals(listOf("Ice", "Mint", "Amber", "Coral", "Violet"), AccentId.entries.map { it.label })
        assertEquals(listOf("Standard", "Large"), TextSize.entries.map { it.label })
        assertEquals(listOf("24-hour", "12-hour", "Hidden"), ClockChoice.entries.map { it.label })
        assertEquals(listOf("Ring and glow", "Ring only"), FocusStyle.entries.map { it.label })
    }

    @Test fun largeText_isFifteenPercentBigger() {
        assertEquals(1f, TextSize.Standard.scale)
        assertEquals(1.15f, TextSize.Large.scale)
    }
}
