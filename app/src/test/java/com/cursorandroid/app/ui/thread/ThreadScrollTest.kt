package com.cursorandroid.app.ui.thread

import com.cursorandroid.app.data.repo.TranscriptLine
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThreadScrollTest {
    @Test
    fun thinkingGrowthChangesKeyWhenUserIsQueued() {
        val user = TranscriptLine("user-r1", "user", "do the work", "r1")
        val think = TranscriptLine("think-r1", "thinking", "plan", "r1")
        val queued = TranscriptLine("user-local-1", "user", "also this", queued = true)
        val before = threadScrollKey(groupChatRows(listOf(user, think, queued), showTools = true, showThinking = true))
        val after = threadScrollKey(
            groupChatRows(
                listOf(user, think.copy(text = "plan\nmore thinking"), queued),
                showTools = true,
                showThinking = true,
            ),
        )
        assertNotEquals(before, after)
    }
}
