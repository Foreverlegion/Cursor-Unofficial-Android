package com.cursorandroid.app.ui.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxSearchLogicTest {
    @Test
    fun blankQueryClearsFilters() {
        val parsed = parseInboxSearchQuery("  ")
        assertNull(parsed.apiPrUrl)
        assertEquals("", parsed.textNeedle)
        assertFalse(parsed.hasPrFilter)
    }

    @Test
    fun prUrlsRouteToApiFilter() {
        val github = parseInboxSearchQuery("https://github.com/acme/app/pull/12")
        assertEquals("https://github.com/acme/app/pull/12", github.apiPrUrl)
        assertEquals("", github.textNeedle)

        val gitlab = parseInboxSearchQuery("gitlab.com/acme/app/-/merge_requests/4")
        assertEquals("https://gitlab.com/acme/app/-/merge_requests/4", gitlab.apiPrUrl)

        val hash = parseInboxSearchQuery("#99")
        assertEquals("#99", hash.apiPrUrl)
    }

    @Test
    fun plainTextUsesLocalNeedle() {
        val parsed = parseInboxSearchQuery("thermal nexus")
        assertNull(parsed.apiPrUrl)
        assertEquals("thermal nexus", parsed.textNeedle)
    }

    @Test
    fun normalizeAddsHttpsScheme() {
        assertEquals(
            "https://github.com/org/repo/pull/1",
            normalizePrUrl("github.com/org/repo/pull/1"),
        )
    }

    @Test
    fun agentPrMatchSupportsHashFilter() {
        assertTrue(
            agentMatchesPrFilter(
                "https://github.com/acme/app/pull/12",
                "#12",
            ),
        )
        assertFalse(agentMatchesPrFilter("https://github.com/acme/app/pull/3", "#12"))
    }
}
