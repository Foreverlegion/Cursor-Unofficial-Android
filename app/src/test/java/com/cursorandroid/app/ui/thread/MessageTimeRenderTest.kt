package com.cursorandroid.app.ui.thread

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import com.cursorandroid.app.data.repo.TranscriptLine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class MessageTimeRenderTest {
    @get:Rule
    val compose = createComposeRule()

    private val ny = ZoneId.of("America/New_York")
    private val at = Instant.parse("2026-10-09T22:41:00Z").toEpochMilli()

    @Test
    fun bubbleShowsAShortLocalTime() {
        Locale.setDefault(Locale.US)
        compose.setContent {
            MaterialTheme {
                TranscriptBubble(
                    line = TranscriptLine("user-r1", "user", "hello there", "r1"),
                    timeMs = at,
                    zone = ny,
                    use24h = false,
                )
            }
        }

        compose.onNodeWithText("6:41 PM").assertIsDisplayed()
    }

    @Test
    fun twentyFourHourClockIsHonoured() {
        Locale.setDefault(Locale.US)
        compose.setContent {
            MaterialTheme {
                TranscriptBubble(
                    line = TranscriptLine("assistant-r1", "assistant", "done", "r1"),
                    timeMs = at,
                    zone = ny,
                    use24h = true,
                )
            }
        }

        compose.onNodeWithText("18:41").assertIsDisplayed()
    }

    @Test
    fun longPressShowsTheFullDateAndTime() {
        Locale.setDefault(Locale.US)
        compose.setContent {
            MaterialTheme {
                TranscriptBubble(
                    line = TranscriptLine("user-r1", "user", "hello there", "r1"),
                    timeMs = at,
                    zone = ny,
                    use24h = false,
                )
            }
        }

        compose.onNodeWithText("hello there").performSemanticsAction(SemanticsActions.OnLongClick)

        compose.onNodeWithText("Fri, Oct 9, 2026, 6:41 PM").assertIsDisplayed()
    }

    @Test
    fun longPressOnTheTimeAlsoShowsIt() {
        Locale.setDefault(Locale.US)
        compose.setContent {
            MaterialTheme {
                TranscriptBubble(
                    line = TranscriptLine("user-r1", "user", "hello there", "r1"),
                    timeMs = at,
                    zone = ny,
                    use24h = false,
                )
            }
        }

        compose.onNodeWithTag("message-time").performTouchInput { longClick() }

        compose.onNodeWithText("Fri, Oct 9, 2026, 6:41 PM").assertIsDisplayed()
    }

    @Test
    fun noTimeMeansNoTimeLabel() {
        compose.setContent {
            MaterialTheme {
                TranscriptBubble(line = TranscriptLine("user-r1", "user", "hello there", "r1"))
            }
        }

        assertEquals(0, compose.onAllNodesWithTag("message-time").fetchSemanticsNodes().size)
    }

    @Test
    fun queuedMessageShowsNoTime() {
        compose.setContent {
            MaterialTheme {
                TranscriptBubble(
                    line = TranscriptLine("user-local-1", "user", "later", queued = true),
                    timeMs = at,
                    zone = ny,
                )
            }
        }

        assertEquals(0, compose.onAllNodesWithTag("message-time").fetchSemanticsNodes().size)
    }

    @Test
    fun dividerShowsItsLabel() {
        compose.setContent {
            MaterialTheme {
                Column {
                    DayDivider("Yesterday")
                    DayDivider("Oct 8")
                }
            }
        }

        compose.onNodeWithText("Yesterday").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithTag("day-divider").fetchSemanticsNodes().size)
    }
}
