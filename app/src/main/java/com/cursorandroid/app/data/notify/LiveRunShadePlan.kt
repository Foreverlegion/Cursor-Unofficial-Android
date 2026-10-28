package com.cursorandroid.app.data.notify

/** Stable group for run, live, and completion notifications from this app. */
const val AGENT_RUN_NOTIFICATION_GROUP = "cursor_agent_runs"

const val AGENT_RUN_SUMMARY_TAG = "cursor_agent_runs_summary"

fun agentRunSummarySlot(): LiveNotificationSlot = LiveNotificationSlot(
    tag = AGENT_RUN_SUMMARY_TAG,
    id = stableNotificationId("cursor_agent_runs_summary"),
)

data class RunningAgentShadeRow(
    val agentId: String,
    val runId: String,
    val title: String,
    val body: String,
    val shortCriticalText: String,
    val lastActiveMs: Long,
)

data class LiveRunShadePlan(
    val promotedAgentIds: List<String>,
    val groupedRows: List<RunningAgentShadeRow>,
    val summaryLineCount: Int,
) {
    val showsSummary: Boolean get() = summaryLineCount > 0
}

/**
 * Picks up to [maxPromoted] agents for ungrouped promoted live updates (most recently active first).
 * The rest stay in [groupedRows]. Summary covers grouped rows only.
 */
fun planLiveRunShade(
    running: List<RunningAgentShadeRow>,
    maxPromoted: Int,
    liveStatusEnabled: Boolean,
    promotionAllowed: Boolean,
): LiveRunShadePlan {
    if (running.isEmpty()) {
        return LiveRunShadePlan(emptyList(), emptyList(), 0)
    }
    val sorted = running.sortedByDescending { it.lastActiveMs }
    if (!liveStatusEnabled) {
        return LiveRunShadePlan(
            promotedAgentIds = emptyList(),
            groupedRows = sorted,
            summaryLineCount = sorted.size,
        )
    }
    val cap = if (promotionAllowed) clampMaxLivePromoted(maxPromoted) else 0
    val promoted = sorted.take(cap).map { it.agentId }
    val promotedSet = promoted.toSet()
    val grouped = sorted.filter { it.agentId !in promotedSet }
    return LiveRunShadePlan(
        promotedAgentIds = promoted,
        groupedRows = grouped,
        summaryLineCount = grouped.size,
    )
}

fun clampMaxLivePromoted(value: Int): Int = value.coerceIn(MIN_MAX_LIVE_PROMOTED, MAX_MAX_LIVE_PROMOTED)

const val MIN_MAX_LIVE_PROMOTED = 1
const val MAX_MAX_LIVE_PROMOTED = 15
const val DEFAULT_MAX_LIVE_PROMOTED = 2
