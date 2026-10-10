package com.cursorandroid.app.ui.inbox

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.GitSnap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepoFallbackTest {
    private val repo = "https://github.com/Foreverlegion/Cursor-Unofficial-Android"

    @Test
    fun runGitRepoWins() {
        val git = mapOf("a" to GitSnap("a", branch = "main", repoUrl = repo))
        val out = withRepoFallbacks(git, mapOf("a" to "https://github.com/other/repo"), mapOf("a" to "https://x/y"))
        assertEquals(repo, out["a"]?.repoUrl)
    }

    @Test
    fun getAgentRepoFillsAgentWithoutRunGit() {
        val out = withRepoFallbacks(emptyMap(), mapOf("new" to repo), emptyMap())
        assertEquals(repo, out["new"]?.repoUrl)
    }

    @Test
    fun snapWithBranchButNoRepoGetsRepoAndKeepsBranch() {
        val git = mapOf("a" to GitSnap("a", branch = "cursor/fix", repoUrl = null))
        val out = withRepoFallbacks(git, mapOf("a" to repo), emptyMap())
        assertEquals(repo, out["a"]?.repoUrl)
        assertEquals("cursor/fix", out["a"]?.branch)
    }

    @Test
    fun chatRepoIsLastFallbackAndBlankMarkersAreIgnored() {
        val out = withRepoFallbacks(emptyMap(), mapOf("a" to "", "b" to ""), mapOf("a" to repo, "c" to null))
        assertEquals(repo, out["a"]?.repoUrl)
        assertNull(out["b"])
        assertNull(out["c"])
    }

    @Test
    fun newerAgentLandsInItsRepoGroupInsteadOfNoRepo() {
        val agents = listOf(
            AgentSummary(id = "opus", name = "Opus audit", status = "RUNNING", updatedAt = "2026-10-10T09:00:00Z"),
            AgentSummary(id = "old", name = "Old", status = "FINISHED", updatedAt = "2026-10-09T09:00:00Z"),
        )
        val git = withRepoFallbacks(emptyMap(), mapOf("opus" to repo), emptyMap())
        val groups = repoGroups(agents, git, emptySet())
        assertEquals(listOf("Cursor-Unofficial-Android", "No repo"), groups.map { it.label })
        assertEquals(listOf("opus"), groups.first().agents.map { it.id })
    }
}
