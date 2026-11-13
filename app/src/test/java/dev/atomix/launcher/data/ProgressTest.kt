package dev.atomix.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {
    @Test fun minutesLeft_fromProgressDuration() = assertEquals(40, minutesLeft(Progress(600.0, 3000.0), null))
    @Test fun minutesLeft_fromItemDuration() = assertEquals(40, minutesLeft(Progress(600.0), 3000.0))
    @Test fun minutesLeft_watched_isNull() = assertNull(minutesLeft(Progress(10.0, 3000.0, watched = true), null))
    @Test fun minutesLeft_noProgress_isNull() = assertNull(minutesLeft(null, 3000.0))
    @Test fun minutesLeft_notStarted_isNull() = assertNull(minutesLeft(Progress(0.0, 3000.0), null))
    @Test fun progressFraction_half() = assertEquals(0.5f, progressFraction(Progress(1500.0, 3000.0), null)!!, 0.0001f)
    @Test fun progressFraction_notStarted_isNull() = assertNull(progressFraction(Progress(0.0, 3000.0), null))
}
