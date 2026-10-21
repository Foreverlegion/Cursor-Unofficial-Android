package com.cursorandroid.app.ui.thread

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cursorandroid.app.data.repo.TranscriptLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class ToolCallsBlockTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun toolCallsStartCollapsedAndOpenAsOneList() {
        compose.setContent {
            MaterialTheme {
                ToolCallsBlock(
                    tools = listOf(
                        TranscriptLine("t1", "tool", "read\nREADME.md"),
                        TranscriptLine("t2", "tool", "grep\nsrc"),
                    ),
                )
            }
        }
        compose.onNodeWithText("Tool Calls").assertExists()
        compose.onNodeWithText("read").assertDoesNotExist()
        compose.onNodeWithTag("tool-calls").performClick()
        compose.onNodeWithText("read").assertExists()
        compose.onNodeWithText("README.md").assertExists()
        compose.onNodeWithText("grep").assertExists()
        compose.onNodeWithText("src").assertExists()
    }
}
