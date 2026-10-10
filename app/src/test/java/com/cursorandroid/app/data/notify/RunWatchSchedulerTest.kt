package com.cursorandroid.app.data.notify

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RunWatchSchedulerTest {
    private lateinit var context: Context
    private lateinit var wm: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        wm = WorkManager.getInstance(context)
        VisibleAgent.resetForTest()
    }

    private fun infos(runId: String): List<WorkInfo> =
        wm.getWorkInfosForUniqueWork(RunWatchScheduler.watchWorkName(runId)).get()

    @Test
    fun repeatedEnqueuesForTheSameRunKeepOneWatcher() {
        repeat(5) { RunWatchScheduler.enqueuePoll(context, "agent-1", "run-1", "name") }
        val all = infos("run-1")
        assertEquals(1, all.size)
        assertEquals(WorkInfo.State.ENQUEUED, all.single().state)
    }

    @Test
    fun enqueueingAgainDoesNotReplaceTheWatcherItAlreadyHas() {
        RunWatchScheduler.enqueuePoll(context, "agent-1", "run-1", "name")
        val first = infos("run-1").single().id
        RunWatchScheduler.enqueuePoll(context, "agent-1", "run-1", "name")
        assertEquals(first, infos("run-1").single().id)
    }

    @Test
    fun differentRunsGetTheirOwnWatcher() {
        RunWatchScheduler.enqueuePoll(context, "agent-1", "run-1", "a")
        RunWatchScheduler.enqueuePoll(context, "agent-2", "run-2", "b")
        assertEquals(1, infos("run-1").size)
        assertEquals(1, infos("run-2").size)
    }

    @Test
    fun theWorkerChainsItsNextPollWithoutCancellingItself() {
        RunWatchScheduler.enqueuePoll(context, "agent-1", "run-1", "name")
        val running = infos("run-1").single().id
        RunWatchScheduler.chainNextPoll(context, "agent-1", "run-1", "name")
        val all = infos("run-1")
        assertEquals(2, all.size)
        assertTrue(all.none { it.state == WorkInfo.State.CANCELLED })
        assertEquals(WorkInfo.State.ENQUEUED, all.first { it.id == running }.state)
        assertEquals(WorkInfo.State.BLOCKED, all.first { it.id != running }.state)
    }

    @Test
    fun anOpenThreadInTheForegroundIsNotWatchedInTheBackground() {
        VisibleAgent.set("agent-1")
        assertFalse(VisibleAgent.isOpenInForeground("agent-1"))
        VisibleAgent.activityStarted()
        assertTrue(VisibleAgent.isOpenInForeground("agent-1"))
        assertFalse(VisibleAgent.isOpenInForeground("agent-2"))
        VisibleAgent.activityStopped()
        assertFalse(VisibleAgent.isOpenInForeground("agent-1"))
        VisibleAgent.activityStarted()
        VisibleAgent.set(null)
        assertFalse(VisibleAgent.isOpenInForeground("agent-1"))
    }
}
