package com.cursorandroid.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CursorDashboardUsageTest {
    @Test
    fun parsePlanUsageBars() {
        val json = """
            {
              "planUsage": {
                "autoPercentUsed": 42.5,
                "apiPercentUsed": 10.0
              },
              "billingCycleEnd": 1_700_000_000_000
            }
        """.trimIndent().replace("_", "")
        val parsed = parseDashboardPeriodUsage(json)
        assertEquals(42.5, parsed?.cursorModelsPercent!!, 0.001)
        assertEquals(10.0, parsed.otherModelsPercent!!, 0.001)
        assertEquals(1_700_000_000_000L, parsed.billingCycleEndMs)
    }

    @Test
    fun missingPlanUsageHidesBars() {
        assertNull(parseDashboardPeriodUsage("""{"billingCycleEnd": 100}"""))
        assertNull(parseDashboardPeriodUsage("not json"))
    }

    @Test
    fun storedRoundTrip() {
        val period = DashboardPeriodUsage(5.0, 6.0, 123L)
        val again = period.toStored().toPeriod()
        assertEquals(period.cursorModelsPercent, again.cursorModelsPercent)
        assertEquals(period.otherModelsPercent, again.otherModelsPercent)
        assertEquals(period.billingCycleEndMs, again.billingCycleEndMs)
    }
}
