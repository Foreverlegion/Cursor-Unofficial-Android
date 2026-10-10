package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.isArchived
import com.cursorandroid.app.data.api.isLiveStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

enum class RefreshReason {
    Start,
    Resume,
    Notification,
    RunStarted,
    Manual,
    Tick,
}

/** Wakes the inbox poll loop early. Only the latest pending request is kept. */
class InboxRefreshHub {
    private val pending = Channel<RefreshReason>(Channel.CONFLATED)

    fun request(reason: RefreshReason) {
        if (reason != RefreshReason.Tick) pending.trySend(reason)
    }

    fun drain() {
        while (pending.tryReceive().isSuccess) Unit
    }

    suspend fun await(timeoutMs: Long): RefreshReason? = withTimeoutOrNull(timeoutMs) { pending.receive() }
}

object InboxPollPolicy {
    const val ACTIVE_MS = 4_000L
    const val IDLE_MS = 20_000L
    const val MAX_BACKOFF_MS = 60_000L
    const val MIN_GAP_MS = 1_500L
    const val FULL_HYDRATE_EVERY = 5
    const val MACHINES_EVERY_MS = 60_000L
    const val RUN_CHECK_LIMIT = 6

    fun busy(agents: List<AgentSummary>, localActive: Set<String>): Boolean =
        localActive.isNotEmpty() || agents.any { isLiveStatus(it.status) }

    fun intervalMs(agents: List<AgentSummary>, localActive: Set<String>, failures: Int): Long {
        val base = if (busy(agents, localActive)) ACTIVE_MS else IDLE_MS
        if (failures <= 0) return base
        return (base shl failures.coerceAtMost(4)).coerceAtMost(MAX_BACKOFF_MS)
    }

    /** Time to wait before polling after [reason]: a tick waits the interval, a trigger only the minimum gap. */
    fun pauseBefore(reason: RefreshReason, sinceLastPollMs: Long): Long =
        if (reason == RefreshReason.Tick) 0 else (MIN_GAP_MS - sinceLastPollMs).coerceAtLeast(0)

    fun fullHydrate(reason: RefreshReason, tick: Int): Boolean =
        reason != RefreshReason.Tick || tick % FULL_HYDRATE_EVERY == 0

    fun machinesDue(reason: RefreshReason, lastAtMs: Long, nowMs: Long): Boolean =
        reason != RefreshReason.Tick || nowMs - lastAtMs >= MACHINES_EVERY_MS
}

/**
 * Agents whose list entry points at a run we have not seen before while the list says they are idle.
 * That is a run started elsewhere, and the list can lag behind the run itself.
 */
fun runCheckTargets(
    previous: List<AgentSummary>,
    incoming: List<AgentSummary>,
    settled: Map<String, RunSettled>,
    limit: Int = InboxPollPolicy.RUN_CHECK_LIMIT,
): List<AgentSummary> {
    if (previous.isEmpty()) return emptyList()
    val before = previous.associateBy { it.id }
    return incoming
        .filter { agent ->
            val runId = agent.latestRunId
            !runId.isNullOrBlank() &&
                !isLiveStatus(agent.status) &&
                !agent.isArchived() &&
                before[agent.id]?.latestRunId != runId &&
                settled[agent.id]?.runId != runId
        }
        .take(limit)
}

/** Records what each fetched run says: live runs go to the live map, ended ones settle the card. */
fun applyRunChecks(hub: RunSettleHub, checks: List<Pair<AgentSummary, Run?>>): Int {
    var changed = 0
    for ((agent, run) in checks) {
        if (run == null) continue
        val live = isLiveStatus(run.status) && hub.publishLive(agent.id, run.id, run.status)
        val ended = !isLiveStatus(run.status) && hub.publish(agent.id, run.id, run.status, run.result)
        if (live || ended) changed++
    }
    return changed
}

/**
 * Runs until cancelled. The first pass is immediate, then it polls every interval, or sooner when
 * a trigger arrives. One pass at a time, so passes never overlap, and a trigger that arrives during
 * a pass is handled right after it.
 */
suspend fun inboxRefreshLoop(
    first: RefreshReason,
    intervalMs: (failures: Int) -> Long,
    now: () -> Long,
    awaitTrigger: suspend (timeoutMs: Long) -> RefreshReason?,
    pause: suspend (ms: Long) -> Unit,
    poll: suspend (reason: RefreshReason, tick: Int) -> Boolean,
) {
    var reason = first
    var failures = 0
    var tick = 0
    var lastAt = Long.MIN_VALUE / 2
    while (true) {
        val wait = InboxPollPolicy.pauseBefore(reason, now() - lastAt)
        if (wait > 0) pause(wait)
        if (reason == RefreshReason.Tick) tick++
        val ok = try {
            poll(reason, tick)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        lastAt = now()
        failures = if (ok) 0 else failures + 1
        reason = awaitTrigger(intervalMs(failures)) ?: RefreshReason.Tick
    }
}
