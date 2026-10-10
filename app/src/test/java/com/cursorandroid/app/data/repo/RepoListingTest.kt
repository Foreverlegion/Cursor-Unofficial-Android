package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.RepositoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoListingTest {
    @Test
    fun gateAllowsFirstCallThenWaitsAMinute() {
        assertTrue(RepoRateGate.mayCall(0L, 1_000L))
        assertFalse(RepoRateGate.mayCall(10_000L, 30_000L))
        assertTrue(RepoRateGate.mayCall(10_000L, 10_000L + RepoRateGate.MIN_GAP_MS))
        assertTrue(RepoRateGate.mayCall(50_000L, 1_000L))
    }

    @Test
    fun mergeKeepsCachedOtherHostsAndDropsStaleGithub() {
        val found = listOf(RepositoryItem("https://github.com/acme/b"), RepositoryItem("https://github.com/acme/A.git"))
        val cached = listOf(
            RepositoryItem("https://github.com/acme/gone"),
            RepositoryItem("https://github.com/acme/a"),
            RepositoryItem("https://gitlab.com/acme/c"),
        )
        val merged = mergeListedRepos(found, cached).map { it.url }
        assertEquals(
            listOf("https://github.com/acme/A.git", "https://github.com/acme/b", "https://gitlab.com/acme/c"),
            merged,
        )
    }

    @Test
    fun mergeFallsBackToCacheWhenNothingListed() {
        val cached = listOf(RepositoryItem("https://github.com/acme/a"))
        assertEquals(cached, mergeListedRepos(emptyList(), cached))
    }
}
