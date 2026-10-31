package com.cursorandroid.app.data.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkOwnershipPolicyTest {
    private val grokConflict = LinkOwnershipSnapshot(
        relevant = true,
        grokInstalled = true,
        linkHandler = AgentLinkHandler.GrokBot,
        ownHostsEnabled = false,
    )

    private val needsOwn = LinkOwnershipSnapshot(
        relevant = true,
        grokInstalled = true,
        linkHandler = AgentLinkHandler.Other,
        ownHostsEnabled = false,
    )

    private val ready = LinkOwnershipSnapshot(
        relevant = true,
        grokInstalled = true,
        linkHandler = AgentLinkHandler.ThisApp,
        ownHostsEnabled = true,
    )

    @Test
    fun firstLaunchShowsGrokConflict() {
        val d = LinkOwnershipPolicy.decide(
            snap = grokConflict,
            dismissed = false,
            pendingGrok = false,
            pendingOwn = false,
            manual = false,
        )
        assertEquals(LinkOwnershipDialogKind.GrokConflict, d.dialog)
    }

    @Test
    fun dismissedHidesUntilManualOrPending() {
        val hidden = LinkOwnershipPolicy.decide(
            snap = grokConflict,
            dismissed = true,
            pendingGrok = false,
            pendingOwn = false,
            manual = false,
        )
        assertEquals(LinkOwnershipDialogKind.None, hidden.dialog)

        val manual = LinkOwnershipPolicy.decide(
            snap = grokConflict,
            dismissed = true,
            pendingGrok = false,
            pendingOwn = false,
            manual = true,
        )
        assertEquals(LinkOwnershipDialogKind.GrokConflict, manual.dialog)
    }

    @Test
    fun pendingGrokStillConflictThenEnableOwn() {
        val still = LinkOwnershipPolicy.decide(
            snap = grokConflict,
            dismissed = false,
            pendingGrok = true,
            pendingOwn = false,
            manual = false,
        )
        assertEquals(LinkOwnershipDialogKind.GrokStillConflict, still.dialog)

        val next = LinkOwnershipPolicy.decide(
            snap = needsOwn,
            dismissed = false,
            pendingGrok = true,
            pendingOwn = false,
            manual = false,
        )
        assertEquals(LinkOwnershipDialogKind.EnableOwnLinks, next.dialog)
        assertTrue(next.clearPendingGrok)
    }

    @Test
    fun pendingOwnPromptsUntilHostsEnabled() {
        val prompt = LinkOwnershipPolicy.decide(
            snap = needsOwn,
            dismissed = true,
            pendingGrok = false,
            pendingOwn = true,
            manual = false,
        )
        assertEquals(LinkOwnershipDialogKind.EnableOwnLinks, prompt.dialog)
    }

    @Test
    fun readyClearsPendingAndShowsSuccess() {
        val d = LinkOwnershipPolicy.decide(
            snap = ready,
            dismissed = false,
            pendingGrok = true,
            pendingOwn = true,
            manual = false,
        )
        assertEquals(LinkOwnershipDialogKind.None, d.dialog)
        assertTrue(d.showSuccessSnackbar)
        assertTrue(d.clearPendingGrok)
        assertTrue(d.clearPendingOwn)
    }

    @Test
    fun irrelevantSnapshotIsIdle() {
        val snap = LinkOwnershipSnapshot(
            relevant = false,
            grokInstalled = false,
            linkHandler = AgentLinkHandler.Other,
            ownHostsEnabled = true,
        )
        val d = LinkOwnershipPolicy.decide(snap, false, false, false, false)
        assertEquals(LinkOwnershipDialogKind.None, d.dialog)
        assertFalse(d.showSuccessSnackbar)
    }

    @Test
    fun settingsSummaryReflectsState() {
        assertEquals("Grok Bot opens cursor.com links", LinkOwnershipPolicy.settingsSummary(grokConflict))
        assertEquals("Turn on Supported web addresses", LinkOwnershipPolicy.settingsSummary(needsOwn))
        assertEquals("Cursor agent links open in this app", LinkOwnershipPolicy.settingsSummary(ready))
    }
}
