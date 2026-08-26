package com.cursorandroid.app.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalCopyTest {

    @Test
    fun folderAskTellsUserToApproveOnPc() {
        val ask = ApprovalCopy.ask(
            name = "create_folder",
            args = """{"path":"src/generated"}""",
            status = "pending",
        )
        assertEquals("folder", ask?.kind)
        assertEquals("create a folder `src/generated`", ask?.action)
        assertEquals(
            "Cursor is requesting approval to create a folder src/generated. Approve this on your PC.",
            ask?.body,
        )
    }

    @Test
    fun shellAndWriteCovered() {
        assertEquals(
            "Cursor is requesting approval to run npm test. Approve this on your PC.",
            ApprovalCopy.ask("run_terminal_cmd", """{"command":"npm test"}""", "pending")?.body,
        )
        assertEquals(
            "Cursor is requesting approval to write app/src/Main.kt. Approve this on your PC.",
            ApprovalCopy.ask("Write", """{"path":"app/src/Main.kt"}""", "needs_approval")?.body,
        )
    }

    @Test
    fun arrayArgsDoNotCrash() {
        val ask = ApprovalCopy.ask(
            name = "Write",
            args = """{"path":["src/a.kt","src/b.kt"]}""",
            status = "awaiting-approval",
        )
        assertEquals("write", ask?.kind)
        assertEquals("write `src/a.kt, src/b.kt`", ask?.action)
    }

    @Test
    fun onlyPendingStatusIsApproval() {
        assertFalse(ApprovalCopy.isApproval("read_file", "started"))
        assertFalse(ApprovalCopy.isApproval("read_file", "running"))
        assertFalse(ApprovalCopy.isApproval("mkdir", "started"))
        assertFalse(ApprovalCopy.isApproval("Write", "running"))
        assertFalse(ApprovalCopy.isApproval("run_terminal_cmd", "completed"))
        assertFalse(ApprovalCopy.isApproval("mcp", null))
        assertFalse(ApprovalCopy.isApproval("Write", ""))
        assertTrue(ApprovalCopy.isApproval("read_file", "pending"))
        assertTrue(ApprovalCopy.isApproval("mkdir", "waiting"))
        assertTrue(ApprovalCopy.isApproval("Write", "needs-approval"))
        assertNull(ApprovalCopy.ask("Write", """{"path":"a.kt"}""", "running"))
    }
}
