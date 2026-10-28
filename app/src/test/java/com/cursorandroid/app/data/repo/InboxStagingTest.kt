package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxStagingTest {
    @Test
    fun stagedAgentShowsBeforeListReturnsIt() {
        val hub = InboxStagingHub()
        val existing = listOf(agent("old"))
        hub.stage(agent("new", "RUNNING"))
        val merged = hub.merge(existing, emptyList())
        assertEquals(listOf("new", "old"), merged.map { it.id })
    }

    @Test
    fun listMatchDropsStaging() {
        val hub = InboxStagingHub()
        hub.stage(agent("new", "RUNNING"))
        val merged = hub.merge(emptyList(), listOf(agent("new", "CREATING")))
        assertEquals(1, merged.size)
        assertEquals("CREATING", merged.single().status)
        assertTrue(hub.merge(emptyList(), emptyList()).isEmpty())
    }

    private fun agent(id: String, status: String) = AgentSummary(id = id, status = status)
}
