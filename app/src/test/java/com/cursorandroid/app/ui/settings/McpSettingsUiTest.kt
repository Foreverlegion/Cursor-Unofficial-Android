package com.cursorandroid.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.repo.UiPrefsStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h900dp-mdpi")
class McpSettingsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var ui: UiPrefsStore
    private lateinit var store: ApiKeyStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("cursor_secure", "cursor_secure_bak", "cursor_secure_fallback", "cursor_prefs", "local_chats")
            .forEach { context.deleteSharedPreferences(it) }
        ui = UiPrefsStore.open(context)
        store = ApiKeyStore(context, ui) { c, name -> c.getSharedPreferences(name, Context.MODE_PRIVATE) }
    }

    @After
    fun tearDown() {
        ui.close()
    }

    @Test
    fun fromCursorIsShownButDisabled() {
        compose.setContent { McpListSection(store) }
        compose.onNodeWithText("Import").performClick()
        compose.onNodeWithText("From mcp.json").assertExists()
        compose.onNodeWithText("From Cursor").assertIsNotEnabled()
    }

    @Test
    fun presetListShowsEveryPresetWithItsUrl() {
        compose.setContent { McpListSection(store) }
        compose.onNodeWithText("Presets").performClick()
        listOf("GitHub", "Context7", "Cloudflare", "Cloudflare Docs", "Postman", "GitLab").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("https://mcp.postman.com/minimal").assertExists()
        compose.onNodeWithText("https://gitlab.com/api/v4/mcp  (OAuth)").assertExists()
    }

    @Test
    fun theListExplainsWhyThereIsNoImportFromCursor() {
        compose.setContent { McpListSection(store) }
        compose.onNode(hasText("Cursor has no API that lists the MCP servers", substring = true)).assertExists()
    }
}
