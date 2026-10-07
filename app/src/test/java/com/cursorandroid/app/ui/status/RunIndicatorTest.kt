package com.cursorandroid.app.ui.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunIndicatorTest {
    @Test
    fun mapsLiveTerminalAndApprovalStates() {
        assertEquals(RunIndicator.Done, runIndicator("FINISHED"))
        assertEquals(RunIndicator.Done, runIndicator("IDLE"))
        assertEquals(RunIndicator.Done, runIndicator("ARCHIVED"))
        assertEquals(RunIndicator.Done, runIndicator(null))
        assertEquals(RunIndicator.Running, runIndicator("RUNNING"))
        assertEquals(RunIndicator.Running, runIndicator("CREATING"))
        assertEquals(RunIndicator.Running, runIndicator("ACTIVE", "RUNNING"))
        assertEquals(RunIndicator.Running, runIndicator("ERROR", "RUNNING"))
        assertEquals(RunIndicator.Failed, runIndicator("ERROR"))
        assertEquals(RunIndicator.Failed, runIndicator("EXPIRED"))
        assertEquals(RunIndicator.Failed, runIndicator("CANCELLED"))
        assertEquals(RunIndicator.NeedsApproval, runIndicator("NEEDS_APPROVAL"))
        assertEquals(RunIndicator.NeedsApproval, runIndicator("awaiting_approval"))
        assertEquals(RunIndicator.NeedsApproval, runIndicator("FINISHED", approvalPending = true))
        assertEquals(RunIndicator.NeedsApproval, runIndicator("RUNNING", approvalPending = true))
    }

    @Test
    fun subtitleUsesRepoAndEnv() {
        assertEquals(
            "acme/app · Cloud",
            agentCardSubtitle("cloud", "Cloud", "https://github.com/acme/app.git"),
        )
        assertEquals("local-pool · Pool", agentCardSubtitle("pool", "local-pool", null))
        assertEquals("Remote", agentCardSubtitle("machine", null, null))
        assertEquals("cursor-android · Running", threadSubtitle("cursor-android", RunIndicator.Running))
        assertEquals("Done", threadSubtitle(null, RunIndicator.Done))
    }

    @Test
    fun relativeAgeBuckets() {
        val now = 1_700_000_000_000L
        assertEquals("now", relativeAge("2023-11-14T22:13:20Z", now))
        assertEquals("2m ago", relativeAge(java.time.Instant.ofEpochMilli(now - 120_000).toString(), now))
        assertEquals("3h", relativeAge(java.time.Instant.ofEpochMilli(now - 3 * 3_600_000L).toString(), now))
        assertEquals("", relativeAge("not-a-date", now))
        assertNull(shortRepo("  "))
    }

    @Test
    fun toolRowKeepsNameAndPath() {
        val text = toolCallText("read_file", """{"path":"app/src/main/java/com/cursorandroid/app/ui/settings/McpSettings.kt"}""")
        val (name, path) = toolCallParts(text)
        assertEquals("read_file", name)
        assertEquals("app/.../McpSettings.kt", path)
        assertEquals("shell" to null, toolCallParts("shell completed"))
    }
}
