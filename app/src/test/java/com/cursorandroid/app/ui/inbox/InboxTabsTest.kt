package com.cursorandroid.app.ui.inbox

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxTabsTest {
    @Test
    fun allTabsDefault() {
        assertEquals(
            listOf(InboxTab.Agents, InboxTab.Envs, InboxTab.Remote),
            InboxTabs.visible(showEnvs = true, showRemote = true),
        )
    }

    @Test
    fun hideEnvsAndRemoteLeavesAgents() {
        assertEquals(
            listOf(InboxTab.Agents),
            InboxTabs.visible(showEnvs = false, showRemote = false),
        )
    }

    @Test
    fun remoteTitle() {
        assertEquals("Remote", InboxTab.Remote.title)
        assertEquals("ENVs", InboxTab.Envs.title)
    }

    @Test
    fun emptyCopyTellsHowToLeaveHidden() {
        assertEquals(
            "No hidden chats. Turn off Hidden to see the rest.",
            inboxEmptyCopy(showHidden = true, showArchived = false, workingOnly = false),
        )
        assertEquals(
            "No archived chats. Turn off Archived to see the rest.",
            inboxEmptyCopy(showHidden = false, showArchived = true, workingOnly = false),
        )
        assertEquals(
            "No hidden or archived chats. Turn off Hidden or Archived to see the rest.",
            inboxEmptyCopy(showHidden = true, showArchived = true, workingOnly = false),
        )
        assertEquals(
            "Nothing working. Turn off Working to see the rest.",
            inboxEmptyCopy(showHidden = false, showArchived = false, workingOnly = true),
        )
        assertEquals(
            "No chats match this search.",
            inboxEmptyCopy(showHidden = false, showArchived = false, workingOnly = false, query = "zenbook"),
        )
        assertEquals(
            "No agents yet. Start one on a cloud VM or a named machine.",
            inboxEmptyCopy(showHidden = false, showArchived = false, workingOnly = false),
        )
    }
}
