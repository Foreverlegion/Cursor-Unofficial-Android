package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.auth.DemoAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSessionTest {
    @Test
    fun acceptsDemoCredentials() {
        assertTrue(DemoAccess.accepts("demo", "demo"))
        assertTrue(DemoAccess.accepts(" Demo ", "demo"))
        assertFalse(DemoAccess.accepts("demo", "Demo"))
        assertFalse(DemoAccess.accepts("review", "demo"))
    }

    @Test
    fun seedsTwoChatsWithAFewMessages() {
        val session = DemoSession()
        val chats = session.summaries(includeArchived = true)
        assertEquals(2, chats.size)
        assertEquals(setOf("FINISHED", "ERROR"), chats.map { it.status }.toSet())
        chats.forEach { chat ->
            val count = session.conversation(chat.id).messages.size
            assertTrue(count in 2..4)
            assertTrue(count % 2 == 0)
        }
        assertTrue(session.repos().isNotEmpty())
        assertTrue(session.environments().isNotEmpty())
        assertTrue(session.workers().isNotEmpty())
    }

    @Test
    fun newMessageUsesTheDemoReply() {
        val session = DemoSession()
        val id = session.summaries(false).first().id
        val before = session.conversation(id).messages.size
        val run = session.followUp(id, "Can you change the padding?")
        assertEquals(DemoSession.REPLY, run.result)
        val after = session.conversation(id).messages
        assertEquals(before + 2, after.size)
        assertEquals(DemoSession.REPLY, after.last().text)
    }
}
