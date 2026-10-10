package com.cursorandroid.app.data.notify

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollBudgetTest {
    private fun agent(id: String, status: String = "IDLE", run: String? = "run-$id", updated: String = "t0") =
        AgentSummary(id = id, status = status, latestRunId = run, updatedAt = updated)

    @Test
    fun sweepOnlyFetchesRunsThatCanChange() {
        val agents = listOf(
            agent("a", status = "ACTIVE"),
            agent("b"),
            agent("c"),
            agent("d"),
            agent("e", run = null),
        )
        val seen = mapOf("run-a" to "IDLE", "run-b" to "FINISHED", "run-c" to "RUNNING")
        val picked = SweepPlan.targets(agents, { seen[it] }, watched = setOf("run-x")).map { it.id }
        assertEquals(listOf("a", "c", "d"), picked)
    }

    @Test
    fun watchedRunIsSwept() {
        val picked = SweepPlan.targets(listOf(agent("b")), { "FINISHED" }, watched = setOf("run-b"))
        assertEquals(1, picked.size)
    }

    @Test
    fun pruneOnlyPastCapAndKeepsCurrent() {
        val keys = (1..10).map { "r$it" }.toSet()
        assertTrue(SeenPrefs.staleKeys(keys, setOf("r1"), max = 20).isEmpty())
        assertTrue(SeenPrefs.staleKeys(keys, emptySet(), max = 5).isEmpty())
        val stale = SeenPrefs.staleKeys(keys + "approval:x", setOf("r1", "r2"), max = 5) { it.startsWith("approval:") }
        assertEquals(8, stale.size)
        assertFalse("r1" in stale)
        assertFalse("approval:x" in stale)
    }

    @Test
    fun watchWorkerGivesUpOnAuthAndMissing() {
        assertTrue(RunWatchWorker.giveUp(ApiException(404, "gone"), 1))
        assertTrue(RunWatchWorker.giveUp(ApiException(401, "no"), 1))
        assertFalse(RunWatchWorker.giveUp(ApiException(503, "busy"), 1))
        assertFalse(RunWatchWorker.giveUp(RuntimeException("io"), 1))
        assertTrue(RunWatchWorker.giveUp(RuntimeException("io"), RunWatchWorker.MAX_FAILURES))
        assertEquals(20L, RunWatchWorker.backoffSeconds(0))
        assertEquals(40L, RunWatchWorker.backoffSeconds(1))
        assertEquals(300L, RunWatchWorker.backoffSeconds(9))
    }
}
