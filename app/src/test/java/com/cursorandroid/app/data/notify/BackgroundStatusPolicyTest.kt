package com.cursorandroid.app.data.notify

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.repo.gitSnapTargets
import com.cursorandroid.app.data.repo.hydrateTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundStatusPolicyTest {
    private fun agent(id: String, status: String?, run: String? = "r-$id", archived: Boolean? = null) =
        AgentSummary(id = id, status = status, latestRunId = run, archived = archived)

    private val list = listOf(
        agent("done", "FINISHED"),
        agent("failed", "ERROR"),
        agent("running", "RUNNING"),
        agent("creating", "CREATING"),
        agent("blank", null),
        agent("old", "RUNNING", archived = true),
    )

    @Test
    fun finishedAgentsGetNoPerAgentStatusCall() {
        val ids = hydrateTargets(list, limit = 15).map { it.id }
        assertEquals(listOf("running", "creating", "blank"), ids)
    }

    @Test
    fun hydrateRespectsTheLimitAndAnExplicitSet() {
        assertEquals(listOf("running"), hydrateTargets(list, limit = 3).map { it.id })
        assertEquals(listOf("done"), hydrateTargets(list, limit = 15, only = setOf("done")).map { it.id })
    }

    @Test
    fun gitSnapsAreOnlyRefetchedForLiveOrNeverFetchedAgents() {
        val ids = gitSnapTargets(list, known = setOf("done", "failed", "running")).map { it.id }
        assertEquals(listOf("running", "creating", "blank"), ids)
    }

    @Test
    fun theSweepOnlyChecksLiveAgentsAndRunsItSawLive() {
        val seen = mapOf("r-done" to "RUNNING", "r-failed" to "FINISHED")
        val ids = sweepTargets(list, seenStatus = { seen[it] }).map { it.id }
        assertEquals(listOf("done", "running", "creating"), ids)
    }

    @Test
    fun theSweepNeverChecksMoreThanItsLimit() {
        val many = (1..100).map { agent("a$it", "RUNNING") }
        assertEquals(SWEEP_LIMIT, sweepTargets(many, seenStatus = { null }).size)
    }

    @Test
    fun runWatchSpacingGrowsAfterFailuresAndHonoursRetryAfter() {
        assertEquals(RunWatchBackoff.NORMAL_MS, RunWatchBackoff.delayMs(0, null))
        val boom = ApiException(500, "boom")
        assertEquals(40_000L, RunWatchBackoff.delayMs(1, boom))
        assertEquals(160_000L, RunWatchBackoff.delayMs(3, boom))
        assertEquals(RunWatchBackoff.MAX_MS, RunWatchBackoff.delayMs(9, boom))
        val limited = ApiException(429, "slow down", retryAfterMs = 120_000)
        assertEquals(120_000L, RunWatchBackoff.delayMs(1, limited))
        assertTrue(RunWatchBackoff.delayMs(1, ApiException(429, "slow down")) >= 40_000L)
    }

    @Test
    fun aMissingRunEndsTheWatch() {
        assertTrue(RunWatchBackoff.gone(ApiException(404, "not found")))
        assertFalse(RunWatchBackoff.gone(ApiException(429, "slow")))
        assertFalse(RunWatchBackoff.gone(IllegalStateException()))
    }
}
