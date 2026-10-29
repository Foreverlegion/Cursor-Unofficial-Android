package com.cursorandroid.app.ui.chat

import com.cursorandroid.app.data.api.ModelItem

enum class ModelVendorTab(val id: String, val label: String, private val rank: Int) {
    Recent("recent", "Recent", -2),
    All("all", "All", -1),
    Cursor("cursor", "Cursor", 0),
    Anthropic("anthropic", "Anthropic", 1),
    OpenAi("openai", "OpenAI", 2),
    Google("google", "Google", 3),
    Xai("xai", "xAI", 4),
    DeepSeek("deepseek", "DeepSeek", 5),
    Moonshot("moonshot", "Moonshot", 6),
    Zhipu("zhipu", "Zhipu", 7),
    Muse("muse", "Muse", 8),
    Other("other", "Other", 99),
    ;

    companion object {
        val catalogVendors: List<ModelVendorTab> =
            entries.filter { it != Recent && it != All }.sortedBy { it.rank }

        fun fromId(id: String): ModelVendorTab? = entries.firstOrNull { it.id == id }
    }
}

/** Maps a model id prefix to a vendor tab. Unknown ids land in [ModelVendorTab.Other]. */
fun modelVendorForId(modelId: String): ModelVendorTab {
    val id = modelId.trim().lowercase()
    if (id.isEmpty()) return ModelVendorTab.Other
    return when {
        id.startsWith("composer-") || id.startsWith("cursor-") || id in cursorBareIds -> ModelVendorTab.Cursor
        id.startsWith("claude-") || id in anthropicAliases -> ModelVendorTab.Anthropic
        id.startsWith("gpt-") || id.startsWith("codex") || openAiOSeries(id) -> ModelVendorTab.OpenAi
        id.startsWith("gemini-") -> ModelVendorTab.Google
        id.startsWith("grok-") -> ModelVendorTab.Xai
        id.startsWith("deepseek-") -> ModelVendorTab.DeepSeek
        id.startsWith("kimi-") -> ModelVendorTab.Moonshot
        id.startsWith("glm-") -> ModelVendorTab.Zhipu
        id.startsWith("muse-") -> ModelVendorTab.Muse
        else -> ModelVendorTab.Other
    }
}

private val anthropicAliases = setOf("opus", "sonnet", "haiku")

/** Bare ids (no hyphen) that still belong to Cursor / Auto in the catalog. */
private val cursorBareIds = setOf("composer", "auto", "cursor", "default")

private val openAiOPrefix = Regex("^o\\d")

private fun openAiOSeries(id: String): Boolean = openAiOPrefix.containsMatchIn(id)

fun modelPickerTabs(models: List<ModelItem>, recentIds: List<String>): List<ModelVendorTab> {
    val catalog = models.map { it.id }.toSet()
    val recent = recentIds.filter { it in catalog }
    val tabs = mutableListOf<ModelVendorTab>()
    if (recent.isNotEmpty()) tabs += ModelVendorTab.Recent
    tabs += ModelVendorTab.All
    ModelVendorTab.catalogVendors.forEach { vendor ->
        if (models.any { modelVendorForId(it.id) == vendor }) tabs += vendor
    }
    return tabs
}

fun modelsMatchingSearch(query: String, models: List<ModelItem>): List<ModelItem> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return models.filter { model -> modelMatchesQuery(model, q) }
}

fun groupedSearchResults(query: String, models: List<ModelItem>): List<Pair<ModelVendorTab, List<ModelItem>>> {
    val matched = modelsMatchingSearch(query, models)
    if (matched.isEmpty()) return emptyList()
    return ModelVendorTab.catalogVendors.mapNotNull { vendor ->
        val group = matched.filter { modelVendorForId(it.id) == vendor }
            .sortedBy { it.displayName ?: it.id }
        if (group.isEmpty()) null else vendor to group
    }
}

internal fun modelMatchesQuery(model: ModelItem, query: String): Boolean {
    val q = query.lowercase()
    if (model.id.lowercase().contains(q)) return true
    if (model.displayName?.lowercase()?.contains(q) == true) return true
    if (model.description?.lowercase()?.contains(q) == true) return true
    if (model.aliases.orEmpty().any { it.lowercase().contains(q) }) return true
    return false
}

fun modelsForPickerTab(
    tab: ModelVendorTab,
    models: List<ModelItem>,
    recentIds: List<String>,
): List<ModelPickerRow> {
    val catalog = models.associateBy { it.id }
    return when (tab) {
        ModelVendorTab.All -> {
            listOf(ModelPickerRow.AccountDefault) +
                models.sortedBy { it.displayName ?: it.id }.map { ModelPickerRow.Model(it) }
        }
        ModelVendorTab.Recent ->
            recentIds.mapNotNull { id -> catalog[id]?.let { ModelPickerRow.Model(it) } }
        else ->
            models.filter { modelVendorForId(it.id) == tab }
                .sortedBy { it.displayName ?: it.id }
                .map { ModelPickerRow.Model(it) }
    }
}

sealed class ModelPickerRow {
    data object AccountDefault : ModelPickerRow()
    data class Model(val item: ModelItem) : ModelPickerRow()
}
