package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.Run

object ImmediateSend {
    /** Moves [targetId] to the front. Null when it is not queued. */
    fun promote(ids: List<String>, targetId: String): List<String>? {
        if (targetId !in ids) return null
        return listOf(targetId) + ids.filterNot { it == targetId }
    }

    /**
     * Cancels the active run, then creates the next one. Cancel is skipped when there is no run id.
     * A cancel failure does not create a run.
     */
    suspend fun cancelThenRun(
        agentId: String,
        activeRunId: String?,
        prompt: Prompt,
        cancel: suspend (agentId: String, runId: String) -> Unit,
        createRun: suspend (agentId: String, prompt: Prompt) -> Run,
    ): Run {
        val runId = activeRunId?.trim()?.takeIf { it.isNotEmpty() }
        if (runId != null) cancel(agentId, runId)
        return createRun(agentId, prompt)
    }
}
