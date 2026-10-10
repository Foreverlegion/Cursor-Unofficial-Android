package com.cursorandroid.app.ui.inbox

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.GitSnap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentListLogicTest {
    private val now = InstantMillis.of("2026-10-10T12:00:00Z")

    @Test
    fun filtersRunningAttentionAndDone() {
        val running = agent("run", status = "RUNNING")
        val failed = agent("bad", status = "ERROR")
        val waiting = agent("wait", status = "FINISHED")
        val done = agent("done", status = "FINISHED")
        val all = listOf(running, failed, waiting, done)
        val approval = setOf("wait")
        assertEquals(listOf("run"), shown(all, CloudFilter.Running, approval))
        assertEquals(listOf("bad", "wait"), shown(all, CloudFilter.NeedsAttention, approval))
        assertEquals(listOf("done"), shown(all, CloudFilter.Done, approval))
        assertEquals(4, shown(all, CloudFilter.All, approval).size)
    }

    @Test
    fun hidesFinishedOlderThanTheChosenWindow() {
        val fresh = agent("fresh", status = "FINISHED", updatedAt = "2026-10-10T01:00:00Z")
        val old = agent("old", status = "FINISHED", updatedAt = "2026-10-06T01:00:00Z")
        val stillRunning = agent("live", status = "RUNNING", updatedAt = "2026-10-01T01:00:00Z")
        val errored = agent("bad", status = "ERROR", updatedAt = "2026-10-01T01:00:00Z")
        val arranged = arrange(
            listOf(fresh, old, stillRunning, errored),
            hideDays = 3,
        )
        assertEquals(listOf("fresh", "live", "bad"), (arranged.rest).map { it.id })
        assertEquals(1, arranged.hiddenFinished)
        val revealed = arrange(listOf(fresh, old, stillRunning, errored), hideDays = 3, reveal = true)
        assertTrue(revealed.rest.map { it.id }.contains("old"))
    }

    @Test
    fun groupsByRepoNewestActivityFirst() {
        val git = mapOf(
            "a" to GitSnap("a", repoUrl = "https://github.com/acme/app"),
            "b" to GitSnap("b", repoUrl = "https://github.com/acme/app"),
            "c" to GitSnap("c", repoUrl = "https://github.com/acme/docs"),
        )
        val arranged = arrange(
            listOf(
                agent("a", updatedAt = "2026-10-01T00:00:00Z"),
                agent("b", updatedAt = "2026-10-08T00:00:00Z"),
                agent("c", updatedAt = "2026-10-10T00:00:00Z"),
            ),
            git = git,
            group = true,
        )
        assertEquals(listOf("docs", "app"), arranged.groups.map { it.label })
        assertEquals(listOf("c"), arranged.groups[0].agents.map { it.id })
        assertEquals(listOf("b", "a"), arranged.groups[1].agents.map { it.id })
        assertEquals(0, arranged.groups[0].running)
    }

    @Test
    fun runningCountShowsOnTheRepoHeader() {
        val git = mapOf("a" to GitSnap("a", repoUrl = "https://github.com/acme/app"))
        val arranged = arrange(
            listOf(agent("a", status = "RUNNING", updatedAt = "2026-10-10T00:00:00Z")),
            git = git,
            group = true,
        )
        assertEquals(1, arranged.groups.single().running)
        assertEquals("app", arranged.groups.single().label)
    }

    @Test
    fun pinsStayAboveRepoGroups() {
        val git = mapOf(
            "old" to GitSnap("old", repoUrl = "https://github.com/acme/app"),
            "new" to GitSnap("new", repoUrl = "https://github.com/acme/docs"),
        )
        val arranged = arrange(
            listOf(
                agent("old", updatedAt = "2026-10-01T00:00:00Z"),
                agent("new", updatedAt = "2026-10-10T00:00:00Z"),
            ),
            git = git,
            group = true,
            pinnedAt = mapOf("old" to 50L),
        )
        assertEquals(listOf("old"), arranged.pinned.map { it.id })
        assertEquals(listOf("new"), arranged.groups.single().agents.map { it.id })
    }

    @Test
    fun missingRepoSharesOneGroup() {
        val arranged = arrange(
            listOf(agent("a", updatedAt = "2026-10-02T00:00:00Z"), agent("b", updatedAt = "2026-10-03T00:00:00Z")),
            group = true,
        )
        assertEquals("No repo", arranged.groups.single().label)
        assertEquals(listOf("b", "a"), arranged.groups.single().agents.map { it.id })
    }

    private fun shown(agents: List<AgentSummary>, filter: CloudFilter, approval: Set<String>): List<String> {
        return arrange(agents, filter = filter, approval = approval).rest.map { it.id }
    }

    private fun arrange(
        agents: List<AgentSummary>,
        git: Map<String, GitSnap> = emptyMap(),
        filter: CloudFilter = CloudFilter.All,
        approval: Set<String> = emptySet(),
        hideDays: Int = 0,
        reveal: Boolean = false,
        group: Boolean = false,
        pinnedAt: Map<String, Long> = emptyMap(),
    ): CloudArrangement {
        return arrangeCloudAgents(
            agents = agents,
            git = git,
            pinnedAt = pinnedAt,
            favoriteIds = emptySet(),
            approvalIds = approval,
            filter = filter,
            hideFinishedDays = hideDays,
            revealFinished = reveal,
            groupByRepo = group,
            nowMillis = now,
        )
    }

    private fun agent(
        id: String,
        status: String = "FINISHED",
        updatedAt: String = "2026-10-10T00:00:00Z",
    ): AgentSummary = AgentSummary(id = id, name = id, status = status, updatedAt = updatedAt)
}

private object InstantMillis {
    fun of(iso: String): Long = java.time.Instant.parse(iso).toEpochMilli()
}
