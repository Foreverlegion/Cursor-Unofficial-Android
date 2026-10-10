package com.cursorandroid.app.ui.thread

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.repo.TranscriptLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class ChatRowKeysTest {
    private fun tool(id: String, text: String) = TranscriptLine(id, "tool", text)
    private fun msg(id: String, kind: String, text: String) = TranscriptLine(id, kind, text)

    @Test
    fun keysStayTheSameWhileARowGrows() {
        val before = groupChatRows(
            listOf(msg("user-r1", "user", "go"), tool("tool-1", "read"), tool("tool-2", "grep"), msg("assistant-r1", "assistant", "do")),
            showTools = true,
            showThinking = true,
        )
        val after = groupChatRows(
            listOf(
                msg("user-r1", "user", "go"),
                tool("tool-1", "read"),
                tool("tool-2", "grep"),
                tool("tool-3", "edit"),
                msg("assistant-r1", "assistant", "doing it now, with more words streamed in"),
            ),
            showTools = true,
            showThinking = true,
        )
        assertEquals(before.map(::chatRowKey).take(2), after.map(::chatRowKey).take(2))
        assertEquals(before.map(::chatRowKey).last(), after.map(::chatRowKey).last())
        assertEquals(before.map(::chatRowType), after.map(::chatRowType).take(before.size))
    }

    @Test
    fun everyRowKeyIsUniqueAndNeverCollidesWithTheLiveTail() {
        val rows = groupChatRows(
            listOf(
                msg("user-r1", "user", "a"),
                tool("t1", "x"),
                msg("assistant-r1", "assistant", "b"),
                msg("user-r2", "user", "c"),
                tool("t2", "y"),
                msg("assistant-r2", "assistant", "d"),
            ),
            showTools = true,
            showThinking = true,
        )
        val keys = rows.map(::chatRowKey)
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(LIVE_TAIL_KEY !in keys)
    }

    @Test
    fun aMessageKeepsItsKeyWhenItsTextGrowsOrItBecomesPartOfALongerThread() {
        val one = chatRowKey(ChatRow.Message(msg("assistant-r1", "assistant", "a")))
        val two = chatRowKey(ChatRow.Message(msg("assistant-r1", "assistant", "a lot more text now")))
        assertEquals(one, two)
        assertNotEquals(one, chatRowKey(ChatRow.Message(msg("assistant-r2", "assistant", "a"))))
    }
}

class CoalesceTest {
    @Test
    fun aBurstOfTokensBecomesAFewUpdatesAndEndsOnTheLatest() = runBlocking {
        val source = MutableStateFlow(0)
        val seen = ArrayList<Int>()
        withTimeout(5_000) {
            val job = launch {
                source.coalesced(60).collect { seen += it }
            }
            repeat(400) {
                source.value = it + 1
                kotlinx.coroutines.delay(1)
            }
            kotlinx.coroutines.delay(200)
            job.cancel()
        }
        assertEquals(400, seen.last())
        assertTrue("expected a handful of updates, got ${seen.size}", seen.size in 2..30)
    }

    @Test
    fun aQuietListUpdatesImmediately() = runBlocking {
        val source = MutableStateFlow(0)
        val seen = ArrayList<Int>()
        withTimeout(5_000) {
            val job = launch { source.coalesced(300).collect { seen += it } }
            kotlinx.coroutines.delay(50)
            assertEquals(listOf(0), seen)
            source.value = 1
            kotlinx.coroutines.delay(400)
            assertEquals(listOf(0, 1), seen)
            job.cancel()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
class StickToBottomScrollTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun followingAGrowingTailStaysPinnedWithoutJumpingToTheRowFirst() {
        var tail by mutableIntStateOf(60)
        lateinit var state: LazyListState
        compose.setContent {
            state = rememberLazyListState()
            LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(400.dp)) {
                items(40, key = { "row-$it" }) { Box(Modifier.fillMaxWidth().height(60.dp)) }
                item(key = LIVE_TAIL_KEY) { Box(Modifier.fillMaxWidth().height(tail.dp)) }
            }
        }
        compose.waitForIdle()
        val last = state.layoutInfo.totalItemsCount - 1
        compose.runOnIdle { runBlocking { state.scrollToBottom() } }
        compose.waitForIdle()
        assertTrue("setup: list is scrolled near the end", state.firstVisibleItemIndex >= last - 8)

        repeat(30) { step ->
            tail = 60 + (step + 1) * 9
            compose.waitForIdle()
            assertTrue("step $step: tail stays on screen, so only the one-step adjustment runs", state.isTailOnScreen())
            compose.runOnIdle { runBlocking { state.scrollToBottom() } }
            compose.waitForIdle()
            val info = state.layoutInfo
            val lastItem = info.visibleItemsInfo.last()
            assertEquals("step $step: pinned", info.viewportEndOffset, lastItem.offset + lastItem.size)
        }

        val info = state.layoutInfo
        val lastItem = info.visibleItemsInfo.last()
        assertEquals("tail bottom sits on the viewport bottom", info.viewportEndOffset, lastItem.offset + lastItem.size)
    }

    @Test
    fun jumpsToTheTailOnlyWhenItIsOffScreen() {
        lateinit var state: LazyListState
        compose.setContent {
            state = rememberLazyListState()
            LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(400.dp)) {
                items(100, key = { "row-$it" }) { Box(Modifier.fillMaxWidth().size(60.dp)) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { runBlocking { state.scrollToBottom() } }
        compose.waitForIdle()
        val info = state.layoutInfo
        val lastItem = info.visibleItemsInfo.last()
        assertEquals(99, lastItem.index)
        assertEquals(info.viewportEndOffset, lastItem.offset + lastItem.size)
    }
}
