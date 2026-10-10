package com.cursorandroid.app.ui.inbox

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cursorandroid.app.data.notify.Notice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class NoticePopupTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun notice(id: String, title: String = "Agent $id") = Notice(
        id = id,
        agentId = "agent-$id",
        title = title,
        body = "Finished",
        kind = "finished",
        at = 1L,
    )

    @Test
    fun bellShowsCountAndHidesBadgeWhenAllRead() {
        var unread by mutableStateOf(3)
        compose.setContent { MaterialTheme { NoticeBell(unread = unread, onClick = {}) } }
        compose.onNodeWithContentDescription("Notifications, 3 unread").assertExists()
        compose.onNodeWithText("3").assertExists()

        unread = 0
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Notifications").assertExists()
        compose.onNodeWithTag("notice-badge").assertDoesNotExist()
    }

    @Test
    fun bellClickInvokesCallback() {
        var clicks = 0
        compose.setContent { MaterialTheme { NoticeBell(unread = 1, onClick = { clicks++ }) } }
        compose.onNodeWithTag("notice-bell").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun popupListsNoticesAndRoutesTapDismissAndClearAll() {
        val opened = mutableListOf<String>()
        val dismissed = mutableListOf<String>()
        var cleared = 0
        var closed = 0
        val items = listOf(notice("a"), notice("b"))
        compose.setContent {
            MaterialTheme {
                NoticePopup(
                    notices = items,
                    onOpen = { opened += it.id },
                    onDismiss = { dismissed += it },
                    onClearAll = { cleared++ },
                    onClose = { closed++ },
                )
            }
        }
        compose.onNodeWithText("Notifications").assertExists()
        compose.onNodeWithText("Agent a").performClick()
        assertEquals(listOf("a"), opened)

        compose.onNodeWithTag("notice-b").assertExists()
        compose.onNodeWithTag("notice-a").assertExists()
        compose.onNodeWithText("Clear all").assertIsEnabled().performClick()
        assertEquals(1, cleared)

        compose.onNodeWithText("Close").performClick()
        assertEquals(1, closed)
        assertEquals(emptyList<String>(), dismissed)
    }

    @Test
    fun rowDismissButtonDismissesOnlyThatNotice() {
        val dismissed = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                NoticePopup(
                    notices = listOf(notice("only")),
                    onOpen = {},
                    onDismiss = { dismissed += it },
                    onClearAll = {},
                    onClose = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(listOf("only"), dismissed)
    }

    @Test
    fun emptyPopupSaysSoAndDisablesClearAll() {
        compose.setContent {
            MaterialTheme {
                NoticePopup(notices = emptyList(), onOpen = {}, onDismiss = {}, onClearAll = {}, onClose = {})
            }
        }
        compose.onNodeWithText("No notifications").assertExists()
        compose.onNodeWithText("Clear all").assertIsNotEnabled()
    }
}
