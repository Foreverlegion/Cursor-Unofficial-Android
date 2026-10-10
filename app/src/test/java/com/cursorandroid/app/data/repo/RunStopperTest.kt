package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.ui.thread.stopMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RunStopperTest {
    private val cancelled = mutableListOf<String>()
    private var listed = 0
    private var runs: List<Run> = emptyList()
    private var cancelError: ((String) -> Throwable?) = { null }
    private var listError: Throwable? = null

    private val stopper = RunStopper(
        cancel = { _, runId ->
            cancelled += runId
            cancelError(runId)?.let { throw it }
        },
        listRuns = {
            listed++
            listError?.let { throw it }
            runs
        },
    )

    private fun stop(known: String? = null, latest: String? = null) =
        runBlocking { stopper.stop("a1", known, latest) }

    @Test
    fun missingRunIdIsLookedUpFromTheRunListAndNewestActiveIsCancelled() {
        runs = listOf(
            Run("old", status = "FINISHED", createdAt = "2026-10-01T00:00:00Z"),
            Run("mid", status = "RUNNING", createdAt = "2026-10-02T00:00:00Z"),
            Run("new", status = "CREATING", createdAt = "2026-10-03T00:00:00Z"),
        )
        assertEquals(StopOutcome.Stopped, stop())
        assertEquals(listOf("new"), cancelled)
        assertEquals(1, listed)
    }

    @Test
    fun missingRunIdWithNothingActiveReportsAlreadyFinishedWithoutCancelling() {
        runs = listOf(Run("r1", status = "FINISHED"))
        assertEquals(StopOutcome.AlreadyFinished, stop())
        assertEquals(emptyList<String>(), cancelled)
    }

    @Test
    fun blankIdsCountAsMissing() {
        runs = listOf(Run("r9", status = "RUNNING"))
        assertEquals(StopOutcome.Stopped, stop(known = " ", latest = ""))
        assertEquals(listOf("r9"), cancelled)
    }

    @Test
    fun runListFailureIsReportedNotSwallowed() {
        listError = ApiException(500, "boom")
        val out = stop() as StopOutcome.Failed
        assertEquals("HTTP 500 boom", out.reason)
        assertEquals(emptyList<String>(), cancelled)
    }

    @Test
    fun knownRunIsCancelledWithoutListing() {
        assertEquals(StopOutcome.Stopped, stop(known = "r1"))
        assertEquals(listOf("r1"), cancelled)
        assertEquals(0, listed)
    }

    @Test
    fun latestRunIdIsUsedWhenTheHeldRunIsMissing() {
        assertEquals(StopOutcome.Stopped, stop(known = null, latest = "r7"))
        assertEquals(listOf("r7"), cancelled)
    }

    @Test
    fun notCancellableMeansAlreadyFinished() {
        cancelError = { ApiException(409, "run_not_cancellable", "run_not_cancellable") }
        assertEquals(StopOutcome.AlreadyFinished, stop(known = "r1"))
    }

    @Test
    fun staleHeldRunFallsThroughToTheNewerActiveRun() {
        runs = listOf(
            Run("r1", status = "FINISHED", createdAt = "2026-10-01T00:00:00Z"),
            Run("r2", status = "RUNNING", createdAt = "2026-10-02T00:00:00Z"),
        )
        cancelError = { id -> if (id == "r1") ApiException(409, "run_not_cancellable", "run_not_cancellable") else null }
        assertEquals(StopOutcome.Stopped, stop(known = "r1"))
        assertEquals(listOf("r1", "r2"), cancelled)
    }

    @Test
    fun otherErrorsCarryTheReason() {
        cancelError = { ApiException(403, "forbidden") }
        assertEquals(StopOutcome.Failed("HTTP 403 forbidden"), stop(known = "r1"))
        cancelError = { java.io.IOException("timeout") }
        assertEquals(StopOutcome.Failed("timeout"), stop(known = "r1"))
    }

    @Test
    fun messagesMatchEveryOutcome() {
        assertEquals("Stopped", stopMessage(StopOutcome.Stopped))
        assertEquals("Run already finished", stopMessage(StopOutcome.AlreadyFinished))
        assertEquals("Couldn't stop: nope", stopMessage(StopOutcome.Failed("nope")))
    }
}
