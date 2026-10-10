package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.ui.status.RunIndicator
import com.cursorandroid.app.ui.status.runIndicator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSettleHubTest {
    private fun agent(id: String, status: String, run: String?) =
        AgentSummary(id = id, status = status, latestRunId = run)

    @Test
    fun publishesOnlyEndedRunsAndDeduplicates() {
        val saved = ArrayList<RunSettled>()
        val hub = RunSettleHub { saved += it }

        assertFalse(hub.publish("a1", "r1", "RUNNING"))
        assertFalse(hub.publish("a1", "r1", "ARCHIVED"))
        assertFalse(hub.publish("a1", "r1", null))
        assertTrue(hub.publish("a1", "r1", "finished", "done"))
        assertFalse(hub.publish("a1", "r1", "FINISHED"))

        assertEquals("FINISHED", hub.current("a1")?.status)
        assertEquals("done", hub.current("a1")?.result)
        assertEquals(1, saved.size)
    }

    @Test
    fun laterResultForSameRunIsKept() {
        val hub = RunSettleHub()
        assertTrue(hub.publish("a1", "r1", "FINISHED"))
        assertTrue(hub.publish("a1", "r1", "FINISHED", "final text"))
        assertEquals("final text", hub.current("a1")?.result)
    }

    @Test
    fun finishedRunFlipsCardFromRunningToDone() {
        val hub = RunSettleHub()
        val cards = listOf(agent("a1", "ACTIVE", "r1"), agent("a2", "ACTIVE", "r9"))
        assertEquals(RunIndicator.Running, runIndicator(cards[0].status))

        hub.publish("a1", "r1", "FINISHED")
        val next = settleAgents(cards, hub.settled.value)

        assertEquals(RunIndicator.Done, runIndicator(next[0].status))
        assertEquals(RunIndicator.Running, runIndicator(next[1].status))
    }

    @Test
    fun erroredRunShowsFailed() {
        val hub = RunSettleHub()
        hub.publish("a1", "r1", "ERROR")
        val next = settleAgents(listOf(agent("a1", "ACTIVE", "r1")), hub.settled.value)
        assertEquals(RunIndicator.Failed, runIndicator(next[0].status))
    }

    @Test
    fun newerRunOnTheAgentIsNotSettledByAnOlderOne() {
        val hub = RunSettleHub()
        hub.publish("a1", "r1", "FINISHED")
        val cards = listOf(agent("a1", "ACTIVE", "r2"))
        assertSame(cards, settleAgents(cards, hub.settled.value))
    }

    @Test
    fun idleAgentIsLeftAlone() {
        val hub = RunSettleHub()
        hub.publish("a1", "r1", "FINISHED")
        val cards = listOf(agent("a1", "IDLE", "r1"))
        assertSame(cards, settleAgents(cards, hub.settled.value))
    }

    @Test
    fun clearDropsTheAgentEntry() {
        val hub = RunSettleHub()
        hub.publish("a1", "r1", "FINISHED")
        hub.clear("a1")
        assertNull(hub.current("a1"))
    }

    @Test
    fun sweepPublishesLiveCardsWhoseRunAlreadyEnded() = runBlocking {
        val hub = RunSettleHub()
        val cards = listOf(
            agent("a1", "ACTIVE", "r1"),
            agent("a2", "ACTIVE", "r2"),
            agent("a3", "IDLE", "r3"),
            agent("a4", "ACTIVE", null),
        )
        val asked = ArrayList<String>()
        val found = hub.sweep(cards) { _, runId ->
            asked += runId
            Run(id = runId, status = if (runId == "r1") "FINISHED" else "RUNNING")
        }

        assertEquals(1, found)
        assertEquals(listOf("r1", "r2"), asked)
        assertEquals("FINISHED", hub.current("a1")?.status)
        assertNull(hub.current("a2"))
    }

    @Test
    fun sweepSkipsRunsAlreadyPublishedAndCapsRequests() = runBlocking {
        val hub = RunSettleHub()
        hub.publish("a0", "r0", "FINISHED")
        val cards = (0..10).map { agent("a$it", "ACTIVE", "r$it") }
        val asked = ArrayList<String>()
        hub.sweep(cards) { _, runId ->
            asked += runId
            null
        }
        assertEquals(RunSettleHub.SWEEP_LIMIT, asked.size)
        assertFalse("r0" in asked)
    }

    @Test
    fun runWinsOverAStaleActiveAgentStatus() {
        val ended = Run(id = "r1", status = "FINISHED")
        assertEquals("FINISHED", settledAgentStatus("ACTIVE", "r1", ended))
        assertEquals("FINISHED", settledAgentStatus("ACTIVE", null, ended))
        assertEquals("ACTIVE", settledAgentStatus("ACTIVE", "r2", ended))
        assertEquals("ACTIVE", settledAgentStatus("ACTIVE", "r1", Run(id = "r1", status = "RUNNING")))
        assertEquals("IDLE", settledAgentStatus("IDLE", "r1", ended))
        assertEquals("ACTIVE", settledAgentStatus("ACTIVE", "r1", null))
        assertEquals(
            RunIndicator.Done,
            runIndicator(settledAgentStatus("ACTIVE", "r1", ended), ended.status),
        )
    }

    @Test
    fun localActivityFlipsOnlyNonLiveCardsToRunning() {
        val hub = RunSettleHub()
        hub.setLocalActive("a", true)
        hub.setLocalActive("b", true)
        val list = listOf(agent("a", "FINISHED", "r1"), agent("b", "ACTIVE", "r2"), agent("c", "FINISHED", "r3"))
        val out = markLocalActive(list, hub.localActive.value)
        assertEquals(listOf("ACTIVE", "ACTIVE", "FINISHED"), out.map { it.status })
        hub.setLocalActive("a", false)
        assertEquals(listOf("FINISHED", "ACTIVE", "FINISHED"), markLocalActive(list, hub.localActive.value).map { it.status })
        assertEquals(list, markLocalActive(list, emptySet()))
    }
}
