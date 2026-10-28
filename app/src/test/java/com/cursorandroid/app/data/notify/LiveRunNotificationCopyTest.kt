package com.cursorandroid.app.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LiveRunNotificationCopyTest {
    @Test
    fun runningRunMapsToOngoingCopy() {
        val content = liveRunNotificationContent(
            chatTitle = "Fleet widget",
            agentStatus = "ACTIVE",
            runStatus = "RUNNING",
        )
        requireNotNull(content)
        assertEquals("Fleet widget", content.title)
        assertEquals("Active · Running", content.body)
        assertEquals("Running", content.shortCriticalText)
    }

    @Test
    fun toolHintShowsInBodyAndChip() {
        val hint = "write\nsrc/main.kt"
        val content = liveRunNotificationContent(
            chatTitle = "Fix",
            agentStatus = "ACTIVE",
            runStatus = "RUNNING",
            activityHint = hint,
        )
        requireNotNull(content)
        assertEquals(hint, content.body)
        assertEquals("write", content.shortCriticalText)
    }

    @Test
    fun approvalMapsToApproveChip() {
        val content = liveRunNotificationContent(
            chatTitle = "Deploy",
            agentStatus = "ACTIVE",
            runStatus = "RUNNING",
            approvalPending = true,
        )
        requireNotNull(content)
        assertEquals("Waiting for approval", content.body)
        assertEquals("Approve", content.shortCriticalText)
    }

    @Test
    fun finishedRunClearsLiveUpdate() {
        assertNull(
            liveRunNotificationContent(
                chatTitle = "Done",
                agentStatus = "IDLE",
                runStatus = "FINISHED",
            ),
        )
    }

    @Test
    fun errorRunClearsLiveUpdate() {
        assertNull(
            liveRunNotificationContent(
                chatTitle = "Broke",
                agentStatus = "ACTIVE",
                runStatus = "ERROR",
            ),
        )
    }

    @Test
    fun liveNotificationSlotIsStablePerAgent() {
        val first = liveNotificationSlot("agent-1")
        val second = liveNotificationSlot("agent-1")
        assertEquals(first, second)
        assertEquals(LIVE_RUN_NOTIFICATION_TAG, first.tag)
        assertNotEquals(first, liveNotificationSlot("agent-2"))
    }

    @Test
    fun workerAndInboxPathsShareOneSlotPerAgent() {
        val agentId = "bc-agent-9"
        val fromWorker = liveNotificationSlot(agentId)
        val fromInboxSync = liveNotificationSlot(agentId)
        assertEquals(fromWorker.tag, fromInboxSync.tag)
        assertEquals(fromWorker.id, fromInboxSync.id)
        assertNotEquals(
            fromWorker.id,
            legacyLiveNotificationSlot("run-old").id,
        )
    }

    @Test
    fun elapsedTimeAppendsToBody() {
        val content = liveRunNotificationContent(
            chatTitle = "Job",
            agentStatus = "ACTIVE",
            runStatus = "RUNNING",
            startedAtMs = 5_000L,
            nowMs = 125_000L,
        )
        requireNotNull(content)
        assertEquals("Active · Running · 2m", content.body)
    }

    @Test
    fun liveNotifyIdLegacyAliasMatchesRunKeyedSlot() {
        assertEquals(legacyLiveNotificationSlot("run-1").id, liveNotifyId("run-1"))
    }

    @Test
    fun creatingThenRunningKeepsSameNotificationSlot() {
        val agentId = "agent-status-flow"
        val creating = liveNotificationSlot(agentId)
        val running = liveNotificationSlot(agentId)
        assertEquals(creating.id, running.id)
        assertEquals(creating.tag, running.tag)
        assertNotNull(
            liveRunNotificationContent(
                chatTitle = "Ship",
                agentStatus = "ACTIVE",
                runStatus = "CREATING",
            ),
        )
        assertNotNull(
            liveRunNotificationContent(
                chatTitle = "Ship",
                agentStatus = "ACTIVE",
                runStatus = "RUNNING",
            ),
        )
    }
}
