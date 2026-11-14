package dev.atomix.launcher.prefs

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test fun clock_followsTheTvUntilChosen() {
        assertTrue(clockIs24(null, tvIs24 = true))
        assertFalse(clockIs24(null, tvIs24 = false))
    }

    @Test fun clock_aChoiceWinsOverTheTv() {
        assertTrue(clockIs24(ClockChoice.H24, tvIs24 = false))
        assertFalse(clockIs24(ClockChoice.H12, tvIs24 = true))
    }

    @Test fun clock_hidden_keepsTheTvsFormatForProgrammeTimes() {
        assertFalse(clockIs24(ClockChoice.Hidden, tvIs24 = false))
        assertTrue(clockIs24(ClockChoice.Hidden, tvIs24 = true))
    }

    @Test fun clock_theRowShowsTheTvsFormatAsChosen_untilTheOwnerChooses() {
        assertEquals(ClockChoice.H12, shownClock(null, tvIs24 = false))
        assertEquals(ClockChoice.H24, shownClock(null, tvIs24 = true))
        assertEquals(ClockChoice.Hidden, shownClock(ClockChoice.Hidden, tvIs24 = true))
        assertEquals(ClockChoice.H24, shownClock(ClockChoice.H24, tvIs24 = false))
    }

    @Test fun onlyHidden_takesTheClockOffTheTopBar() {
        assertTrue(topBarShowsClock(null))
        assertTrue(topBarShowsClock(ClockChoice.H12))
        assertTrue(topBarShowsClock(ClockChoice.H24))
        assertFalse(topBarShowsClock(ClockChoice.Hidden))
    }

    @Test fun focusRow_withFullEffects_isTheChoice() {
        assertEquals(FocusRow("Ring only", null, true), focusHighlightRow(FocusStyle.RingOnly, reduced = false))
        assertEquals(FocusRow("Ring and glow", null, true), focusHighlightRow(FocusStyle.RingAndGlow, reduced = false))
    }

    @Test fun focusRow_withReducedEffects_isAStatement_whateverWasChosen() {
        assertEquals(FocusRow("Ring only", "Reduced effects is on", false), focusHighlightRow(FocusStyle.RingAndGlow, reduced = true))
        assertEquals(FocusRow("Ring only", "Reduced effects is on", false), focusHighlightRow(FocusStyle.RingOnly, reduced = true))
    }
}
