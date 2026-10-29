package com.cursorandroid.app.data.notify

import org.junit.Assert.assertEquals
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
    fun liveNotifyIdIsStable() {
        assertEquals(liveNotifyId("run-1"), liveNotifyId("run-1"))
        assert(liveNotifyId("run-1") != liveNotifyId("run-2"))
    }
}
