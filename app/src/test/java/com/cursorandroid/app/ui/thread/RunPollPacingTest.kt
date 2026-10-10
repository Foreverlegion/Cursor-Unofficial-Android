package com.cursorandroid.app.ui.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPollPacingTest {
    @Test
    fun pollsWithinASecondOfASendThenEveryTwoSecondsUntilOutput() {
        assertEquals(1_000L, runPollDelayMs(receiving = false, tick = 0))
        assertEquals(2_000L, runPollDelayMs(receiving = false, tick = 1))
        assertEquals(2_000L, runPollDelayMs(receiving = false, tick = 40))
    }

    @Test
    fun slowsDownOnceOutputIsFlowing() {
        assertEquals(4_000L, runPollDelayMs(receiving = true, tick = 0))
        assertEquals(4_000L, runPollDelayMs(receiving = true, tick = 9))
    }

    @Test
    fun readsTheConversationEverySixSecondsOnlyWhileWaiting() {
        val waiting = (0 until 9).filter { pullConversationOnTick(receiving = false, tick = it) }
        assertEquals(listOf(2, 5, 8), waiting)
        assertTrue((0 until 9).none { pullConversationOnTick(receiving = true, tick = it) })
        assertFalse(pullConversationOnTick(receiving = false, tick = 0))
    }
}
