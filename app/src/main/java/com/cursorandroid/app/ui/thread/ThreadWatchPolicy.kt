package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.repo.StatusGate
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** What the thread knows when it decides whether the background "did something start elsewhere?" watch should call. */
data class WatchState(
    val foreground: Boolean,
    val finished: Boolean,
    val streamLive: Boolean,
    val busy: Boolean,
    val failures: Int = 0,
    val pausedForMs: Long = 0,
)

/**
 * The thread watch only exists to notice a run that started somewhere else. It stays silent for a finished
 * agent, while the app or the thread is not on screen, and while a stream or send already covers the run.
 * Everything else is checked at 5s, backing off to 60s after failures and always honouring a rate-limit pause.
 */
object ThreadWatchPolicy {
    const val IDLE_RECHECK_MS = 10_000L

    /** Milliseconds until the next status call, or null when none should be made. */
    fun nextDelayMs(state: WatchState): Long? {
        if (!state.foreground || state.finished || state.streamLive || state.busy) return null
        val backoff = if (state.failures <= 0) StatusGate.BASE_MS else StatusGate.backoffMs(state.failures + 1)
        return maxOf(backoff, state.pausedForMs).coerceAtMost(StatusGate.MAX_PAUSE_MS)
    }

    /** A thread whose agent is known to be done: nothing is running, nothing is waiting to be sent. */
    fun finished(agentWorking: Boolean?, runActive: Boolean, streaming: Boolean, outboundPending: Boolean): Boolean =
        agentWorking == false && !runActive && !streaming && !outboundPending

    /** A call is due after an event (resume, a live run noticed elsewhere) only when the thread is on screen. */
    fun kickAllowed(state: WatchState): Boolean = state.foreground && !state.busy
}

/** "Refresh paused, retrying in 12s" while the rate-limit gate is shut, or null when there is nothing to say. */
fun refreshPausedNote(remainingMs: Long): String? {
    if (remainingMs <= 0) return null
    val seconds = (remainingMs + 999) / 1000
    return "Refresh paused, retrying in ${seconds}s"
}

/**
 * Runs until cancelled. Each round waits for the policy's delay or a kick, then makes at most one status call,
 * and only when the policy still allows it after the wait. [attach] returns false on failure, which backs off.
 */
suspend fun threadWatchLoop(
    state: (failures: Int) -> WatchState,
    awaitKick: suspend (timeoutMs: Long) -> Boolean,
    attach: suspend () -> Boolean,
) {
    var failures = 0
    while (currentCoroutineContext().isActive) {
        val wait = ThreadWatchPolicy.nextDelayMs(state(failures))
        val kicked = awaitKick(wait ?: ThreadWatchPolicy.IDLE_RECHECK_MS)
        val now = state(failures)
        val due = if (kicked) ThreadWatchPolicy.kickAllowed(now) else ThreadWatchPolicy.nextDelayMs(now) != null
        if (!due) continue
        failures = if (attach()) 0 else failures + 1
    }
}
