package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.repo.TranscriptLine

internal fun showWorkBar(
    lines: List<TranscriptLine>,
    receiving: Boolean,
    busy: Boolean,
    agentStatus: String?,
    runStatus: String?,
): Boolean {
    if (receiving || busy) return true
    if (isCreating(agentStatus) || isCreating(runStatus)) return true
    val lastUser = lines.indexOfLast { it.kind == "user" }
    if (lastUser < 0) return false
    val lastAssistant = lines.indexOfLast { it.kind == "assistant" }
    return lastUser > lastAssistant
}

private fun isCreating(status: String?): Boolean =
    status.equals("CREATING", ignoreCase = true)
