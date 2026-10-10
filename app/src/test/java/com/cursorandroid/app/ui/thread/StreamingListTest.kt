package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.repo.TranscriptLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
