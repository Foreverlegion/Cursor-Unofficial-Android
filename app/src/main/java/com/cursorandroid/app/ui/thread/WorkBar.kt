package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.isCreatingStatus
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.api.isRemoteEnvType

internal fun waitCopy(
    receiving: Boolean,
    agentStatus: String?,
    runStatus: String?,
    envType: String?,
): String? {
    if (receiving) return null
    if (!isLiveStatus(agentStatus) && !isLiveStatus(runStatus) &&
        !isCreatingStatus(agentStatus) && !isCreatingStatus(runStatus)
    ) {
        return null
    }
    return if (isRemoteEnvType(envType)) {
        "Waiting for the PC. Keep Cursor open with Remote Control."
    } else {
        "Starting this run."
    }
}

internal fun workActivityLine(
    receiving: Boolean,
    agentStatus: String?,
    runStatus: String?,
    envType: String?,
    toolName: String?,
): String {
    val detail = when {
        !toolName.isNullOrBlank() -> toolName
        receiving -> "writing"
        isRemoteEnvType(envType) &&
            (isLiveStatus(agentStatus) || isLiveStatus(runStatus) ||
                isCreatingStatus(agentStatus) || isCreatingStatus(runStatus)) -> "waiting for the PC"
        isCreatingStatus(agentStatus) || isCreatingStatus(runStatus) -> "starting"
        else -> "…"
    }
    return "Agent working · $detail"
}
