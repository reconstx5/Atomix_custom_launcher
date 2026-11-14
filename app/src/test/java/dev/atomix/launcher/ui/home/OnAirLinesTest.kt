package dev.atomix.launcher.ui.home

import dev.atomix.launcher.livetv.source.Channel
import dev.atomix.launcher.livetv.source.Programme
import dev.atomix.launcher.livetv.source.onAir
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OnAirLinesTest {
    private lateinit var zone: TimeZone

    @Before fun utc() { zone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restore() { TimeZone.setDefault(zone) }

    private val min = 60_000L
    private val now = Programme(title = "Fishing and Adventure", start = 0, end = 25 * min)
    private val next = Programme(title = "Rural Delivery", start = 25 * min, end = 55 * min)
    private fun tvnz(n: Programme? = null, x: Programme? = null) = Channel(id = "1", name = "TVNZ 1", now = n, next = x)

    @Test fun onAir_givesTitleTimesNextAndProgress() {
        val l = onAirLines(onAir(tvnz(now, next), 6 * min), is24 = true)
        assertEquals("Fishing and Adventure", l.title)
        assertEquals("00:00–00:25 • 19 min left", l.time)
        assertEquals("Next: Rural Delivery", l.next)
        assertEquals(0.24f, l.progress!!, 0.001f)
    }

    @Test fun programmeEnded_nothingNewYet_saysNoInformation() {
        val l = onAirLines(onAir(tvnz(now), 30 * min), is24 = true)
        assertEquals(OnAirLines("No programme information", null, null, null), l)
    }

    @Test fun noGuideData_saysNoInformation() =
        assertEquals("No programme information", onAirLines(onAir(tvnz(), 0), true).title)

    @Test fun twentyFourHourTimes_areZeroPadded() = assertEquals("07:36", clockText(7 * 60 * min + 36 * min, true))

    @Test fun titleAlone_isTheProgramme_orNoInformation() {
        assertEquals("Fishing and Adventure", onAirTitle(onAir(tvnz(now, next), 6 * min)))
        assertEquals("No programme information", onAirTitle(onAir(tvnz(), 0)))
        assertEquals("No programme information", onAirTitle(onAir(tvnz(now), 30 * min)))
    }
}
