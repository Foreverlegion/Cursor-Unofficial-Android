package com.cursorandroid.app.ui.chat

import com.cursorandroid.app.data.api.ModelItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelVendorTest {
    @Test
    fun mapsKnownVendorPrefixes() {
        assertEquals(ModelVendorTab.Cursor, modelVendorForId("composer-2.5"))
        assertEquals(ModelVendorTab.Cursor, modelVendorForId("cursor-fast"))
        assertEquals(ModelVendorTab.Cursor, modelVendorForId("composer"))
        assertEquals(ModelVendorTab.Cursor, modelVendorForId("auto"))
        assertEquals(ModelVendorTab.Anthropic, modelVendorForId("claude-opus-5-5"))
        assertEquals(ModelVendorTab.Anthropic, modelVendorForId("opus"))
        assertEquals(ModelVendorTab.OpenAi, modelVendorForId("gpt-5.6-sol"))
        assertEquals(ModelVendorTab.OpenAi, modelVendorForId("codex-mini"))
        assertEquals(ModelVendorTab.OpenAi, modelVendorForId("o3-mini"))
        assertEquals(ModelVendorTab.Google, modelVendorForId("gemini-4-argon"))
        assertEquals(ModelVendorTab.Xai, modelVendorForId("grok-4.7"))
        assertEquals(ModelVendorTab.DeepSeek, modelVendorForId("deepseek-v3"))
        assertEquals(ModelVendorTab.Moonshot, modelVendorForId("kimi-k2"))
        assertEquals(ModelVendorTab.Zhipu, modelVendorForId("glm-5"))
        assertEquals(ModelVendorTab.Muse, modelVendorForId("muse-wonder-1.0"))
    }

    @Test
    fun unknownPrefixFallsBackToOther() {
        assertEquals(ModelVendorTab.Other, modelVendorForId("totally-new-vendor-9"))
        assertEquals(ModelVendorTab.Other, modelVendorForId(""))
    }

    @Test
    fun pickerTabsHideEmptyRecentAndVendors() {
        val models = listOf(
            ModelItem(id = "composer-2.5", displayName = "Composer"),
            ModelItem(id = "claude-opus-5-5", displayName = "Opus"),
        )
        val tabs = modelPickerTabs(models, recentIds = emptyList())
        assertEquals(listOf("all", "cursor", "anthropic"), tabs.map { it.id })
        assertFalse(tabs.any { it.id == "recent" })
    }

    @Test
    fun pickerTabsIncludeRecentWhenPresent() {
        val models = listOf(ModelItem(id = "composer-2.5", displayName = "Composer"))
        val tabs = modelPickerTabs(models, recentIds = listOf("composer-2.5", "missing"))
        assertEquals("recent", tabs.first().id)
    }

    @Test
    fun searchGroupsByVendor() {
        val models = listOf(
            ModelItem(id = "composer-2.5", displayName = "Composer 2.5"),
            ModelItem(id = "claude-opus-5-5", displayName = "Claude Opus"),
            ModelItem(id = "gpt-5", displayName = "GPT 5"),
        )
        val groups = groupedSearchResults("opus", models)
        assertEquals(1, groups.size)
        assertEquals(ModelVendorTab.Anthropic, groups.first().first)
    }

    @Test
    fun allTabPinsAccountDefaultFirst() {
        val models = listOf(ModelItem(id = "composer-2.5", displayName = "Composer"))
        val rows = modelsForPickerTab(ModelVendorTab.All, models, recentIds = emptyList())
        assertTrue(rows.first() is ModelPickerRow.AccountDefault)
    }
}
