package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.isCreatingStatus
import com.cursorandroid.app.data.api.isRemoteEnvType
import com.cursorandroid.app.data.repo.TranscriptLine

internal fun showWorkBar(
    lines: List<TranscriptLine>,
    receiving: Boolean,
    busy: Boolean,
    agentStatus: String?,
    runStatus: String?,
): Boolean {
    if (receiving || busy) return true
    if (isCreatingStatus(agentStatus) || isCreatingStatus(runStatus)) return true
    val lastUser = lines.indexOfLast { it.kind == "user" }
    if (lastUser < 0) return false
    val lastAssistant = lines.indexOfLast { it.kind == "assistant" }
    return lastUser > lastAssistant
}

internal fun waitCopy(
    receiving: Boolean,
    agentStatus: String?,
    runStatus: String?,
    envType: String?,
): String? {
    if (receiving) return null
    val creating = isCreatingStatus(agentStatus) || isCreatingStatus(runStatus)
    val live = creating || agentStatus.equals("ACTIVE", ignoreCase = true)
    if (!live) return null
    return if (isRemoteEnvType(envType)) {
        "Waiting for the PC. Keep Cursor open with Remote Control."
    } else {
        "Starting this run."
    }
}
