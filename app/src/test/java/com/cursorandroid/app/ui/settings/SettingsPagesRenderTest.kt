package com.cursorandroid.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.ui.theme.Appearance
import com.cursorandroid.app.ui.theme.ChatDensity
import com.cursorandroid.app.ui.theme.CodeFont
import com.cursorandroid.app.ui.theme.CursorTheme
import com.cursorandroid.app.ui.theme.UiFont
import androidx.work.Configuration
import androidx.work.WorkManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsPagesRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun initWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
    }

    private fun container(): AppContainer {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AppContainer(context).also { it.store.demoMode = true }
    }

    private fun render(page: SettingsPage, container: AppContainer = container()) {
        compose.setContent {
            MaterialTheme {
                SettingsScreenContent(
                    container = container,
                    showBack = true,
                    onBack = {},
                    onSignedOut = {},
                    initialPage = page,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun everySettingsPageMeasuresWithoutNestedScroll() {
        val c = container()
        var shown by mutableStateOf(SettingsPage.Home)
        compose.setContent {
            MaterialTheme {
                key(shown) {
                    SettingsScreenContent(
                        container = c,
                        showBack = true,
                        onBack = {},
                        onSignedOut = {},
                        initialPage = shown,
                    )
                }
            }
        }
        SettingsPage.entries.forEach { page ->
            shown = page
            compose.waitForIdle()
            compose.onAllNodesWithText(page.title)[0].assertIsDisplayed()
        }
    }

    @Test
    fun appearancePageRendersWithBundledFontsAndScale() {
        val c = container()
        c.store.uiFont = "inter"
        c.store.codeFont = "jetbrains_mono"
        c.store.textScalePct = 120
        c.store.chatDensity = "compact"
        compose.setContent {
            val appearance = Appearance(
                uiFont = UiFont.fromId(c.store.uiFont),
                codeFont = CodeFont.fromId(c.store.codeFont),
                textScalePct = c.store.textScalePct,
                density = ChatDensity.fromId(c.store.chatDensity),
            )
            CursorTheme(appearance = appearance) {
                SettingsScreenContent(
                    container = c,
                    showBack = true,
                    onBack = {},
                    onSignedOut = {},
                    initialPage = SettingsPage.Appearance,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Preview").assertIsDisplayed()
        compose.onNodeWithText("Font").assertExists()
    }

    @Test
    fun aboutPageShowsCachedRowsAndUsageWindowWithoutWaiting() {
        val c = container()
        render(SettingsPage.About, c)
        compose.onNodeWithText("Overview").assertIsDisplayed()
        compose.onNodeWithText("Cloud agents").assertIsDisplayed()
        compose.onNodeWithText("Usage").assertExists()
    }

    @Test
    fun forgesPageOpensAndAddsAForge() {
        render(SettingsPage.Forges)
        compose.onNodeWithText("No forges yet").assertIsDisplayed()
        compose.onNodeWithText("Add forge").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Provider").assertIsDisplayed()
        compose.onNodeWithText("Token / personal access token").assertExists()
        compose.onNodeWithText("Back to forges").assertExists()
    }

    @Test
    fun repoDefaultsPageOpensAndStartsAnEntry() {
        render(SettingsPage.RepoDefaults)
        compose.onNodeWithText("No repo defaults").assertIsDisplayed()
        compose.onNodeWithText("Add repo default").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Find a repo").performTextInput("https://github.com/example/demo")
        compose.waitForIdle()
        compose.onNodeWithText("Use this URL").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Default model").assertIsDisplayed()
        compose.onNodeWithText("Default branch").assertExists()
    }

    @Test
    fun machinesPageListsSeenMachinesWithHideAndAutoHide() {
        val c = container()
        c.catalog.saveComputers(
            listOf(
                com.cursorandroid.app.data.api.Computer(name = "Laptop", online = true, workerId = "w-1"),
                com.cursorandroid.app.data.api.Computer(name = "OldBox", online = false),
            ),
        )
        c.machines.hide("id:w-1", "Laptop")
        render(SettingsPage.Machines, c)
        compose.onNodeWithText("Auto-hide offline machines").assertIsDisplayed()
        compose.onNodeWithText("Hidden").assertExists()
        compose.onNodeWithText("Unhide").assertExists()
        compose.onNodeWithText("Laptop").assertExists()
    }
}

