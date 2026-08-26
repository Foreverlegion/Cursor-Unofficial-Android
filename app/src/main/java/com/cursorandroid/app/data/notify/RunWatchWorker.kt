package com.cursorandroid.app.data.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cursorandroid.app.CursorAndroidApp
import com.cursorandroid.app.data.api.isActive
import com.cursorandroid.app.data.api.isTerminal

class RunWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? CursorAndroidApp ?: return Result.failure()
        val agentId = inputData.getString(KEY_AGENT_ID) ?: return Result.failure()
        val runId = inputData.getString(KEY_RUN_ID) ?: return Result.failure()
        val agentName = inputData.getString(KEY_AGENT_NAME)
        return try {
            val run = app.container.repo.getRun(agentId, runId)
            when {
                run.isTerminal() -> {
                    app.container.notifier.notifyIfNeeded(agentId, agentName, run.id, run.status, run.result)
                    RunWatchStore.remove(applicationContext, run.id)
                    ApprovalStreamHub.close(run.id)
                    ApprovalStreamHub.detach(applicationContext, run.id)
                    Result.success()
                }
                run.isActive() -> {
                    RunWatchStore.add(applicationContext, WatchItem(agentId, run.id, agentName))
                    ApprovalStreamHub.attach(applicationContext, agentId, run.id, agentName)
                    RunWatchScheduler.enqueuePoll(applicationContext, agentId, run.id, agentName)
                    Result.success()
                }
                else -> {
                    RunWatchStore.remove(applicationContext, runId)
                    Result.success()
                }
            }
        } catch (_: Exception) {
            RunWatchScheduler.enqueuePoll(applicationContext, agentId, runId, agentName)
            Result.success()
        }
    }

    companion object {
        const val KEY_AGENT_ID = "agent_id"
        const val KEY_RUN_ID = "run_id"
        const val KEY_AGENT_NAME = "agent_name"
    }
}
