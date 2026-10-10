package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePolicyTest {
    private val day = CachePolicy.DAY_MS
    private val now = 100 * day

    private fun entry(key: String, kb: Long, ageDays: Long, maxDays: Long = 30, pinned: Boolean = false) =
        CacheEntry(key, kb * 1024, now - ageDays * day, maxDays * day, pinned)

    @Test
    fun dropsEntriesPastMaxAge() {
        val out = CachePolicy.evict(
            listOf(entry("old", 1, 31), entry("fresh", 1, 2), entry("gone", 1, 8, maxDays = 7)),
            budgetBytes = Long.MAX_VALUE,
            now = now,
        )
        assertEquals(setOf("old", "gone"), out)
    }

    @Test
    fun evictsLeastRecentlyUsedUntilUnderBudget() {
        val entries = listOf(entry("a", 400, 5), entry("b", 400, 1), entry("c", 400, 3))
        val out = CachePolicy.evict(entries, budgetBytes = 900 * 1024, now = now)
        assertEquals(setOf("a"), out)
        val tighter = CachePolicy.evict(entries, budgetBytes = 500 * 1024, now = now)
        assertEquals(setOf("a", "c"), tighter)
    }

    @Test
    fun pinnedEntriesSurviveAgeAndBudget() {
        val entries = listOf(entry("live", 900, 60, pinned = true), entry("x", 100, 1))
        val out = CachePolicy.evict(entries, budgetBytes = 0, now = now)
        assertEquals(setOf("x"), out)
        assertFalse("live" in out)
    }

    @Test
    fun rowCapKeepsNewest() {
        val entries = (1..10).map { entry("c$it", 1, it.toLong()) }
        val out = CachePolicy.evict(entries, Long.MAX_VALUE, now, maxEntries = 4)
        assertEquals((5..10).map { "c$it" }.toSet(), out)
    }

    @Test
    fun cacheAgeHandlesClockSkew() {
        assertTrue(CacheAge.fresh(1_000, 2_000, 5_000))
        assertFalse(CacheAge.fresh(1_000, 7_000, 5_000))
        assertFalse(CacheAge.fresh(0, 10, 5_000))
        assertFalse(CacheAge.fresh(9_000, 1_000, 5_000))
    }
}
