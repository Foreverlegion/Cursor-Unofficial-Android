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

private val HINT_LINE = Regex("^\\s*model\\s*:\\s*(\\S.*?)\\s*$", RegexOption.IGNORE_CASE)
private val CLAUDE_ID = Regex("^claude-(opus|sonnet|haiku)-(\\d+)-(\\d+)$", RegexOption.IGNORE_CASE)
private const val MAX_HINT = 120

/** `Model: <name>` on the first line of a prompt. Known ids get a friendly name, anything else is kept as written. */
internal fun promptModelHint(text: String?): String? {
    val first = text?.lineSequence()?.firstOrNull() ?: return null
    val name = HINT_LINE.matchEntire(first)?.groupValues?.get(1)?.take(MAX_HINT) ?: return null
    return friendlyHintName(name)
}

internal fun friendlyHintName(name: String): String {
    val claude = CLAUDE_ID.matchEntire(name.trim()) ?: return name
    val (family, major, minor) = claude.destructured
    return "${family.lowercase().replaceFirstChar { it.uppercase() }} $major.$minor"
}

/** The hint from each run's first user message, keyed by run id. */
internal fun runModelHints(lines: List<TranscriptLine>): Map<String, String> {
    val out = HashMap<String, String>()
    val seen = HashSet<String>()
    for (line in lines) {
        if (line.kind != "user") continue
        val run = line.runId?.takeIf { it.isNotBlank() }
            ?: line.id.takeIf { it.startsWith("user-") && !it.startsWith("user-local-") }?.removePrefix("user-")
            ?: continue
        if (!seen.add(run)) continue
        promptModelHint(line.text)?.let { out[run] = it }
    }
    return out
}

/** API field, then the model recorded at send time, then the prompt hint, then the agent's model. */
internal fun runModelLabel(
    book: RunModelBook,
    runId: String?,
    hints: Map<String, String>,
    catalog: List<ModelItem>,
): String? {
    book.forRun(runId)?.let { return modelLabel(it, catalog) }
    runId?.let { hints[it] }?.let { return it }
    return modelLabel(book.fallback(runId), catalog)
}
