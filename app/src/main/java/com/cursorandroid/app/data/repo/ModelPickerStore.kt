package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.data.api.defaultParams
import com.cursorandroid.app.data.api.modelsVisibleInPicker
import com.cursorandroid.app.ui.chat.ModelVendorTab
import com.cursorandroid.app.ui.chat.modelPickerTabs
import com.cursorandroid.app.ui.chat.modelVendorForId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModelPickerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var lastTabId: String
        get() = prefs.getString(KEY_TAB, ModelVendorTab.All.id) ?: ModelVendorTab.All.id
        set(value) {
            prefs.edit { putString(KEY_TAB, value) }
        }

    fun recentModelIds(): List<String> =
        prefs.getString(KEY_RECENT, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun recordModelPick(modelId: String, tabId: String) {
        lastTabId = tabId
        if (modelId.isBlank()) return
        val next = listOf(modelId) + recentModelIds().filter { it != modelId }
        prefs.edit { putString(KEY_RECENT, next.take(MAX_RECENT).joinToString(",")) }
    }

    fun resolveOpenTab(selectedModelId: String, models: List<ModelItem>): String {
        val recent = recentInCatalog(models)
        val tabs = modelPickerTabs(models, recent).map { it.id }
        fun tabContains(tabId: String, modelId: String): Boolean {
            if (modelId.isBlank()) return tabId == ModelVendorTab.All.id
            return when (tabId) {
                ModelVendorTab.Recent.id -> modelId in recent
                ModelVendorTab.All.id -> true
                else -> modelVendorForId(modelId).id == tabId
            }
        }
        val last = lastTabId
        if (selectedModelId.isNotBlank() && !tabContains(last, selectedModelId)) {
            val preferred = when {
                selectedModelId in recent -> ModelVendorTab.Recent.id
                else -> modelVendorForId(selectedModelId).id
            }
            if (preferred in tabs) return preferred
        }
        if (last in tabs) return last
        if (ModelVendorTab.Recent.id in tabs) return ModelVendorTab.Recent.id
        return ModelVendorTab.All.id
    }

    fun recentInCatalog(models: List<ModelItem>): List<String> {
        val catalog = models.map { it.id }.toSet()
        return recentModelIds().filter { it in catalog }
    }

    fun hiddenFromPicker(): Set<String> =
        prefs.getString(KEY_HIDDEN, null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    fun setHiddenFromPicker(ids: Set<String>) {
        prefs.edit {
            if (ids.isEmpty()) remove(KEY_HIDDEN) else putString(KEY_HIDDEN, ids.sorted().joinToString(","))
        }
    }

    fun pickerModels(catalog: List<ModelItem>, selectedModelId: String): List<ModelItem> =
        modelsVisibleInPicker(catalog, hiddenFromPicker(), selectedModelId)

    fun allParamPrefs(): Map<String, List<ModelParam>> {
        val raw = prefs.getString(KEY_PARAM_PREFS, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<ModelParamPrefsFile>(raw).byModel }.getOrDefault(emptyMap())
    }

    fun replaceParamPrefs(byModel: Map<String, List<ModelParam>>) {
        prefs.edit {
            if (byModel.isEmpty()) {
                remove(KEY_PARAM_PREFS)
            } else {
                putString(KEY_PARAM_PREFS, json.encodeToString(ModelParamPrefsFile(byModel)))
            }
        }
    }

    fun paramsFor(modelId: String, item: ModelItem?): List<ModelParam> {
        if (modelId.isBlank()) return emptyList()
        allParamPrefs()[modelId]?.let { return it }
        return item?.defaultParams().orEmpty()
    }

    fun saveParams(modelId: String, params: List<ModelParam>) {
        if (modelId.isBlank()) return
        val next = allParamPrefs().toMutableMap()
        if (params.isEmpty()) {
            next.remove(modelId)
        } else {
            next[modelId] = params
        }
        replaceParamPrefs(next)
    }

    companion object {
        const val MAX_RECENT = 5
        private const val PREFS = "cursor_model_picker"
        private const val KEY_TAB = "last_tab"
        private const val KEY_RECENT = "recent_ids"
        private const val KEY_HIDDEN = "hidden_from_picker"
        private const val KEY_PARAM_PREFS = "param_prefs"
    }
}

@Serializable
private data class ModelParamPrefsFile(
    val byModel: Map<String, List<ModelParam>> = emptyMap(),
)
