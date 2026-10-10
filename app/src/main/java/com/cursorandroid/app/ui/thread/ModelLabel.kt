package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelSelection

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
