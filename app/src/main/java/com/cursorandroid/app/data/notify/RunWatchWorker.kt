package com.cursorandroid.app.data.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cursorandroid.app.CursorAndroidApp
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.isActive
import com.cursorandroid.app.data.api.isTerminal
import kotlinx.coroutines.CancellationException

class RunWatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? CursorAndroidApp ?: return Result.failure()
        if (app.container.store.demoMode) {
            inputData.getString(KEY_RUN_ID)?.let { runId ->
                RunWatchStore.remove(applicationContext, runId)
                ApprovalStreamHub.close(runId)
                ApprovalStreamHub.detach(applicationContext, runId)
            }
            return Result.success()
        }
        val agentId = inputData.getString(KEY_AGENT_ID) ?: return Result.failure()
        val runId = inputData.getString(KEY_RUN_ID) ?: return Result.failure()
        val agentName = inputData.getString(KEY_AGENT_NAME)
        if (VisibleAgent.isOpenInForeground(agentId) || VisibleAgent.inboxCoversRuns()) {
            RunWatchScheduler.chainNextPoll(applicationContext, agentId, runId, agentName)
            return Result.success()
        }
        return try {
            val run = app.container.repo.getRun(agentId, runId)
            when {
                run.isTerminal() -> {
                    app.container.notifier.notifyIfNeeded(
                        agentId,
                        agentName,
                        run.id,
                        run.status,
                        run.result,
                        run.git?.branches?.firstOrNull()?.prUrl,
                    )
                    RunWatchStore.remove(applicationContext, run.id)
                    ApprovalStreamHub.close(run.id)
                    ApprovalStreamHub.detach(applicationContext, run.id)
                    Result.success()
                }
                run.isActive() -> {
                    RunWatchStore.add(applicationContext, WatchItem(agentId, run.id, agentName))
                    ApprovalStreamHub.attach(applicationContext, agentId, run.id, agentName)
                    RunWatchScheduler.chainNextPoll(applicationContext, agentId, run.id, agentName)
                    Result.success()
                }
                else -> {
                    RunWatchStore.remove(applicationContext, runId)
                    Result.success()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failures = inputData.getInt(KEY_FAILURES, 0) + 1
            if (giveUp(e, failures)) {
                RunWatchStore.remove(applicationContext, runId)
                ApprovalStreamHub.detach(applicationContext, runId)
            } else {
                RunWatchScheduler.chainNextPoll(applicationContext, agentId, runId, agentName, failures)
            }
            Result.success()
        }
    }

    companion object {
        const val KEY_AGENT_ID = "agent_id"
        const val KEY_RUN_ID = "run_id"
        const val KEY_AGENT_NAME = "agent_name"
        const val KEY_FAILURES = "failures"
        const val MAX_FAILURES = 12

        // 401/403: key revoked. 404: agent or run deleted. The inbox sweep re-adds live runs.
        fun giveUp(e: Throwable, failures: Int): Boolean {
            val code = (e as? ApiException)?.code
            return code == 401 || code == 403 || code == 404 || failures >= MAX_FAILURES
        }

        fun backoffSeconds(failures: Int): Long = when {
            failures <= 0 -> 20L
            else -> (20L shl failures.coerceAtMost(4)).coerceAtMost(300L)
        }
    }
}
