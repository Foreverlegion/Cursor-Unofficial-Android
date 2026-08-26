package com.cursorandroid.app.data.notify

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisibleAgentTest {
    @Before
    @After
    fun reset() {
        VisibleAgent.resetForTest()
    }

    @Test
    fun shadePostsWhenAppIsBackgrounded() {
        VisibleAgent.set("chat-1")
        assertFalse(VisibleAgent.shouldSuppress())
    }

    @Test
    fun shadeIsSuppressedOnAnyScreenWhileAppIsOpen() {
        VisibleAgent.activityStarted()
        VisibleAgent.set("other-chat")
        assertTrue(VisibleAgent.shouldSuppress())
    }

    @Test
    fun shadeReturnsAfterLastActivityStops() {
        VisibleAgent.activityStarted()
        VisibleAgent.activityStarted()
        VisibleAgent.activityStopped()
        assertTrue(VisibleAgent.shouldSuppress())
        VisibleAgent.activityStopped()
        assertFalse(VisibleAgent.shouldSuppress())
    }

    @Test
    fun extraStopsDoNotGoNegative() {
        VisibleAgent.activityStopped()
        VisibleAgent.activityStopped()
        assertFalse(VisibleAgent.shouldSuppress())
        VisibleAgent.activityStarted()
        assertTrue(VisibleAgent.shouldSuppress())
    }
}
