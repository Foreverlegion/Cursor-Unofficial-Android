package com.cursorandroid.app.data.notify

import com.cursorandroid.app.ui.status.RunIndicator
import com.cursorandroid.app.ui.status.runIndicator
import com.cursorandroid.app.ui.status.runStatusLabel
import com.cursorandroid.app.ui.status.threadStatusLine

data class LiveRunNotificationContent(
    val title: String,
    val body: String,
    val shortCriticalText: String,
)

/**
 * Maps run state to shade copy for an ongoing live update. Returns null when the run is no longer live
 * (caller should clear the ongoing notification and use the normal completion alert).
 */
fun liveRunNotificationContent(
    chatTitle: String,
    agentStatus: String?,
    runStatus: String?,
    activityHint: String? = null,
    approvalPending: Boolean = false,
): LiveRunNotificationContent? {
    val indicator = runIndicator(agentStatus, runStatus, approvalPending)
    if (indicator == RunIndicator.Done || indicator == RunIndicator.Failed) return null
    val title = chatTitle.trim().ifBlank { "Agent" }
    val body = when {
        !activityHint.isNullOrBlank() -> activityHint.trim().take(140)
        indicator == RunIndicator.NeedsApproval -> "Waiting for approval"
        else -> {
            val line = threadStatusLine(agentStatus, runStatus, indicator)
            if (line.isNotBlank()) line else (runStatusLabel(runStatus) ?: "Running")
        }
    }
    val chip = when {
        indicator == RunIndicator.NeedsApproval -> "Approve"
        !activityHint.isNullOrBlank() -> {
            activityHint.lineSequence().firstOrNull()?.trim()?.take(12)?.ifBlank { null } ?: "Working"
        }
        normalizeRun(runStatus) == "CREATING" -> "Starting"
        else -> runStatusLabel(runStatus)?.take(12) ?: "Running"
    }
    return LiveRunNotificationContent(
        title = title,
        body = body,
        shortCriticalText = chip,
    )
}

private fun normalizeRun(status: String?): String =
    status?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_').orEmpty()

fun liveNotifyId(runId: String): Int = ("live:$runId").hashCode()
