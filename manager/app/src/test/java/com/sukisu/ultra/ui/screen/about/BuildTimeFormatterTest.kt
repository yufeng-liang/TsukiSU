package com.sukisu.ultra.ui.screen.about

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class BuildTimeFormatterTest {
    @Test
    fun formatsSameInstantInDifferentTimeZones() {
        val epochMillis = 0L
        assertEquals("1970-01-01 08:00", formatBuildTime(epochMillis, TimeZone.getTimeZone("Asia/Shanghai")))
        assertEquals("1970-01-01 00:00", formatBuildTime(epochMillis, TimeZone.getTimeZone("UTC")))
        assertEquals("1969-12-31 19:00", formatBuildTime(epochMillis, TimeZone.getTimeZone("America/New_York")))
    }
}
