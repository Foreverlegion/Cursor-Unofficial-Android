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
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.api.isWorking
import java.util.concurrent.TimeUnit

object RunWatchScheduler {
    fun watch(
        context: Context,
        agentId: String,
        runId: String,
        agentName: String?,
        status: String? = null,
    ) {
        val app = context.applicationContext
        val recorded = status?.uppercase()?.takeIf { it.isNotBlank() } ?: "RUNNING"
        if (!isLiveStatus(recorded)) return
        val seen = app.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE)
        if (seen.getString(runId, null) != recorded) seen.edit().putString(runId, recorded).apply()
        ApprovalStreamHub.attach(app, agentId, runId, agentName)
        if (RunWatchStore.all(app).any { it.runId == runId }) return
        RunWatchStore.add(app, WatchItem(agentId, runId, agentName))
        enqueuePoll(app, agentId, runId, agentName)
        ensureSweep(app)
    }

    fun rememberStatuses(context: Context, agents: List<AgentSummary>) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE)
        val changed = agents.mapNotNull { agent ->
            val runId = agent.latestRunId ?: return@mapNotNull null
            val status = agent.status?.uppercase() ?: return@mapNotNull null
            if (prefs.getString(runId, null) == status) null else runId to status
        }
        val watched = RunWatchStore.all(app).mapTo(HashSet()) { it.runId }
        val keep = agents.mapNotNullTo(HashSet()) { it.latestRunId } + watched
        val stale = SeenPrefs.staleKeys(prefs.all.keys, keep)
        if (changed.isEmpty() && stale.isEmpty()) return
        prefs.edit().apply {
            changed.forEach { (runId, status) -> putString(runId, status) }
            stale.forEach { remove(it) }
        }.apply()
    }

    fun watchActive(context: Context, agents: List<AgentSummary>) {
        rememberStatuses(context, agents)
        agents.filter { it.isWorking() }.forEach { agent ->
            val runId = agent.latestRunId ?: return@forEach
            watch(context, agent.id, runId, agent.name, agent.status)
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

    fun enqueuePoll(
        context: Context,
        agentId: String,
        runId: String,
        agentName: String?,
        failures: Int = 0,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<RunWatchWorker>()
            .setConstraints(constraints)
            .setInitialDelay(RunWatchWorker.backoffSeconds(failures), TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    RunWatchWorker.KEY_AGENT_ID to agentId,
                    RunWatchWorker.KEY_RUN_ID to runId,
                    RunWatchWorker.KEY_AGENT_NAME to agentName,
                    RunWatchWorker.KEY_FAILURES to failures,
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

    const val SEEN_PREFS = "run_status_seen"
}
