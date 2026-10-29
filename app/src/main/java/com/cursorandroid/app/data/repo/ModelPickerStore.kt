package com.cursorandroid.app.data.repo

import android.content.Context
import androidx.core.content.edit
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.ui.chat.ModelVendorTab
import com.cursorandroid.app.ui.chat.modelPickerTabs
import com.cursorandroid.app.ui.chat.modelVendorForId

class ModelPickerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

    companion object {
        const val MAX_RECENT = 5
        private const val PREFS = "cursor_model_picker"
        private const val KEY_TAB = "last_tab"
        private const val KEY_RECENT = "recent_ids"
    }
}
