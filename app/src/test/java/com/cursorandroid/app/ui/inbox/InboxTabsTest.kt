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
}
