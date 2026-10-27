package com.cursorandroid.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.repo.McpTemplateTest
import com.cursorandroid.app.data.repo.UiPrefsStore
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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

    @Test
    fun templateButtonSitsNextToImportAndShowsASnackbarWithOpen() {
        val provider = Robolectric.setupContentProvider(McpTemplateTest.FakeMediaProvider::class.java, "media")
        val snackbar = SnackbarHostState()
        compose.setContent {
            Box {
                McpListSection(store, snackbar)
                SnackbarHost(snackbar)
            }
        }
        compose.onNodeWithText("Import").assertExists()
        compose.onNodeWithText("Create MCP sheet for import").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Saved mcp-template.json to Downloads")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Open").assertExists()
        assertEquals(listOf("mcp-template.json"), provider.names())
    }

    @Test
    fun asecondTemplateGetsANumberedName() {
        val provider = Robolectric.setupContentProvider(McpTemplateTest.FakeMediaProvider::class.java, "media")
        compose.setContent { McpListSection(store) }
        compose.onNodeWithText("Create MCP sheet for import").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Saved mcp-template.json to Downloads")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Create MCP sheet for import").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Saved mcp-template (1).json to Downloads")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("mcp-template.json", "mcp-template (1).json"), provider.names())
    }

    @Test
    fun creatingTheTemplateDoesNotTouchSavedServers() {
        Robolectric.setupContentProvider(McpTemplateTest.FakeMediaProvider::class.java, "media")
        compose.setContent { McpListSection(store) }
        compose.onNodeWithText("Create MCP sheet for import").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Saved mcp-template.json to Downloads")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, store.storedMcps().size)
    }

    @Test
    fun aFailedWriteSaysSo() {
        Robolectric.setupContentProvider(McpTemplateTest.FakeMediaProvider::class.java, "media").failInsert = true
        compose.setContent { McpListSection(store) }
        compose.onNodeWithText("Create MCP sheet for import").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Could not create the template")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
