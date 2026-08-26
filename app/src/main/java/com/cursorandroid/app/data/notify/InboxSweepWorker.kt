package com.cursorandroid.app.data.notify

import android.content.Context
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cursorandroid.app.CursorAndroidApp
import com.cursorandroid.app.data.api.isActive
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.api.isTerminal

class InboxSweepWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? CursorAndroidApp ?: return Result.failure()
        if (!app.container.store.hasKey()) return Result.success()
        val seen = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return try {
            val agents = app.container.repo.listAgents()
            for (agent in agents) {
                val runId = agent.latestRunId ?: continue
                val run = runCatching { app.container.repo.getRun(agent.id, runId) }.getOrNull() ?: continue
                val status = run.status?.uppercase()
                val previous = seen.getString(runId, null)?.uppercase()
                if (run.isActive()) {
                    RunWatchScheduler.watch(applicationContext, agent.id, run.id, agent.name)
                } else if (run.isTerminal() && isLiveStatus(previous)) {
                    app.container.notifier.notifyIfNeeded(
                        agent.id,
                        agent.name,
                        run.id,
                        run.status,
                        run.result,
                    )
                    RunWatchStore.remove(applicationContext, run.id)
                    ApprovalStreamHub.close(run.id)
                    ApprovalStreamHub.detach(applicationContext, run.id)
                }
                if (status != null) {
                    seen.edit { putString(runId, status) }
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PREFS = "run_status_seen"
    }
}
