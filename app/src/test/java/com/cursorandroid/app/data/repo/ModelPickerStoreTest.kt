package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.data.api.OPTIMIZE_FOR_PARAM
import com.cursorandroid.app.data.api.ROUTER_MODEL_ID
import com.cursorandroid.app.ui.chat.ModelVendorTab
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ModelPickerStoreTest {
    private lateinit var context: Context
    private lateinit var store: ModelPickerStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("cursor_model_picker", Context.MODE_PRIVATE).edit().clear().commit()
        store = ModelPickerStore(context)
    }

    @Test
    fun recentListKeepsNewestFiveDistinct() {
        repeat(7) { store.recordModelPick("model-$it", ModelVendorTab.All.id) }
        assertEquals(
            listOf("model-6", "model-5", "model-4", "model-3", "model-2"),
            store.recentModelIds(),
        )
    }

    @Test
    fun recentPromotesReusedModel() {
        store.recordModelPick("a", ModelVendorTab.All.id)
        store.recordModelPick("b", ModelVendorTab.All.id)
        store.recordModelPick("c", ModelVendorTab.All.id)
        store.recordModelPick("a", ModelVendorTab.Anthropic.id)
        assertEquals(listOf("a", "c", "b"), store.recentModelIds())
    }

    @Test
    fun blankPickDoesNotAddRecent() {
        store.recordModelPick("", ModelVendorTab.All.id)
        assertEquals(emptyList<String>(), store.recentModelIds())
        assertEquals(ModelVendorTab.All.id, store.lastTabId)
    }

    @Test
    fun openTabPrefersTabContainingSelectedModel() {
        store.lastTabId = ModelVendorTab.Cursor.id
        val models = listOf(
            ModelItem(id = "composer-2.5"),
            ModelItem(id = "claude-opus-5-5"),
        )
        assertEquals(
            ModelVendorTab.Anthropic.id,
            store.resolveOpenTab("claude-opus-5-5", models),
        )
    }

    @Test
    fun openTabUsesRecentWhenModelWasRecent() {
        store.recordModelPick("composer-2.5", ModelVendorTab.All.id)
        store.lastTabId = ModelVendorTab.Anthropic.id
        val models = listOf(
            ModelItem(id = "composer-2.5"),
            ModelItem(id = "claude-opus-5-5"),
        )
        assertEquals(ModelVendorTab.Recent.id, store.resolveOpenTab("composer-2.5", models))
    }

    @Test
    fun paramPrefsPersistPerModel() {
        val router = ModelItem(
            id = ROUTER_MODEL_ID,
            parameters = listOf(
                com.cursorandroid.app.data.api.ModelParameter(
                    id = OPTIMIZE_FOR_PARAM,
                    values = listOf(
                        com.cursorandroid.app.data.api.ModelValue("cost"),
                        com.cursorandroid.app.data.api.ModelValue("balanced"),
                    ),
                ),
            ),
        )
        store.saveParams(ROUTER_MODEL_ID, listOf(ModelParam(OPTIMIZE_FOR_PARAM, "balanced")))
        assertEquals(
            listOf(ModelParam(OPTIMIZE_FOR_PARAM, "balanced")),
            store.paramsFor(ROUTER_MODEL_ID, router),
        )
        val again = ModelPickerStore(context)
        assertEquals(
            listOf(ModelParam(OPTIMIZE_FOR_PARAM, "balanced")),
            again.paramsFor(ROUTER_MODEL_ID, router),
        )
    }

    @Test
    fun hiddenModelsFilterPickerList() {
        val catalog = listOf(ModelItem(id = "a"), ModelItem(id = "b"))
        store.setHiddenFromPicker(setOf("a"))
        assertEquals(listOf("b"), store.pickerModels(catalog, "").map { it.id })
        assertEquals(listOf("a", "b"), store.pickerModels(catalog, "a").map { it.id })
    }
}
