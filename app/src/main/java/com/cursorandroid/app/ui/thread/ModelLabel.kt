package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelSelection
import com.cursorandroid.app.data.repo.RunModelBook
import com.cursorandroid.app.data.repo.TranscriptLine

private val EFFORT_PARAMS = setOf("effort", "reasoning", "reasoning_effort", "reasoningeffort", "thinking")

internal fun modelName(selection: ModelSelection?, catalog: List<ModelItem>): String? {
    val id = selection?.id?.trim().orEmpty()
    if (id.isEmpty()) return null
    val item = catalog.firstOrNull { it.id == id }
        ?: catalog.firstOrNull { entry -> entry.aliases.orEmpty().any { it == id } }
    return item?.displayName?.takeIf { it.isNotBlank() } ?: id
}

internal fun modelEffort(selection: ModelSelection?, catalog: List<ModelItem>): String? {
    val params = selection?.params.orEmpty()
    val param = params.firstOrNull { it.id.lowercase() in EFFORT_PARAMS && it.value.isNotBlank() } ?: return null
    val item = catalog.firstOrNull { it.id == selection?.id }
        ?: catalog.firstOrNull { entry -> entry.aliases.orEmpty().any { it == selection?.id } }
    val shown = item?.parameters.orEmpty()
        .firstOrNull { it.id == param.id }
        ?.values.orEmpty()
        .firstOrNull { it.value == param.value }
        ?.displayName
    return shown?.takeIf { it.isNotBlank() } ?: param.value
}

/** Friendly model text such as "Claude Opus 5.5 · high", or null when the model is unknown. */
internal fun modelLabel(selection: ModelSelection?, catalog: List<ModelItem>): String? {
    val name = modelName(selection, catalog) ?: return null
    val effort = modelEffort(selection, catalog)
    return if (effort == null) name else "$name · $effort"
}

internal fun senderLabel(model: String?): String = if (model.isNullOrBlank()) "Agent" else "Agent · $model"

private val HINT_LINE = Regex("^model\\s*:\\s*(\\S.*?)$", RegexOption.IGNORE_CASE)
private val CLAUDE_ID = Regex("^claude-(opus|sonnet|haiku)-(\\d+)-(\\d+)$", RegexOption.IGNORE_CASE)
private val DECORATION = Regex("^[\\s>*_#`\\-+]+|[\\s*_`]+$")
private const val MAX_HINT = 120

/**
 * `Model: <name>` on the first non-blank line of a prompt. Leading whitespace, CRLF, quote or list
 * markers, and emphasis around the line (`**Model:** x`) are tolerated. Known ids get a friendly
 * name, anything else is kept as written.
 */
internal fun promptModelHint(text: String?): String? {
    val first = text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: return null
    val plain = first.replace(DECORATION, "").replace("**", "").replace("__", "")
    val name = HINT_LINE.matchEntire(plain)?.groupValues?.get(1)
        ?.replace(DECORATION, "")?.take(MAX_HINT)?.takeIf { it.isNotEmpty() } ?: return null
    return friendlyHintName(name)
}

internal fun friendlyHintName(name: String): String {
    val claude = CLAUDE_ID.matchEntire(name.trim()) ?: return name
    val (family, major, minor) = claude.destructured
    return "${family.lowercase().replaceFirstChar { it.uppercase() }} $major.$minor"
}

private fun userRunId(line: TranscriptLine): String? =
    line.runId?.takeIf { it.isNotBlank() }
        ?: line.id.takeIf { it.startsWith("user-") && !it.startsWith("user-local-") }?.removePrefix("user-")

private fun lineRun(line: TranscriptLine): String? =
    line.runId?.takeIf { it.isNotBlank() }
        ?: line.id.takeIf { it.startsWith("assistant-") || it.startsWith("think-") }
            ?.substringAfter('-')

/**
 * The hint that applies to each non-user line. A run's own prompt hint (from Get Run or List Runs)
 * wins, then the hint on the user message that opened the turn, then the most recent earlier hint.
 * Conversation messages carry no run id, so the turn is found by position.
 */
internal fun lineModelHints(lines: List<TranscriptLine>, runHints: Map<String, String>): Map<String, String> {
    val out = HashMap<String, String>()
    var carried: String? = null
    for (line in lines) {
        if (line.kind == "user") {
            val own = userRunId(line)?.let { runHints[it] } ?: promptModelHint(line.text)
            if (own != null) carried = own
            continue
        }
        val hint = lineRun(line)?.let { runHints[it] } ?: carried
        if (hint != null) out[line.id] = hint
    }
    return out
}

/** The hint for the run that is going now: its own prompt, else the latest hint in the thread. */
internal fun currentModelHint(lines: List<TranscriptLine>, runHints: Map<String, String>, runId: String?): String? {
    runId?.let { runHints[it] }?.let { return it }
    val users = lines.filter { it.kind == "user" }
    users.firstOrNull { runId != null && userRunId(it) == runId }?.let { promptModelHint(it.text) }?.let { return it }
    return users.asReversed().firstNotNullOfOrNull { promptModelHint(it.text) }
        ?: runHints.values.lastOrNull()
}

/** API field, then the model recorded at send time, then the prompt hint, then the agent's model. */
internal fun runModelLabel(
    book: RunModelBook,
    runId: String?,
    hint: String?,
    catalog: List<ModelItem>,
): String? {
    book.forRun(runId)?.let { return modelLabel(it, catalog) }
    hint?.let { return it }
    return modelLabel(book.fallback(runId), catalog)
}
