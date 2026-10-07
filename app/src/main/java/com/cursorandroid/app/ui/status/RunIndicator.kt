package com.cursorandroid.app.ui.status

import com.cursorandroid.app.data.api.gitPath
import com.cursorandroid.app.data.api.isCreatingStatus
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.api.isRemoteEnvType
import com.cursorandroid.app.data.notify.ApprovalCopy
import java.time.Instant

enum class RunIndicator {
    Done,
    Running,
    NeedsApproval,
    Failed,
    ;

    val label: String
        get() = when (this) {
            Done -> "Done"
            Running -> "Running"
            NeedsApproval -> "Needs approval"
            Failed -> "Failed"
        }
}

private val FAILED = setOf(
    "ERROR",
    "FAILED",
    "FAILURE",
    "EXPIRED",
    "CANCELLED",
    "CANCELED",
)

private val APPROVAL = setOf(
    "NEEDS_APPROVAL",
    "AWAITING_APPROVAL",
    "WAITING_FOR_APPROVAL",
    "APPROVAL",
    "USER_APPROVAL",
    "REQUIRES_APPROVAL",
)

fun runIndicator(
    agentStatus: String?,
    runStatus: String? = null,
    approvalPending: Boolean = false,
): RunIndicator {
    if (approvalPending) return RunIndicator.NeedsApproval
    val raw = listOfNotNull(agentStatus, runStatus).filter { it.isNotBlank() }
    if (raw.any { isApprovalStatus(it) }) return RunIndicator.NeedsApproval
    if (raw.any { isLiveStatus(it) || isCreatingStatus(it) }) return RunIndicator.Running
    if (raw.any { normalizeStatus(it) in FAILED }) return RunIndicator.Failed
    return RunIndicator.Done
}

fun isApprovalStatus(status: String?): Boolean {
    val key = normalizeStatus(status)
    if (key.isEmpty()) return false
    if (key in APPROVAL) return true
    return ApprovalCopy.isPending(status)
}

fun normalizeStatus(status: String?): String {
    return status?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_').orEmpty()
}

fun envLabel(type: String?): String {
    return when (type?.trim()?.lowercase()) {
        null, "", "cloud" -> "Cloud"
        "pool" -> "Pool"
        "machine", "local", "remote" -> "Remote"
        else -> type.trim().replaceFirstChar { it.uppercase() }
    }
}

fun shortRepo(url: String?): String? {
    val raw = url?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val path = gitPath(raw).trim('/').ifBlank { return null }
    if (path.equals(raw, ignoreCase = true) && !path.contains('/')) return path
    return path
}

fun agentCardSubtitle(envType: String?, envName: String?, repoUrl: String?): String {
    val env = envLabel(envType)
    val named = envName?.trim()?.takeIf { it.isNotEmpty() && !it.equals(env, ignoreCase = true) }
    val repo = shortRepo(repoUrl) ?: named
    return listOfNotNull(repo, env).joinToString(" · ")
}

fun threadSubtitle(repo: String?, indicator: RunIndicator): String {
    val name = repo?.trim()?.takeIf { it.isNotEmpty() }
    return if (name == null) indicator.label else "$name · ${indicator.label}"
}

fun relativeAge(iso: String?, nowMillis: Long = System.currentTimeMillis()): String {
    if (iso.isNullOrBlank()) return ""
    val then = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
    val sec = ((nowMillis - then) / 1000L).coerceAtLeast(0L)
    return when {
        sec < 45 -> "now"
        sec < 3600 -> "${sec / 60}m ago"
        sec < 86_400 -> "${sec / 3600}h"
        sec < 86_400L * 14 -> "${sec / 86_400}d"
        else -> "${sec / (86_400L * 7)}w"
    }
}

fun toolPath(args: String?): String? {
    val fields = ApprovalCopy.parseArgs(args)
    if (fields.isEmpty()) return null
    val keys = listOf(
        "path",
        "file",
        "file_path",
        "target_file",
        "target_directory",
        "directory",
        "dir",
    )
    keys.forEach { key ->
        fields[key]?.takeIf { it.isNotBlank() }?.let { return it }
    }
    val lower = fields.mapKeys { it.key.lowercase() }
    keys.forEach { key ->
        lower[key]?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

fun toolCallText(name: String?, args: String?): String {
    val tool = name?.trim()?.takeIf { it.isNotEmpty() } ?: "tool"
    val path = toolPath(args)?.trim()?.takeIf { it.isNotEmpty() } ?: return tool
    return "$tool\n$path"
}

fun toolCallParts(text: String): Pair<String, String?> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return "tool" to null
    if (lines.size >= 2) return lines.first() to displayPath(lines.drop(1).joinToString(" "))
    val raw = lines.first()
    val name = raw.substringBefore(' ').ifBlank { raw }
    return name to null
}

fun displayPath(path: String): String {
    val clean = path.trim()
    if (clean.length <= 42) return clean
    val file = clean.substringAfterLast('/')
    val head = clean.substringBefore('/')
    if (head.isNotEmpty() && file.isNotEmpty() && head != file && head.length + file.length <= 36) {
        return "$head/.../$file"
    }
    return clean.take(18) + "..." + clean.takeLast(18)
}

fun isRemoteThread(type: String?): Boolean = isRemoteEnvType(type)
