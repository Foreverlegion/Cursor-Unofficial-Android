package com.cursorandroid.app.ui.thread

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.cursorandroid.app.data.repo.TranscriptLine
import com.cursorandroid.app.ui.status.RunIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class ThreadRunStateTest {
    @get:Rule
    val compose = createComposeRule()

    private val answered = listOf(
        TranscriptLine("user-r1", "user", "hi", "r1"),
        TranscriptLine("assistant-r1", "assistant", "done", "r1"),
    )

    private fun state(
        agent: String? = "IDLE",
        run: String? = "FINISHED",
        busy: Boolean = false,
        streaming: Boolean = false,
        receiving: Boolean = false,
        approval: Boolean = false,
        lines: List<TranscriptLine> = answered,
    ) = threadRunState(lines, agent, run, busy, streaming, receiving, approval)

    @Test
    fun finishedChatIsIdleAndDone() {
        val s = state()
        assertFalse(s.active)
        assertEquals(RunIndicator.Done, s.indicator)
    }

    @Test
    fun unansweredLastMessageDoesNotKeepAFinishedChatBusy() {
        val lines = answered + TranscriptLine("user-local-1", "user", "lost", null)
        val s = state(lines = lines)
        assertFalse(s.active)
        assertEquals(RunIndicator.Done, s.indicator)
    }

    @Test
    fun inFlightSendFlipsHeaderToRunningBeforeTheRunExists() {
        val s = state(busy = true)
        assertTrue(s.active)
        assertEquals(RunIndicator.Running, s.indicator)
    }

    @Test
    fun openStreamOrReceivingTextCountsAsActive() {
        assertEquals(RunIndicator.Running, state(streaming = true).indicator)
        assertEquals(RunIndicator.Running, state(receiving = true).indicator)
    }

    @Test
    fun creatingAndRunningStatusesAreActive() {
        assertTrue(state(agent = "CREATING", run = null).active)
        assertTrue(state(agent = "IDLE", run = "RUNNING").active)
        assertEquals(RunIndicator.Running, state(agent = "FINISHED", run = "RUNNING").indicator)
    }

    @Test
    fun approvalKeepsItsIndicatorAndStaysActive() {
        val s = state(approval = true)
        assertTrue(s.active)
        assertEquals(RunIndicator.NeedsApproval, s.indicator)
    }

    @Test
    fun failedStatusStaysFailedWhenNothingIsActive() {
        assertEquals(RunIndicator.Failed, state(agent = "IDLE", run = "ERROR").indicator)
    }

    @Test
    fun unknownStatusFallsBackToAnUnansweredMessage() {
        val lines = listOf(TranscriptLine("user-r1", "user", "hi", "r1"))
        assertTrue(state(agent = null, run = null, lines = lines).active)
        assertFalse(state(agent = null, run = null).active)
    }

    @Test
    fun workingBarStopButtonCallsTheKillPath() {
        var stopped = 0
        compose.setContent { WorkingBar("Agent working", onStop = { stopped++ }) }
        compose.onNodeWithTag("stop-run").performClick()
        assertEquals(1, stopped)
    }

    @Test
    fun workingBarHasNoStopButtonWithoutAHandler() {
        compose.setContent { WorkingBar("Agent working") }
        compose.onNodeWithTag("stop-run").assertDoesNotExist()
    }
}

