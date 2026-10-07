package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.repo.TranscriptLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkBarTest {
    @Test
    fun idleAgentDoesNotKeepTheWorkBarUp() {
        val user = TranscriptLine("user-r1", "user", "hi", "r1")
        val assistant = TranscriptLine("assistant-r1", "assistant", "done", "r1")
        assertFalse(
            showWorkBar(
                lines = listOf(user, assistant),
                receiving = false,
                busy = false,
                agentStatus = "IDLE",
                runStatus = "FINISHED",
            ),
        )
    }

    @Test
    fun liveRunKeepsTheWorkBarUpAfterAPartialReply() {
        val user = TranscriptLine("user-r1", "user", "hi", "r1")
        val assistant = TranscriptLine("assistant-r1", "assistant", "working on it", "r1")
        assertTrue(
            showWorkBar(
                lines = listOf(user, assistant),
                receiving = false,
                busy = false,
                agentStatus = "ACTIVE",
                runStatus = "RUNNING",
            ),
        )
    }

    @Test
    fun workBarNamesTheLiveTool() {
        assertEquals(
            "Agent working · read_file",
            workActivityLine(false, "RUNNING", "RUNNING", "cloud", "read_file"),
        )
        assertEquals(
            "Agent working · starting",
            workActivityLine(false, "CREATING", null, "cloud", null),
        )
    }
}
