package com.cursorandroid.app.data.notify

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NoticeStorePersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        context.getSharedPreferences("notice_feed", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun store() = NoticeStore(context)

    private fun record(store: NoticeStore, run: String, status: String = "FINISHED") =
        store.record("agent", "Agent", run, status, "done")

    @Test
    fun readStateSurvivesARestart() {
        val first = store()
        record(first, "run-1")
        record(first, "run-2")
        assertEquals(2, unreadCount(first.visible()))

        first.markAllRead()
        assertEquals(0, unreadCount(first.visible()))

        val second = store()
        assertEquals(2, second.visible().size)
        assertEquals(0, unreadCount(second.visible()))

        record(second, "run-3")
        assertEquals(1, unreadCount(second.visible()))
    }

    @Test
    fun dismissedStateSurvivesARestartAndClearAllEmptiesTheFeed() {
        val first = store()
        record(first, "run-1")
        record(first, "run-2")
        first.dismiss("run-1")

        val second = store()
        assertEquals(listOf("run-2"), second.visible().map { it.id })
        assertFalse(record(second, "run-1"))
        assertEquals(listOf("run-2"), second.visible().map { it.id })

        second.dismissAll()
        assertTrue(store().visible().isEmpty())
    }

    @Test
    fun noticesStoredByAnOlderVersionStartAsReadAfterUpdate() {
        context.getSharedPreferences("notice_feed", Context.MODE_PRIVATE).edit()
            .putString(
                "items",
                """[{"id":"run-old","agentId":"agent","title":"Agent","body":"Finished","kind":"finished","at":5}]""",
            )
            .commit()

        val updated = store()
        assertEquals(listOf("run-old"), updated.visible().map { it.id })
        assertEquals(0, unreadCount(updated.visible()))

        record(updated, "run-new")
        assertEquals(1, unreadCount(updated.visible()))
        assertEquals(1, unreadCount(store().visible()))
    }

    @Test
    fun aRunThatChangesStateAfterBeingReadIsUnreadAgain() {
        val s = store()
        record(s, "run-1", status = "RUNNING")
        s.markAllRead()
        assertEquals(0, unreadCount(s.visible()))

        record(s, "run-1", status = "FINISHED")
        assertEquals(1, unreadCount(s.visible()))
    }
}
