package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.TokenUsage
import com.cursorandroid.app.data.api.WorkerPool
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CancellationException

class AccountStatsTest {
    @Test
    fun freshLoadReplacesAnEmptyCache() {
        val loaded = pickFreshList(Result.success(listOf("a", "b")), emptyList())
        assertEquals(listOf("a", "b"), loaded)
    }

    @Test
    fun failedLoadKeepsACacheThatAlreadyHasRows() {
        val loaded = pickFreshList(
            Result.failure(IllegalStateException("down")),
            listOf("cached"),
        )
        assertEquals(listOf("cached"), loaded)
    }

    @Test
    fun failedLoadWithAnEmptyCacheDoesNotBecomeZero() {
        try {
            pickFreshList(Result.failure(IllegalStateException("down")), emptyList<String>())
            fail("missing data must not look like an empty catalog")
        } catch (e: IllegalStateException) {
            assertEquals("down", e.message)
        }
    }

    @Test
    fun cancellationIsNotServedFromCache() {
        try {
            pickFreshList(
                Result.failure(CancellationException("gone")),
                listOf("cached"),
            )
            fail("a cancelled load must not fall back to cache")
        } catch (e: CancellationException) {
            assertEquals("gone", e.message)
        }
    }

    @Test
    fun successfulEmptyLoadIsEmpty() {
        val loaded = pickFreshList(Result.success(emptyList<String>()), listOf("stale"))
        assertEquals(emptyList<String>(), loaded)
    }

    @Test
    fun countsRunningMachinesPoolsAndReposFromTheLoadedLists() {
        val counts = accountCounts(
            agents = listOf(
                AgentSummary(id = "live", status = "RUNNING"),
                AgentSummary(id = "done", status = "FINISHED"),
                AgentSummary(id = "wait", status = "FINISHED"),
            ),
            computers = listOf(
                Computer(name = "desk", online = true),
                Computer(name = "old", online = false),
            ),
            pools = listOf(WorkerPool(poolName = "build", connectedWorkerCount = 2)),
            repoCount = 4,
        )
        assertEquals(3, counts.agentCount)
        assertEquals(1, counts.runningCount)
        assertEquals(1, counts.computersOnline)
        assertEquals(2, counts.computerCount)
        assertEquals(1, counts.poolCount)
        assertEquals(2, counts.poolsConnected)
        assertEquals(4, counts.repoCount)
    }

    @Test
    fun usageTotalsDoNotDependOnAgentCounts() {
        val sample = combineTokenUsage(
            listOf(
                AgentSummary(id = "a", name = "Alpha") to TokenUsage(totalTokens = 10),
                AgentSummary(id = "b", name = "Beta") to TokenUsage(inputTokens = 4, outputTokens = 1),
            ),
        )
        assertEquals(2, sample.sampledAgents)
        assertEquals(15L, sample.usage.totalTokens)
        assertEquals(5L, sample.top.first { it.id == "b" }.tokens)
    }
}
