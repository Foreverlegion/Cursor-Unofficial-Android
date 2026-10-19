package com.cursorandroid.app.data.notify

import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.isArchived
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.repo.StatusGate

/** Which agents the periodic sweep spends a status call on. Finished agents we already know about are skipped. */
fun sweepTargets(
    agents: List<AgentSummary>,
    seenStatus: (runId: String) -> String?,
    limit: Int = SWEEP_LIMIT,
): List<AgentSummary> =
    agents
        .filter { agent ->
            val runId = agent.latestRunId
            when {
                runId.isNullOrBlank() || agent.isArchived() -> false
                isLiveStatus(agent.status) -> true
                else -> isLiveStatus(seenStatus(runId))
            }
        }
        .take(limit)

const val SWEEP_LIMIT = 25

/** Spacing for the per-run background watch. A 429 or other failure stretches it; a 404 ends the watch. */
object RunWatchBackoff {
    const val NORMAL_MS = 20_000L
    const val MAX_MS = 5 * 60_000L

    fun gone(e: Throwable): Boolean = e is ApiException && e.code == 404

    /** The saved key was refused: every further poll would fail the same way, so the watch ends. */
    fun signedOut(e: Throwable): Boolean = e is ApiException && e.isInvalidKey

    fun delayMs(failures: Int, error: Throwable?): Long {
        if (failures <= 0 && error == null) return NORMAL_MS
        val grown = (NORMAL_MS shl failures.coerceIn(0, 4)).coerceAtMost(MAX_MS)
        val api = error as? ApiException
        val server = if (api?.isRateLimited == true) api.retryAfterMs ?: 0L else 0L
        val gate = if (api?.isRateLimited == true) StatusGate.backoffMs(failures.coerceAtLeast(1)) else 0L
        return maxOf(NORMAL_MS, grown, gate).coerceAtMost(MAX_MS).coerceAtLeast(server)
    }
}
