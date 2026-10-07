package com.cursorandroid.app.ui.inbox

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Env
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
        assertEquals("Pool", InboxTab.Envs.title)
        assertEquals("Cloud", InboxTab.Agents.title)
        assertEquals("cloud", InboxTab.Agents.composeTarget())
        assertEquals("pool", InboxTab.Envs.composeTarget())
        assertEquals("machine", InboxTab.Remote.composeTarget())
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

    @Test
    fun tabsSplitByEnvAndFoldHiddenHomesIntoCloud() {
        val cloud = AgentSummary(id = "c", env = Env(type = "cloud"))
        val pool = AgentSummary(id = "p", env = Env(type = "pool", name = "local-pool"))
        val remote = AgentSummary(id = "r", env = Env(type = "machine", name = "zenbook"))
        val all = listOf(cloud, pool, remote)
        val tabs = InboxTabs.visible(showEnvs = true, showRemote = true)
        assertEquals(listOf(cloud), all.forInboxTab(InboxTab.Agents, tabs))
        assertEquals(listOf(pool), all.forInboxTab(InboxTab.Envs, tabs))
        assertEquals(listOf(remote), all.forInboxTab(InboxTab.Remote, tabs))
        val cloudOnly = InboxTabs.visible(showEnvs = false, showRemote = false)
        assertEquals(all, all.forInboxTab(InboxTab.Agents, cloudOnly))
    }
}
