package com.cursorandroid.app.data.notify

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.repo.TranscriptLine
import com.cursorandroid.app.ui.status.RunIndicator
import com.cursorandroid.app.ui.status.runIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RunSettleWiringTest {
    private fun container() = AppContainer(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun finishedNotificationFlipsSharedAgentState() {
        val c = container()
        c.catalog.saveAgents(
            listOf(
                AgentSummary(id = "a1", name = "Widget", status = "ACTIVE", latestRunId = "r1"),
                AgentSummary(id = "a2", name = "Stocks", status = "ACTIVE", latestRunId = "r7"),
            ),
        )
        c.conversations.save(
            "a1",
            listOf(TranscriptLine("user-r1", "user", "go", "r1")),
            runId = "r1",
            runStatus = "RUNNING",
            immediate = true,
        )
        assertEquals("RUNNING", c.conversations.liveStatus("a1"))

        c.notifier.notifyIfNeeded("a1", "Widget", "r1", "FINISHED", "shipped")

        assertEquals("FINISHED", c.runSettle.current("a1")?.status)
        val cards = c.catalog.agents().associateBy { it.id }
        assertEquals(RunIndicator.Done, runIndicator(cards.getValue("a1").status))
        assertEquals(RunIndicator.Running, runIndicator(cards.getValue("a2").status))
        assertNull(c.conversations.liveStatus("a1"))
        assertEquals("FINISHED", c.conversations.loadSnap("a1").runStatus)
    }

    @Test
    fun runningStatusDoesNotPublish() {
        val c = container()
        c.notifier.notifyIfNeeded("a1", "Widget", "r1", "RUNNING", null)
        assertNull(c.runSettle.current("a1"))
    }
}
