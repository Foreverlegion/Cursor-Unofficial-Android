package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.isCreatingStatus
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.repo.TranscriptLine
import com.cursorandroid.app.ui.status.RunIndicator
import com.cursorandroid.app.ui.status.runIndicator

/** One answer to "is a run going" for the header, work bar, typing bubble and the Kill process item. */
internal data class ThreadRunState(
    val active: Boolean,
    val indicator: RunIndicator,
)

internal fun threadRunState(
    lines: List<TranscriptLine>,
    agentStatus: String?,
    runStatus: String?,
    busy: Boolean,
    streaming: Boolean,
    receiving: Boolean,
    approvalPending: Boolean,
): ThreadRunState {
    val statusLive = isLiveStatus(agentStatus) || isLiveStatus(runStatus) ||
        isCreatingStatus(agentStatus) || isCreatingStatus(runStatus)
    val statusKnown = !agentStatus.isNullOrBlank() || !runStatus.isNullOrBlank()
    val active = busy || streaming || receiving || statusLive || approvalPending ||
        (!statusKnown && lastUserUnanswered(lines))
    val base = runIndicator(agentStatus, runStatus, approvalPending)
    val indicator = if (active && base != RunIndicator.NeedsApproval) RunIndicator.Running else base
    return ThreadRunState(active, indicator)
}

private fun lastUserUnanswered(lines: List<TranscriptLine>): Boolean {
    val lastUser = lines.indexOfLast { it.kind == "user" }
    if (lastUser < 0) return false
    return lastUser > lines.indexOfLast { it.kind == "assistant" }
}
