package com.cursorandroid.app.ui.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadWatchPolicyTest {
    private val live = WatchState(foreground = true, finished = false, streamLive = false, busy = false)

    @Test
    fun aFinishedAgentIsNeverPolled() {
        assertNull(ThreadWatchPolicy.nextDelayMs(live.copy(finished = true)))
        assertNull(ThreadWatchPolicy.nextDelayMs(live.copy(finished = true, failures = 3, pausedForMs = 0)))
    }

    @Test
    fun finishedMeansDoneIdleAndNothingWaitingToBeSent() {
        assertTrue(ThreadWatchPolicy.finished(false, runActive = false, streaming = false, outboundPending = false))
        assertFalse(ThreadWatchPolicy.finished(null, runActive = false, streaming = false, outboundPending = false))
        assertFalse(ThreadWatchPolicy.finished(true, runActive = false, streaming = false, outboundPending = false))
        assertFalse(ThreadWatchPolicy.finished(false, runActive = true, streaming = false, outboundPending = false))
        assertFalse(ThreadWatchPolicy.finished(false, runActive = false, streaming = true, outboundPending = false))
        assertFalse(ThreadWatchPolicy.finished(false, runActive = false, streaming = false, outboundPending = true))
    }

    @Test
    fun nothingIsPolledWhileTheAppOrTheThreadIsNotOnScreen() {
        assertNull(ThreadWatchPolicy.nextDelayMs(live.copy(foreground = false)))
        assertFalse(ThreadWatchPolicy.kickAllowed(live.copy(foreground = false)))
        assertTrue(ThreadWatchPolicy.kickAllowed(live))
    }

    @Test
    fun aLiveStreamOrASendCoversTheRunSoTheWatchStaysQuiet() {
        assertNull(ThreadWatchPolicy.nextDelayMs(live.copy(streamLive = true)))
        assertNull(ThreadWatchPolicy.nextDelayMs(live.copy(busy = true)))
    }

    @Test
    fun anUnfinishedThreadIsCheckedEveryFiveSecondsAndBacksOffToAMinute() {
        assertEquals(5_000L, ThreadWatchPolicy.nextDelayMs(live))
        val delays = (1..6).map { ThreadWatchPolicy.nextDelayMs(live.copy(failures = it)) }
        assertEquals(listOf(10_000L, 20_000L, 40_000L, 60_000L, 60_000L, 60_000L), delays)
    }

    @Test
    fun aRateLimitPauseStretchesTheDelay() {
        assertEquals(37_000L, ThreadWatchPolicy.nextDelayMs(live.copy(pausedForMs = 37_000)))
        assertEquals(60_000L, ThreadWatchPolicy.nextDelayMs(live.copy(pausedForMs = 600_000)))
    }

    @Test
    fun theQuietNoteCountsDownInWholeSecondsAndDisappearsAtZero() {
        assertEquals("Refresh paused, retrying in 12s", refreshPausedNote(11_200))
        assertEquals("Refresh paused, retrying in 1s", refreshPausedNote(1))
        assertNull(refreshPausedNote(0))
    }
}

class ThreadWatchLoopTest {
    private val live = WatchState(foreground = true, finished = false, streamLive = false, busy = false)

    private class Stop : kotlinx.coroutines.CancellationException("done")

    /** Runs the loop on a virtual clock for [rounds] waits; returns the times at which a status call was made. */
    private fun run(rounds: Int, state: (Int, Long) -> WatchState, kicks: Set<Int> = emptySet(), ok: (Int) -> Boolean = { true }): List<Long> {
        var clock = 0L
        var round = 0
        val calls = ArrayList<Long>()
        try {
            kotlinx.coroutines.runBlocking {
                threadWatchLoop(
                    state = { failures -> state(failures, clock) },
                    awaitKick = { ms ->
                        if (round++ >= rounds) throw Stop()
                        if (round in kicks) {
                            true
                        } else {
                            clock += ms
                            false
                        }
                    },
                    attach = {
                        calls += clock
                        ok(calls.size)
                    },
                )
            }
        } catch (_: Stop) {
        }
        return calls
    }

    @Test
    fun aFinishedAgentIsNeverCalledNoMatterHowLongTheThreadStaysOpen() {
        val calls = run(rounds = 500, state = { _, _ -> live.copy(finished = true) })
        assertEquals(emptyList<Long>(), calls)
    }

    @Test
    fun aBackgroundedThreadIsNeverCalled() {
        val calls = run(rounds = 500, state = { _, _ -> live.copy(foreground = false) })
        assertEquals(emptyList<Long>(), calls)
    }

    @Test
    fun aFinishedAgentIsCalledOnceWhenSomethingKicksIt() {
        val calls = run(rounds = 10, state = { _, _ -> live.copy(finished = true) }, kicks = setOf(4))
        assertEquals(1, calls.size)
    }

    @Test
    fun aKickWhileBackgroundedMakesNoCall() {
        val calls = run(rounds = 10, state = { _, _ -> live.copy(foreground = false) }, kicks = setOf(2, 3))
        assertEquals(emptyList<Long>(), calls)
    }

    @Test
    fun anUnfinishedThreadIsCalledEveryFiveSeconds() {
        val calls = run(rounds = 4, state = { _, _ -> live })
        assertEquals(listOf(5_000L, 10_000L, 15_000L, 20_000L), calls)
    }

    @Test
    fun repeatedRateLimitsBackOffExponentiallyUpToAMinute() {
        val calls = run(rounds = 8, state = { f, _ -> live.copy(failures = f) }, ok = { false })
        val gaps = calls.zipWithNext { a, b -> b - a }
        assertEquals(listOf(10_000L, 20_000L, 40_000L, 60_000L, 60_000L, 60_000L, 60_000L), gaps.take(7))
        assertTrue(calls.size <= 8)
    }

    @Test
    fun aSuccessAfterFailuresGoesBackToTheNormalPace() {
        val calls = run(rounds = 6, state = { f, _ -> live.copy(failures = f) }, ok = { it != 1 && it != 2 })
        val gaps = calls.zipWithNext { a, b -> b - a }
        assertEquals(listOf(10_000L, 20_000L, 5_000L, 5_000L, 5_000L), gaps)
    }

    @Test
    fun theCallsWaitOutAServerPause() {
        val calls = run(rounds = 2, state = { f, now -> live.copy(failures = f, pausedForMs = (45_000L - now).coerceAtLeast(0)) })
        assertEquals(45_000L, calls.first())
    }
}
