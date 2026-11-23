package com.droiddeck.launcher.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudClockReadingsTest {
    // 0.7.1 showed the GPU and CPU clocks beside their load; since every reading has its own switch they are readings of their own
    @Test fun theClocksAreReadingsOnByDefault() {
        for (key in listOf("showGpuClock", "showCpuClock")) {
            assertTrue(key in DroidDeckHudConfig.ORDER)
            assertEquals("1", DroidDeckHudConfig.DEFAULTS[key])
        }
    }

    @Test fun eachClockFollowsItsTemperatureWhereTheSizeShowsIt() {
        for (size in listOf(DroidDeckHudSize.FULL, DroidDeckHudSize.TILES, DroidDeckHudSize.PILL, DroidDeckHudSize.STRIP)) {
            val rows = DroidDeckHudSize.ROWS.getValue(size)
            assertEquals(rows.indexOf("showGpuTemp") + 1, rows.indexOf("showGpuClock"))
            assertEquals(rows.indexOf("showTemp") + 1, rows.indexOf("showCpuClock"))
        }
        assertTrue("showGpuClock" in DroidDeckHudSize.ROWS.getValue(DroidDeckHudSize.MEGA))
    }
}
