package com.cursorandroid.app.ui.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxSearchTest {
    @Test
    fun emptyQueryIsNotPrFilter() {
        val q = InboxSearch.parse("")
        assertTrue(q.isEmpty)
        assertNull(q.prUrlForApi)
        assertNull(q.prNumber)
    }

    @Test
    fun githubPullUrlNormalizesForApi() {
        val q = InboxSearch.parse("https://github.com/acme/app/pull/42")
        assertEquals("https://github.com/acme/app/pull/42", q.prUrlForApi)
        assertTrue(q.textNeedle.isEmpty())
    }

    @Test
    fun githubPullWithoutSchemeStillMatches() {
        val q = InboxSearch.parse("github.com/acme/app/pull/7")
        assertEquals("https://github.com/acme/app/pull/7", q.prUrlForApi)
    }

    @Test
    fun gitLabMergeRequestUrlMatches() {
        val q = InboxSearch.parse("https://gitlab.com/acme/app/-/merge_requests/9")
        assertEquals("https://gitlab.com/acme/app/-/merge_requests/9", q.prUrlForApi)
    }

    @Test
    fun hashNumberUsesClientSidePrFilter() {
        val q = InboxSearch.parse("#12")
        assertNull(q.prUrlForApi)
        assertEquals(12, q.prNumber)
        assertTrue(q.hasPrFilter)
    }

    @Test
    fun bareDigitsStayTextSearch() {
        val q = InboxSearch.parse("404")
        assertEquals("404", q.textNeedle)
        assertNull(q.prNumber)
    }

    @Test
    fun regularTextStaysNeedleSearch() {
        val q = InboxSearch.parse("thermal nexus")
        assertEquals("thermal nexus", q.textNeedle)
        assertFalse(q.hasPrFilter)
    }

    @Test
    fun prNumberMatchesPullPath() {
        assertTrue(InboxSearch.matchesPrNumber("https://github.com/acme/app/pull/12", 12))
        assertFalse(InboxSearch.matchesPrNumber("https://github.com/acme/app/pull/13", 12))
    }
}
