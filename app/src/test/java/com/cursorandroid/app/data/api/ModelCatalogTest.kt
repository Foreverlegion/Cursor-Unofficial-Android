package com.cursorandroid.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    private val router = ModelItem(
        id = ROUTER_MODEL_ID,
        displayName = "Auto",
        parameters = listOf(
            ModelParameter(
                id = OPTIMIZE_FOR_PARAM,
                values = listOf(
                    ModelValue("cost", "Cost"),
                    ModelValue("balanced"),
                    ModelValue("intelligence", "Intelligence"),
                ),
            ),
        ),
    )

    @Test
    fun optimizeForLabelMapsBalancedToBalance() {
        assertEquals("Balance", modelParamChipLabel(OPTIMIZE_FOR_PARAM, "balanced", null))
        assertEquals("Cost", modelParamChipLabel(OPTIMIZE_FOR_PARAM, "cost", null))
        assertEquals("Intelligence", modelParamChipLabel(OPTIMIZE_FOR_PARAM, "intelligence", null))
    }

    @Test
    fun catalogDisplayNameWinsOverMapping() {
        assertEquals("Balanced tier", modelParamChipLabel(OPTIMIZE_FOR_PARAM, "balanced", "Balanced tier"))
    }

    @Test
    fun routerSelectionEncodesParams() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val body = json.encodeToString(
            CreateAgentRequest.serializer(),
            CreateAgentRequest(
                prompt = Prompt("hi"),
                model = router.selection(listOf(ModelParam(OPTIMIZE_FOR_PARAM, "balanced"))),
            ),
        )
        assertTrue(body.contains("\"id\":\"auto-smart\""))
        assertTrue(body.contains("\"optimize_for\""))
        assertTrue(body.contains("\"balanced\""))
    }

    @Test
    fun routerDecodeFromModelsFixture() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val list = json.decodeFromString<ModelListResponse>(
            """{"items":[{"id":"auto-smart","displayName":"Auto","parameters":[{"id":"optimize_for","values":[{"value":"cost","displayName":"Cost"},{"value":"balanced"},{"value":"intelligence","displayName":"Intelligence"}]}]}]}""",
        )
        assertTrue(routerInCatalog(list.items))
        assertEquals(
            listOf(ModelParam(OPTIMIZE_FOR_PARAM, "cost")),
            list.items.single().defaultParams(),
        )
    }

    @Test
    fun pickerHidesUnlessSelected() {
        val catalog = listOf(
            ModelItem(id = "gpt-5"),
            ModelItem(id = "claude-opus-5-5"),
        )
        val hidden = setOf("gpt-5")
        assertEquals(1, modelsVisibleInPicker(catalog, hidden).size)
        assertEquals(2, modelsVisibleInPicker(catalog, hidden, keepSelectedId = "gpt-5").size)
    }

    @Test
    fun routerOptimizeLabelUsesBalance() {
        val sel = ModelSelection(ROUTER_MODEL_ID, listOf(ModelParam(OPTIMIZE_FOR_PARAM, "balanced")))
        assertEquals("Balance", routerOptimizeLabel(sel, listOf(router)))
    }

    @Test
    fun legacyAutoIsNotRouter() {
        assertFalse(routerInCatalog(listOf(ModelItem(id = "auto"))))
    }
}
