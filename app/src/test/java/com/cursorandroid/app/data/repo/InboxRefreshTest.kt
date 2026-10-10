package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.notify.NoticeStore
import com.cursorandroid.app.data.notify.RunNotifier
import com.cursorandroid.app.data.notify.VisibleAgent
import com.cursorandroid.app.ui.status.RunIndicator
import com.cursorandroid.app.ui.status.runIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InboxRefreshTest {
    private fun agent(id: String, status: String, run: String?, archived: Boolean? = null) =
        AgentSummary(id = id, status = status, latestRunId = run, archived = archived)

    @After
    fun tearDown() {
        VisibleAgent.resetForTest()
    }

    @Test
    fun pollsFasterWhileAnythingRunsAndSlowerWhenIdle() {
        val idle = listOf(agent("a", "FINISHED", "r1"))
        val running = listOf(agent("a", "FINISHED", "r1"), agent("b", "ACTIVE", "r2"))
        assertEquals(InboxPollPolicy.IDLE_MS, InboxPollPolicy.intervalMs(idle, emptySet(), 0))
        assertEquals(InboxPollPolicy.ACTIVE_MS, InboxPollPolicy.intervalMs(running, emptySet(), 0))
        assertEquals(InboxPollPolicy.ACTIVE_MS, InboxPollPolicy.intervalMs(idle, setOf("a"), 0))
        assertTrue(InboxPollPolicy.ACTIVE_MS < InboxPollPolicy.IDLE_MS)
    }

    @Test
    fun failuresBackOffAndAreCapped() {
        val idle = listOf(agent("a", "FINISHED", "r1"))
        assertEquals(InboxPollPolicy.IDLE_MS * 2, InboxPollPolicy.intervalMs(idle, emptySet(), 1))
        assertEquals(InboxPollPolicy.MAX_BACKOFF_MS, InboxPollPolicy.intervalMs(idle, emptySet(), 6))
        val running = listOf(agent("b", "RUNNING", "r2"))
        assertEquals(InboxPollPolicy.ACTIVE_MS * 2, InboxPollPolicy.intervalMs(running, emptySet(), 1))
        assertEquals(InboxPollPolicy.MAX_BACKOFF_MS, InboxPollPolicy.intervalMs(running, emptySet(), 9))
    }

    @Test
    fun triggersWaitOnlyForTheMinimumGapAndTicksNotAtAll() {
        assertEquals(0, InboxPollPolicy.pauseBefore(RefreshReason.Tick, 10))
        assertEquals(InboxPollPolicy.MIN_GAP_MS - 200, InboxPollPolicy.pauseBefore(RefreshReason.Notification, 200))
        assertEquals(0, InboxPollPolicy.pauseBefore(RefreshReason.Resume, 60_000))
    }

    @Test
    fun hubKeepsOnlyTheLatestTriggerAndIgnoresTicks() = runBlocking {
        val hub = InboxRefreshHub()
        hub.request(RefreshReason.Tick)
        assertNull(hub.await(10))
        hub.request(RefreshReason.Notification)
        hub.request(RefreshReason.RunStarted)
        assertEquals(RefreshReason.RunStarted, hub.await(10))
        assertNull(hub.await(10))
        hub.request(RefreshReason.Manual)
        hub.drain()
        assertNull(hub.await(10))
    }

    private class Script(private val triggers: List<RefreshReason?>) {
        val polls = ArrayList<Pair<RefreshReason, Int>>()
        val waits = ArrayList<Long>()
        val pauses = ArrayList<Long>()
        var clock = 1_000_000L
        var running = 0
        var overlap = false
        private var next = 0

        suspend fun run(ok: (Int) -> Boolean = { true }, interval: (Int) -> Long = { 20_000L }) {
            try {
                inboxRefreshLoop(
                    first = RefreshReason.Resume,
                    intervalMs = interval,
                    now = { clock },
                    awaitTrigger = { timeout ->
                        waits += timeout
                        if (next >= triggers.size) throw CancellationException("done")
                        triggers[next++].also { if (it == null) clock += timeout }
                    },
                    pause = { clock += it; pauses += it },
                    poll = { reason, tick ->
                        running++
                        if (running > 1) overlap = true
                        polls += reason to tick
                        clock += 100
                        running--
                        ok(polls.size)
                    },
                )
            } catch (_: CancellationException) {
            }
        }
    }

    @Test
    fun firstPassRunsAtOnceAndThenOnEveryIntervalAndTrigger() = runBlocking {
        val script = Script(listOf(null, RefreshReason.Notification, null))
        script.run()
        assertEquals(
            listOf(
                RefreshReason.Resume to 0,
                RefreshReason.Tick to 1,
                RefreshReason.Notification to 1,
                RefreshReason.Tick to 2,
            ),
            script.polls,
        )
        assertFalse(script.overlap)
    }

    @Test
    fun aTriggerRightAfterAPassWaitsOutTheMinimumGap() = runBlocking {
        val script = Script(listOf(RefreshReason.Notification))
        script.run()
        assertEquals(1, script.pauses.size)
        assertTrue(script.pauses.single() in 1..InboxPollPolicy.MIN_GAP_MS)
    }

    @Test
    fun failedPassesStretchTheWaitAndASuccessResetsIt() = runBlocking {
        val script = Script(listOf(null, null, null))
        script.run(ok = { n -> n >= 3 }, interval = { failures -> 1000L * (failures + 1) })
        assertEquals(listOf(2000L, 3000L, 1000L, 1000L), script.waits)
    }

    @Test
    fun theLoopStopsWhenItsScopeIsCancelled() = runBlocking {
        val script = Script(emptyList())
        script.run()
        assertEquals(1, script.polls.size)
    }

    @Test
    fun aRunStartedElsewhereIsPickedUpFromTheLatestRunId() {
        val before = listOf(agent("a", "FINISHED", "r1"), agent("b", "FINISHED", "r5"))
        val incoming = listOf(agent("a", "FINISHED", "r2"), agent("b", "FINISHED", "r5"))
        assertEquals(listOf("a"), runCheckTargets(before, incoming, emptyMap()).map { it.id })
    }

    @Test
    fun noRunChecksOnTheFirstLoadOrForLiveArchivedOrSettledAgents() {
        val incoming = listOf(
            agent("a", "FINISHED", "r2"),
            agent("live", "ACTIVE", "r3"),
            agent("old", "FINISHED", "r4", archived = true),
            agent("done", "FINISHED", "r6"),
        )
        assertTrue(runCheckTargets(emptyList(), incoming, emptyMap()).isEmpty())
        val before = listOf(
            agent("a", "FINISHED", "r1"),
            agent("live", "FINISHED", "r1"),
            agent("old", "FINISHED", "r1"),
            agent("done", "FINISHED", "r1"),
        )
        val settled = mapOf("done" to RunSettled("done", "r6", "FINISHED"))
        assertEquals(listOf("a"), runCheckTargets(before, incoming, settled).map { it.id })
    }

    @Test
    fun runChecksAreCapped() {
        val before = (1..10).map { agent("a$it", "FINISHED", "old$it") }
        val incoming = (1..10).map { agent("a$it", "FINISHED", "new$it") }
        assertEquals(InboxPollPolicy.RUN_CHECK_LIMIT, runCheckTargets(before, incoming, emptyMap()).size)
    }

    @Test
    fun aLiveRunMakesTheStaleCardRunningAndItStaysSoAcrossListRefreshes() {
        val hub = RunSettleHub()
        val stale = listOf(agent("a", "FINISHED", "r2"))
        assertEquals(1, applyRunChecks(hub, listOf(stale[0] to Run(id = "r2", status = "RUNNING"))))

        val card = settleAgents(stale, hub.settled.value, hub.liveRuns.value).single()
        assertEquals(RunIndicator.Running, runIndicator(card.status))
        val again = settleAgents(stale, hub.settled.value, hub.liveRuns.value).single()
        assertEquals(RunIndicator.Running, runIndicator(again.status))
    }

    @Test
    fun aFinishedRunClearsTheLiveStateAndTheCardGoesDone() {
        val hub = RunSettleHub()
        val stale = listOf(agent("a", "FINISHED", "r2"))
        applyRunChecks(hub, listOf(stale[0] to Run(id = "r2", status = "RUNNING")))
        hub.publish("a", "r2", "FINISHED")

        assertTrue(hub.liveRuns.value.isEmpty())
        val card = settleAgents(stale, hub.settled.value, hub.liveRuns.value).single()
        assertEquals(RunIndicator.Done, runIndicator(card.status))
        assertFalse(hub.publishLive("a", "r2", "RUNNING"))
    }

    @Test
    fun aLiveRunOfAnOlderRunDoesNotTouchANewerOne() {
        val hub = RunSettleHub()
        hub.publishLive("a", "r2", "RUNNING")
        val card = settleAgents(listOf(agent("a", "FINISHED", "r3")), hub.settled.value, hub.liveRuns.value).single()
        assertEquals(RunIndicator.Done, runIndicator(card.status))
    }

    @Test
    fun theCardAndTheThreadReadTheSameRunState() {
        val hub = RunSettleHub()
        val stale = agent("a", "FINISHED", "r2")
        applyRunChecks(hub, listOf(stale to Run(id = "r2", status = "RUNNING")))
        val card = settleAgents(listOf(stale), hub.settled.value, hub.liveRuns.value).single()
        val thread = com.cursorandroid.app.ui.thread.threadRunState(
            lines = emptyList(),
            agentStatus = stale.status,
            runStatus = "RUNNING",
            busy = false,
            streaming = false,
            receiving = false,
            approvalPending = false,
        )
        assertEquals(runIndicator(card.status), thread.indicator)
    }

    @Test
    fun fullHydrateAndMachineListingAreThrottledOnTicks() {
        assertTrue(InboxPollPolicy.fullHydrate(RefreshReason.Resume, 1))
        assertTrue(InboxPollPolicy.fullHydrate(RefreshReason.Tick, InboxPollPolicy.FULL_HYDRATE_EVERY))
        assertFalse(InboxPollPolicy.fullHydrate(RefreshReason.Tick, 1))
        assertTrue(InboxPollPolicy.machinesDue(RefreshReason.Resume, 1_000, 2_000))
        assertFalse(InboxPollPolicy.machinesDue(RefreshReason.Tick, 1_000, 2_000))
        assertTrue(InboxPollPolicy.machinesDue(RefreshReason.Tick, 0, InboxPollPolicy.MACHINES_EVERY_MS))
    }

    @Test
    fun backgroundWatchersSitOutOnlyWhileTheInboxIsPollingInTheForeground() {
        assertFalse(VisibleAgent.inboxCoversRuns())
        VisibleAgent.inboxPolling(true)
        assertFalse(VisibleAgent.inboxCoversRuns())
        VisibleAgent.activityStarted()
        assertTrue(VisibleAgent.inboxCoversRuns())
        VisibleAgent.activityStopped()
        assertFalse(VisibleAgent.inboxCoversRuns())
        VisibleAgent.activityStarted()
        VisibleAgent.inboxPolling(false)
        assertFalse(VisibleAgent.inboxCoversRuns())
    }

    @Test
    fun aFinishedRunNotificationAsksTheInboxToRefresh() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("cursor_secure", "cursor_secure_bak", "cursor_secure_fallback", "cursor_prefs").forEach {
            context.deleteSharedPreferences(it)
        }
        val ui = UiPrefsStore.open(context)
        try {
            val store = ApiKeyStore(context, ui) { c, name -> c.getSharedPreferences(name, Context.MODE_PRIVATE) }
            val hub = InboxRefreshHub()
            val settle = RunSettleHub()
            val notifier = RunNotifier(context, store, NoticeStore(context), LocalChatStore(ui), settle, hub)
            notifier.notifyIfNeeded("a1", "Chat", "r1", "FINISHED", "done")
            runBlocking { assertEquals(RefreshReason.Notification, hub.await(50)) }
            assertEquals("FINISHED", settle.current("a1")?.status)
        } finally {
            ui.close()
        }
    }
}
