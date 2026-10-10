package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.isCreatingStatus
import com.cursorandroid.app.data.api.isLiveStatus
import kotlin.coroutines.cancellation.CancellationException

sealed interface StopOutcome {
    data object Stopped : StopOutcome

    data object AlreadyFinished : StopOutcome

    data class Failed(val reason: String) : StopOutcome
}

/**
 * Stops the active run of an agent. The API has no agent-level stop, so a missing run id is resolved
 * from the run list instead of being skipped. Every call ends in an outcome the UI can show.
 */
class RunStopper(
    private val cancel: suspend (agentId: String, runId: String) -> Unit,
    private val listRuns: suspend (agentId: String) -> List<Run>,
) {
    suspend fun stop(agentId: String, knownRunId: String?, latestRunId: String?): StopOutcome {
        val known = knownRunId?.takeIf { it.isNotBlank() } ?: latestRunId?.takeIf { it.isNotBlank() }
        if (known == null) {
            val id = when (val found = activeRun(agentId, exclude = null)) {
                is Lookup.Failed -> return StopOutcome.Failed(found.reason)
                is Lookup.Found -> found.id
                Lookup.None -> return StopOutcome.AlreadyFinished
            }
            return cancelOne(agentId, id)
        }
        val first = cancelOne(agentId, known)
        if (first != StopOutcome.AlreadyFinished) return first
        // The run we held may be an old one while a newer run is the one going.
        val other = (activeRun(agentId, exclude = known) as? Lookup.Found)?.id ?: return first
        return cancelOne(agentId, other)
    }

    private sealed interface Lookup {
        data class Found(val id: String) : Lookup

        data object None : Lookup

        data class Failed(val reason: String) : Lookup
    }

    private suspend fun cancelOne(agentId: String, runId: String): StopOutcome {
        return try {
            cancel(agentId, runId)
            StopOutcome.Stopped
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.isNotCancellable) StopOutcome.AlreadyFinished else StopOutcome.Failed(reasonOf(e))
        } catch (e: Exception) {
            StopOutcome.Failed(reasonOf(e))
        }
    }

    private suspend fun activeRun(agentId: String, exclude: String?): Lookup {
        val runs = try {
            listRuns(agentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Lookup.Failed(reasonOf(e))
        }
        val active = runs.filter { it.id != exclude && (isLiveStatus(it.status) || isCreatingStatus(it.status)) }
        val newest = active.maxByOrNull { it.createdAt.orEmpty() } ?: return Lookup.None
        return Lookup.Found(newest.id)
    }

    private fun reasonOf(e: Exception): String {
        val text = (e as? ApiException)?.let { "HTTP ${it.code} ${it.message}" } ?: e.message ?: e::class.java.simpleName
        return text.take(160)
    }
}
