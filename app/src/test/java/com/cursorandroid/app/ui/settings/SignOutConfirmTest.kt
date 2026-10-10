package com.cursorandroid.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.cursorandroid.app.AppContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SignOutConfirmTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun initWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
    }

    private fun aboutPage(container: AppContainer, onSignedOut: () -> Unit, onSessionChanged: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                SettingsScreenContent(
                    container = container,
                    showBack = true,
                    onBack = {},
                    onSignedOut = onSignedOut,
                    onSessionChanged = onSessionChanged,
                    initialPage = SettingsPage.About,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun tapSignOut() {
        compose.onNodeWithTag("sign-out-row").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun theNoteSaysWhatIsClearedAndWhatIsKept() {
        val note = signOutNote(demo = false)
        assertTrue(note, note.contains("sign-in key") && note.contains("forge tokens") && note.contains("removed"))
        assertTrue(note, note.contains("Kept") && note.contains("settings"))
        assertEquals("This leaves demo mode. Nothing else changes.", signOutNote(demo = true))
    }

    @Test
    fun theDialogShowsTheTitleNoteAndBothButtons() {
        compose.setContent { MaterialTheme { SignOutDialog(demo = false, onConfirm = {}, onDismiss = {}) } }
        compose.onNodeWithText("Sign out?").assertIsDisplayed()
        compose.onNodeWithText(signOutNote(false)).assertIsDisplayed()
        compose.onNodeWithTag("sign-out-cancel").assertIsDisplayed()
        compose.onNodeWithTag("sign-out-confirm").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Sign out").assertIsDisplayed()
    }

    @Test
    fun cancelAndConfirmAreSeparateActions() {
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            MaterialTheme { SignOutDialog(demo = false, onConfirm = { confirmed++ }, onDismiss = { dismissed++ }) }
        }
        compose.onNodeWithTag("sign-out-cancel").performClick()
        assertEquals(0, confirmed)
        assertEquals(1, dismissed)
        compose.onNodeWithTag("sign-out-confirm").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun tappingSignOutOnlyAsksAndCancelSignsNobodyOut() {
        val c = AppContainer(ApplicationProvider.getApplicationContext<Context>())
        c.store.demoMode = true
        var signedOut = 0
        aboutPage(c, onSignedOut = { signedOut++ })

        tapSignOut()
        compose.onNodeWithText("Sign out?").assertIsDisplayed()
        assertEquals("nothing happens until confirmed", 0, signedOut)
        assertTrue(c.store.demoMode)

        compose.onNodeWithTag("sign-out-cancel").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Sign out?").assertCountEquals(0)
        assertEquals(0, signedOut)
        assertTrue(c.store.demoMode)
    }

    @Test
    fun confirmingLeavesDemoModeAndSignsOut() {
        val c = AppContainer(ApplicationProvider.getApplicationContext<Context>())
        c.store.demoMode = true
        var signedOut = 0
        aboutPage(c, onSignedOut = { signedOut++ })

        tapSignOut()
        compose.onNodeWithTag("sign-out-confirm").performClick()
        compose.waitForIdle()

        assertEquals(1, signedOut)
        assertFalse(c.store.demoMode)
    }

    @Test
    fun confirmingARealSignOutRemovesTheKeyButKeepsSettings() {
        val c = AppContainer(ApplicationProvider.getApplicationContext<Context>())
        c.store.demoMode = false
        c.store.apiKey = "key_SESSION"
        c.store.defaultModel = "sonnet"
        var signedOut = 0
        aboutPage(c, onSignedOut = { signedOut++ })

        tapSignOut()
        assertEquals("key_SESSION", c.store.apiKey)
        compose.onNodeWithTag("sign-out-cancel").performClick()
        compose.waitForIdle()
        assertEquals("key_SESSION", c.store.apiKey)
        assertEquals(0, signedOut)

        tapSignOut()
        compose.onNodeWithTag("sign-out-confirm").performClick()
        compose.waitForIdle()

        assertEquals(1, signedOut)
        assertNull(c.store.apiKey)
        assertEquals("sonnet", c.store.defaultModel)
    }
}
