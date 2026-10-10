package com.cursorandroid.app.data.notify

import android.content.Context
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cursorandroid.app.CursorAndroidApp
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.isActive
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.api.isTerminal
import kotlinx.coroutines.CancellationException

class InboxSweepWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? CursorAndroidApp ?: return Result.failure()
        if (app.container.store.demoMode || !app.container.store.hasKey()) return Result.success()
        val seen = applicationContext.getSharedPreferences(RunWatchScheduler.SEEN_PREFS, Context.MODE_PRIVATE)
        return try {
            val agents = app.container.repo.listAgents()
            val watched = RunWatchStore.all(applicationContext).mapTo(HashSet()) { it.runId }
            val updates = HashMap<String, String>()
            for (agent in SweepPlan.targets(agents, { seen.getString(it, null) }, watched)) {
                val runId = agent.latestRunId ?: continue
                val run = runCatching { app.container.repo.getRun(agent.id, runId) }.getOrNull() ?: continue
                val previous = seen.getString(runId, null)?.uppercase()
                if (run.isActive()) {
                    RunWatchScheduler.watch(applicationContext, agent.id, run.id, agent.name, run.status)
                } else if (run.isTerminal() && isLiveStatus(previous)) {
                    app.container.notifier.notifyIfNeeded(
                        agent.id,
                        agent.name,
                        run.id,
                        run.status,
                        run.result,
                        run.git?.branches?.firstOrNull()?.prUrl,
                    )
                    RunWatchStore.remove(applicationContext, run.id)
                    ApprovalStreamHub.close(run.id)
                    ApprovalStreamHub.detach(applicationContext, run.id)
                }
                run.status?.uppercase()?.let { updates[runId] = it }
            }
            val keep = agents.mapNotNullTo(HashSet()) { it.latestRunId } + watched
            val stale = SeenPrefs.staleKeys(seen.all.keys, keep)
            if (updates.isNotEmpty() || stale.isNotEmpty()) {
                seen.edit {
                    updates.forEach { (runId, status) -> putString(runId, status) }
                    stale.forEach { remove(it) }
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

internal object SweepPlan {
    /** Runs not seen before, live per the list (which can lag), last seen live, or being watched. */
    fun targets(
        agents: List<AgentSummary>,
        seenStatus: (String) -> String?,
        watched: Set<String>,
    ): List<AgentSummary> = agents.filter { agent ->
        val runId = agent.latestRunId ?: return@filter false
        val seen = seenStatus(runId)
        seen == null || isLiveStatus(agent.status) || isLiveStatus(seen) || runId in watched
    }
}
