package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLinkTest {
    @Test
    fun cursorAgentUrlOpensBcId() {
        val link = SafeLinks.agentLink("https://cursor.com/agents/bc-abc123")
        assertEquals("bc-abc123", link?.agentId)
        assertFalse(link!!.invalid)
    }

    @Test
    fun wwwHostQueryAndTrailingSlashStillOpen() {
        val link = SafeLinks.agentLink("https://www.cursor.com/agents/bc-abc_12-XY/?tab=files")
        assertEquals("bc-abc_12-XY", link?.agentId)
        assertFalse(link!!.invalid)
    }

    @Test
    fun unknownIdStaysOnInbox() {
        val missing = SafeLinks.agentLink("https://cursor.com/agents/not-an-agent")
        assertNull(missing?.agentId)
        assertTrue(missing!!.invalid)
        val short = SafeLinks.agentLink("https://cursor.com/agents/bc-ab")
        assertNull(short?.agentId)
        assertTrue(short!!.invalid)
        val extra = SafeLinks.agentLink("https://cursor.com/agents/bc-abc123/files")
        assertNull(extra?.agentId)
        assertTrue(extra!!.invalid)
        val bare = SafeLinks.agentLink("https://cursor.com/agents")
        assertNull(bare?.agentId)
        assertTrue(bare!!.invalid)
    }

    @Test
    fun otherHostsAreNotAgentLinks() {
        assertNull(SafeLinks.agentLink("https://example.com/agents/bc-abc123"))
        assertNull(SafeLinks.agentLink("https://api.cursor.com/agents/bc-abc123"))
        assertNull(SafeLinks.agentLink("http://cursor.com/agents/bc-abc123"))
        assertNull(SafeLinks.agentLink("https://user:pass@cursor.com/agents/bc-abc123"))
        assertNull(SafeLinks.agentLink("https://cursor.com/dashboard"))
    }
}