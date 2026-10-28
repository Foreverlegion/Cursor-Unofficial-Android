package com.cursorandroid.app.data.notify

import com.cursorandroid.app.ui.status.RunIndicator
import com.cursorandroid.app.ui.status.runIndicator
import com.cursorandroid.app.ui.status.runStatusLabel
import com.cursorandroid.app.ui.status.threadStatusLine
import kotlin.math.max

data class LiveRunNotificationContent(
    val title: String,
    val body: String,
    val shortCriticalText: String,
)

/** Tag for the single ongoing live-run notification per agent. */
const val LIVE_RUN_NOTIFICATION_TAG = "cursor_agent_live"

data class LiveNotificationSlot(
    val tag: String,
    val id: Int,
)

fun liveNotificationSlot(agentId: String): LiveNotificationSlot {
    val trimmed = agentId.trim()
    return LiveNotificationSlot(
        tag = LIVE_RUN_NOTIFICATION_TAG,
        id = stableNotificationId("live_agent:$trimmed"),
    )
}

/** Pre-1.0.35 run-id keyed notifications; cancel on upgrade so they do not stack. */
fun legacyLiveNotificationSlot(runId: String): LiveNotificationSlot =
    LiveNotificationSlot(
        tag = LIVE_RUN_NOTIFICATION_TAG,
        id = stableNotificationId("live:$runId"),
    )

fun stableNotificationId(key: String): Int = key.hashCode() and 0x7fffffff

/** @deprecated Use [liveNotificationSlot] keyed by agent id. */
fun liveNotifyId(runId: String): Int = legacyLiveNotificationSlot(runId).id

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
    startedAtMs: Long = 0L,
    nowMs: Long = System.currentTimeMillis(),
): LiveRunNotificationContent? {
    val indicator = runIndicator(agentStatus, runStatus, approvalPending)
    if (indicator == RunIndicator.Done || indicator == RunIndicator.Failed) return null
    val title = chatTitle.trim().ifBlank { "Agent" }
    val statusBody = when {
        !activityHint.isNullOrBlank() -> activityHint.trim().take(140)
        indicator == RunIndicator.NeedsApproval -> "Waiting for approval"
        else -> {
            val line = threadStatusLine(agentStatus, runStatus, indicator)
            if (line.isNotBlank()) line else (runStatusLabel(runStatus) ?: "Running")
        }
    }
    val elapsed = formatLiveRunElapsed(startedAtMs, nowMs)
    val body = if (elapsed != null) "$statusBody · $elapsed" else statusBody
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

fun formatLiveRunElapsed(startedAtMs: Long, nowMs: Long): String? {
    if (startedAtMs <= 0L || nowMs <= startedAtMs) return null
    val seconds = max(0L, (nowMs - startedAtMs) / 1000L)
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours >= 1 -> "${hours}h ${minutes % 60}m"
        minutes >= 1 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

private fun normalizeRun(status: String?): String =
    status?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_').orEmpty()
