package com.cursorandroid.app.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.isWorking
import java.util.concurrent.TimeUnit

object RunWatchScheduler {
    fun watch(context: Context, agentId: String, runId: String, agentName: String?) {
        val app = context.applicationContext
        app.getSharedPreferences("run_status_seen", Context.MODE_PRIVATE)
            .edit()
            .putString(runId, "RUNNING")
            .apply()
        val already = RunWatchStore.all(app).any { it.runId == runId }
        RunWatchStore.add(app, WatchItem(agentId, runId, agentName))
        ApprovalStreamHub.attach(app, agentId, runId, agentName)
        if (!already) enqueuePoll(app, agentId, runId, agentName)
        ensureSweep(app)
    }

    fun watchActive(context: Context, agents: List<AgentSummary>) {
        agents.filter { it.isWorking() }.forEach { agent ->
            val runId = agent.latestRunId ?: return@forEach
            watch(context, agent.id, runId, agent.name)
        }
    }

    fun resume(context: Context) {
        ensureSweep(context)
        val items = RunWatchStore.all(context)
        ApprovalStreamHub.sync(context.applicationContext, items)
        items.forEach { item ->
            enqueuePoll(context.applicationContext, item.agentId, item.runId, item.agentName)
        }
    }

    fun enqueuePoll(context: Context, agentId: String, runId: String, agentName: String?) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<RunWatchWorker>()
            .setConstraints(constraints)
            .setInitialDelay(20, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    RunWatchWorker.KEY_AGENT_ID to agentId,
                    RunWatchWorker.KEY_RUN_ID to runId,
                    RunWatchWorker.KEY_AGENT_NAME to agentName,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "watch-$runId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)
        wm.cancelUniqueWork("inbox-sweep")
        RunWatchStore.all(app).forEach { wm.cancelUniqueWork("watch-${it.runId}") }
        ApprovalStreamHub.stop(app)
        RunWatchStore.clear(app)
    }

    fun ensureSweep(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<InboxSweepWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "inbox-sweep",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
