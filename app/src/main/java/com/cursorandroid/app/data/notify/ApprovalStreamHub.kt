package com.cursorandroid.app.data.notify

import android.content.Context
import com.cursorandroid.app.CursorAndroidApp
import com.cursorandroid.app.data.api.StreamEvent
import com.cursorandroid.app.data.api.isTerminalStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object ApprovalStreamHub {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val closed = ConcurrentHashMap.newKeySet<String>()

    fun attach(context: Context, agentId: String, runId: String, agentName: String?) {
        val app = context.applicationContext as? CursorAndroidApp ?: return
        if (!app.container.store.notifyOnApproval) return
        if (runId.isBlank() || runId in closed || jobs[runId]?.isActive == true) return
        jobs[runId] = scope.launch {
            try {
                app.container.repo.stream(agentId, runId)
                    .catch { }
                    .collect { event ->
                        when (event) {
                            is StreamEvent.ToolCall -> {
                                runCatching {
                                    app.container.notifier.notifyApproval(
                                        agentId = agentId,
                                        agentName = agentName,
                                        runId = runId,
                                        callId = event.callId,
                                        name = event.name,
                                        status = event.status,
                                        args = event.args,
                                    )
                                }
                            }
                            is StreamEvent.Result -> {
                                if (isTerminalStatus(event.status)) {
                                    close(runId)
                                    detach(app, runId)
                                }
                            }
                            is StreamEvent.Done -> detach(app, runId)
                            else -> Unit
                        }
                    }
            } finally {
                val job = coroutineContext[Job]
                if (job != null) jobs.remove(runId, job)
            }
        }
    }

    fun detach(context: Context, runId: String) {
        jobs.remove(runId)?.cancel()
    }

    fun close(runId: String) {
        if (runId.isBlank()) return
        closed.add(runId)
        if (closed.size > 100) {
            closed.toList().take(closed.size - 80).forEach { closed.remove(it) }
        }
    }

    fun sync(context: Context, items: List<WatchItem>) {
        val live = items.map { it.runId }.toHashSet()
        jobs.keys.toList().forEach { runId ->
            if (runId !in live) detach(context, runId)
        }
        items.forEach { item ->
            attach(context, item.agentId, item.runId, item.agentName)
        }
    }

    fun stop(context: Context) {
        jobs.keys.toList().forEach { detach(context, it) }
    }
}
