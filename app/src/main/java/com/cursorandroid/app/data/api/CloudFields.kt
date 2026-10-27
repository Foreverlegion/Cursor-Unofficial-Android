package com.cursorandroid.app.data.api

import java.util.Locale

const val MAX_ENV_VARS = 50
const val MAX_PROMPT_IMAGES = 5
const val MAX_PROMPT_IMAGE_BYTES = 15L * 1024L * 1024L

private val IMAGE_MIME = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

fun defaultOpenAsCursorGithubApp(me: MeResponse?): Boolean {
    return me != null && me.userId == null
}

fun envVarMap(rows: List<Pair<String, String>>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    rows.forEach { (key, value) ->
        val name = key.trim()
        if (name.isNotEmpty()) out[name] = value
    }
    return out
}

fun envVarsError(vars: Map<String, String>?): String? {
    if (vars.isNullOrEmpty()) return null
    if (vars.size > MAX_ENV_VARS) return "At most $MAX_ENV_VARS env vars."
    for ((name, value) in vars) {
        if (name.isBlank()) return "Env var name is empty."
        if (name.startsWith("CURSOR_")) return "Env var names cannot start with CURSOR_."
        if (name.toByteArray(Charsets.UTF_8).size > 255) return "Env var name is too long."
        if (value.toByteArray(Charsets.UTF_8).size > 4096) return "Env var value is too long."
    }
    return null
}

fun isCommitSha(ref: String?): Boolean {
    val s = ref?.trim().orEmpty()
    if (s.length !in 7..40) return false
    return s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
}

/**
 * startingRef is dropped when prUrl is set. A commit SHA is sent only for the default branch,
 * and never when workOnCurrentBranch is set (a SHA is invalid there).
 */
fun resolveStartingRef(
    branch: String?,
    commitSha: String?,
    workOnCurrentBranch: Boolean,
    prUrl: String?,
    defaultBranch: String?,
): String? {
    if (!prUrl.isNullOrBlank()) return null
    val name = branch?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (workOnCurrentBranch) return name.takeUnless { isCommitSha(it) }
    if (isCommitSha(name)) return name
    val onDefault = !defaultBranch.isNullOrBlank() && name.equals(defaultBranch, ignoreCase = true)
    val sha = commitSha?.trim()?.takeIf { isCommitSha(it) }
    if (onDefault && sha != null) return sha
    return name
}

fun startingRefError(branch: String?, workOnCurrentBranch: Boolean, prUrl: String?): String? {
    if (!prUrl.isNullOrBlank()) return null
    val name = branch?.trim().orEmpty()
    if (workOnCurrentBranch && isCommitSha(name)) {
        return "A commit SHA cannot be used when committing on the current branch."
    }
    return null
}

fun normalizeRepoUrl(url: String?): String? {
    val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (raw.startsWith("https://") || raw.startsWith("http://") || raw.startsWith("ssh://") || raw.startsWith("git@")) {
        return raw
    }
    return "https://$raw"
}

fun officialImageMime(mime: String?): String? {
    val key = mime?.trim()?.lowercase() ?: return null
    val normal = if (key == "image/jpg" || key == "image/pjpeg") "image/jpeg" else key
    return normal.takeIf { it in IMAGE_MIME }
}

fun sanitizePromptImages(images: List<PromptImage>?): List<PromptImage>? {
    if (images.isNullOrEmpty()) return null
    val out = ArrayList<PromptImage>(MAX_PROMPT_IMAGES)
    for (image in images) {
        if (out.size >= MAX_PROMPT_IMAGES) break
        val url = image.url?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (!url.isNullOrBlank() && image.data.isNullOrBlank()) {
            out += PromptImage(url = url, dimension = image.dimension?.takeIf { it.width > 0 && it.height > 0 })
            continue
        }
        val mime = officialImageMime(image.mimeType) ?: continue
        val data = image.data?.takeIf { it.isNotBlank() } ?: continue
        val bytes = (data.length.toLong() * 3L) / 4L
        if (bytes > MAX_PROMPT_IMAGE_BYTES) continue
        val dim = image.dimension?.takeIf { it.width > 0 && it.height > 0 }
        out += PromptImage(data = data, mimeType = mime, dimension = dim)
    }
    return out.takeIf { it.isNotEmpty() }
}

fun formatUsdFromCents(cents: Double): String {
    return String.format(Locale.US, "$%.2f", cents / 100.0)
}

fun UsageCost.dollarLine(): String? {
    val charged = chargedCents
    val raw = rawCostCents
    if (charged == null && raw == null) return null
    return when {
        charged != null && raw != null -> "${formatUsdFromCents(charged)} charged · ${formatUsdFromCents(raw)} raw"
        charged != null -> formatUsdFromCents(charged)
        else -> formatUsdFromCents(raw!!)
    }
}

fun AgentUsageResponse.costLine(runId: String? = null): String? {
    cost?.dollarLine()?.let { return it }
    val match = runs.firstOrNull { !runId.isNullOrBlank() && it.id == runId } ?: runs.firstOrNull { it.cost != null }
    return match?.cost?.dollarLine()
}

fun retainEventId(current: String?, incoming: String?): String? {
    return incoming?.trim()?.takeIf { it.isNotEmpty() } ?: current
}

fun parseRetentionSeconds(header: String?): Long? {
    val raw = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return raw.toLongOrNull()?.takeIf { it >= 0 }
}

fun conflictAgentId(requested: String?, body: String?): String? {
    requested?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return Regex("""bc-[0-9a-fA-F-]{8,}""").find(body.orEmpty())?.value
}

fun queueInsteadOfFail(error: ApiException): Boolean = error.isBusy

/**
 * SDK fields the public create schema may still reject. A 400 that names the field as unknown
 * turns the gate off so the next attempt omits it.
 */
object CloudFieldGate {
    @Volatile var createRunScoped: Boolean = true
    @Volatile var followUpEnv: Boolean = true

    fun rejects(error: ApiException, field: String): Boolean {
        if (error.code != 400) return false
        val text = (error.message + "\n" + error.body.orEmpty()).lowercase()
        if (!text.contains(field.lowercase())) return false
        return text.contains("unknown") ||
            text.contains("unrecognized") ||
            text.contains("additional propert") ||
            text.contains("not allowed") ||
            text.contains("unexpected")
    }
}
