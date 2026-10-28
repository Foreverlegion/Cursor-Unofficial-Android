package com.cursorandroid.app.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRunShadePlanTest {
    private fun row(
        agentId: String,
        lastActiveMs: Long,
        title: String = agentId,
    ) = RunningAgentShadeRow(
        agentId = agentId,
        runId = "run-$agentId",
        title = title,
        body = "Running",
        shortCriticalText = "Running",
        lastActiveMs = lastActiveMs,
    )

    @Test
    fun fourAgentsWithCapTwoPromotesTwoAndGroupsTwoWithSummary() {
        val running = listOf(
            row("a", 4L),
            row("b", 3L),
            row("c", 2L),
            row("d", 1L),
        )
        val plan = planLiveRunShade(
            running = running,
            maxPromoted = 2,
            liveStatusEnabled = true,
            promotionAllowed = true,
        )
        assertEquals(listOf("a", "b"), plan.promotedAgentIds)
        assertEquals(listOf("c", "d"), plan.groupedRows.map { it.agentId })
        assertEquals(2, plan.summaryLineCount)
        assertTrue(plan.showsSummary)
    }

    @Test
    fun summaryHiddenWhenNoGroupedAgents() {
        val plan = planLiveRunShade(
            running = listOf(row("solo", 1L)),
            maxPromoted = 3,
            liveStatusEnabled = true,
            promotionAllowed = true,
        )
        assertEquals(listOf("solo"), plan.promotedAgentIds)
        assertTrue(plan.groupedRows.isEmpty())
        assertEquals(0, plan.summaryLineCount)
        assertFalse(plan.showsSummary)
    }

    @Test
    fun emptyRunningClearsSummary() {
        val plan = planLiveRunShade(
            running = emptyList(),
            maxPromoted = 3,
            liveStatusEnabled = true,
            promotionAllowed = true,
        )
        assertEquals(0, plan.summaryLineCount)
        assertFalse(plan.showsSummary)
    }

    @Test
    fun liveDisabledGroupsAllWithoutPromotion() {
        val running = listOf(row("a", 2L), row("b", 1L))
        val plan = planLiveRunShade(
            running = running,
            maxPromoted = 2,
            liveStatusEnabled = false,
            promotionAllowed = true,
        )
        assertTrue(plan.promotedAgentIds.isEmpty())
        assertEquals(2, plan.groupedRows.size)
        assertEquals(2, plan.summaryLineCount)
    }

    @Test
    fun clampMaxLivePromotedBounds() {
        assertEquals(1, clampMaxLivePromoted(0))
        assertEquals(15, clampMaxLivePromoted(99))
        assertEquals(2, clampMaxLivePromoted(3))
    }
}
