package com.cursorandroid.app.ui

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionExpiredBannerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsAClearMessageAndASignInAgainAction() {
        var taps = 0
        compose.setContent { MaterialTheme { SessionExpiredBanner(onSignInAgain = { taps++ }) } }
        compose.onNodeWithText("Your Cursor sign-in expired").assertIsDisplayed()
        compose.onNodeWithText("Sign in again").assertIsDisplayed().performClick()
        assertEquals(1, taps)
    }
}
