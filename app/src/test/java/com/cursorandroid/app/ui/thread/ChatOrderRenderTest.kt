package com.cursorandroid.app.ui.thread

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.repo.TranscriptLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class ChatOrderRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun reversedLayoutDrawsRowsInTranscriptOrderTopToBottom() {
        val lines = listOf(
            TranscriptLine("user-r1", "user", "first", "r1"),
            TranscriptLine("assistant-r1", "assistant", "old answer", "r1"),
            TranscriptLine("user-r2", "user", "second", "r2"),
            TranscriptLine("assistant-r2", "assistant", "newest answer", "r2"),
        )
        val rows = groupChatRows(lines, showTools = true, showThinking = true)
        compose.setContent {
            LazyColumn(
                reverseLayout = true,
                modifier = Modifier.fillMaxWidth().height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
            ) {
                items(rows.asReversed(), key = ::chatRowKey) { row ->
                    val id = (row as ChatRow.Message).line.id
                    Box(Modifier.fillMaxWidth().height(50.dp).testTag(id))
                }
            }
        }
        compose.waitForIdle()

        val tops = lines.map { compose.onNodeWithTag(it.id).getUnclippedBoundsInRoot().top.value }
        assertTrue("rows must run down the screen in transcript order: $tops", tops.zipWithNext().all { (a, b) -> a < b })
        assertEquals(lines.last().id, lines.maxByOrNull { l -> compose.onNodeWithTag(l.id).getUnclippedBoundsInRoot().top.value }!!.id)
    }
}
