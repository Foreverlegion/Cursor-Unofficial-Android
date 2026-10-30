package com.cursorandroid.app.data.api

/** Cursor Router on Teams/Enterprise (`auto-smart`). Legacy `auto` is a different model. */
const val ROUTER_MODEL_ID = "auto-smart"

const val OPTIMIZE_FOR_PARAM = "optimize_for"

/** Chip label for a catalog parameter value; maps Router `balanced` to Balance. */
fun modelParamChipLabel(paramId: String, value: String, catalogLabel: String?): String {
    catalogLabel?.takeIf { it.isNotBlank() }?.let { return it }
    if (paramId == OPTIMIZE_FOR_PARAM && value.equals("balanced", ignoreCase = true)) return "Balance"
    if (paramId == OPTIMIZE_FOR_PARAM && value.equals("intelligence", ignoreCase = true)) return "Intelligence"
    if (paramId == OPTIMIZE_FOR_PARAM && value.equals("cost", ignoreCase = true)) return "Cost"
    return value.replace('_', ' ').replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
    }
}

fun routerInCatalog(catalog: List<ModelItem>): Boolean =
    catalog.any { it.id == ROUTER_MODEL_ID }

/** Picker list with device-hidden models removed; keeps [keepSelectedId] visible when selected. */
fun modelsVisibleInPicker(
    catalog: List<ModelItem>,
    hiddenIds: Set<String>,
    keepSelectedId: String = "",
): List<ModelItem> {
    if (hiddenIds.isEmpty()) return catalog
    return catalog.filter { it.id !in hiddenIds || it.id == keepSelectedId }
}

fun routerOptimizeLabel(selection: ModelSelection?, catalog: List<ModelItem>): String? {
    if (selection?.id != ROUTER_MODEL_ID) return null
    val param = selection.params.orEmpty().firstOrNull { it.id == OPTIMIZE_FOR_PARAM } ?: return null
    val item = catalog.firstOrNull { it.id == ROUTER_MODEL_ID }
    val catalogLabel = item?.parameters.orEmpty()
        .firstOrNull { it.id == OPTIMIZE_FOR_PARAM }
        ?.values.orEmpty()
        .firstOrNull { it.value == param.value }
        ?.displayName
    return modelParamChipLabel(OPTIMIZE_FOR_PARAM, param.value, catalogLabel)
}
