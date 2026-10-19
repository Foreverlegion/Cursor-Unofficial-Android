package com.cursorandroid.app.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UserRuleDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startStaysOffUntilTheCloneUrlIsOrigin() {
        var started: String? = null
        compose.setContent {
            var url by remember { mutableStateOf("") }
            UserRuleDialog(
                url = url,
                onUrl = { url = it },
                error = null,
                onDismiss = {},
                onStart = { started = it },
            )
        }
        compose.onNodeWithText("Start").assertIsNotEnabled()
        compose.onNodeWithTag("user-rule-origin-url").performTextInput("https://github.com/acme/app.git")
        compose.onNodeWithText("Start").assertIsNotEnabled()
        compose.onNodeWithTag("user-rule-origin-url").performTextReplacement("https://origin.cursor.com/acme/rocket")
        compose.onNodeWithText("Start").assertIsEnabled().performClick()
        assertEquals("https://origin.cursor.com/git/acme/rocket.git", started)
    }
}
