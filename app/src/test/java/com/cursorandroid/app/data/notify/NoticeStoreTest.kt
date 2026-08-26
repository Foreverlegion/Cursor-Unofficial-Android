package com.cursorandroid.app.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeStoreTest {

    @Test
    fun dismissedNoticeStaysDismissedOnRerecord() {
        val first = notice("run-1", kind = "working", body = "Working")
        val (afterRecord, dismissed) = applyUpsert(emptyList(), first, emptyList())
        assertEquals(1, afterRecord.size)
        assertFalse(afterRecord[0].dismissed)

        val afterDismiss = afterRecord.map { it.copy(dismissed = true) }
        val ids = rememberDismissed(dismissed, listOf("run-1"))
        val again = notice("run-1", kind = "finished", body = "Finished")
        val (next, nextIds) = applyUpsert(afterDismiss, again, ids)

        assertTrue(next.single().dismissed)
        assertTrue("run-1" in nextIds)
        assertEquals("run-1", next.single().id)
    }

    @Test
    fun evictedDismissedIdDoesNotComeBack() {
        val incoming = notice("run-old", kind = "finished", body = "Finished")
        val (items, ids) = applyUpsert(emptyList(), incoming, listOf("run-old"))
        assertTrue(items.none { it.id == "run-old" })
        assertTrue("run-old" in ids)
    }

    @Test
    fun newRunStillAppears() {
        val dismissed = notice("run-1", kind = "finished", body = "Finished", dismissed = true)
        val incoming = notice("run-2", kind = "working", body = "Working")
        val (items, ids) = applyUpsert(listOf(dismissed), incoming, listOf("run-1"))
        assertEquals(listOf("run-2", "run-1"), items.map { it.id })
        assertFalse(items.first().dismissed)
        assertTrue("run-1" in ids)
        assertFalse("run-2" in ids)
    }

    @Test
    fun relabelUpdatesNoticeTitle() {
        val items = listOf(notice("run-1", kind = "finished", body = "Finished").copy(title = "Old"))
        val next = applyRelabel(items, "agent", "Renamed")
        assertEquals("Renamed", next.single().title)
    }

    @Test
    fun reconcilePrefersLocalChatTitle() {
        val items = listOf(notice("run-1", kind = "finished", body = "Finished").copy(title = "Old"))
        val next = reconcileNotices(items, emptyList(), emptyMap(), mapOf("agent" to "Renamed"))
        assertEquals("Renamed", next.single().title)
    }

    @Test
    fun shadeIdsMatchInboxAndBar() {
        assertEquals("run-1".hashCode(), shadeId("run-1"))
        assertEquals("approval-call-9".hashCode(), shadeId("approval-call-9"))
    }

    @Test
    fun noticeIdReadsExtraThenCursorNoticeUri() {
        assertEquals("run-1", RunNotifier.noticeIdFrom("run-1", null))
        assertEquals("approval-call-9", RunNotifier.noticeIdFrom(null, "cursor-notice:approval-call-9"))
        assertEquals("approval-call-9", RunNotifier.noticeIdFrom(null, "cursor-notice://approval-call-9"))
        assertEquals("run-1", RunNotifier.noticeIdFrom("run-1", "cursor-notice:other"))
        assertNull(RunNotifier.noticeIdFrom(null, "https://cursor.com/agents/x"))
        assertNull(RunNotifier.noticeIdFrom(" ", null))
    }

    private fun notice(
        id: String,
        kind: String,
        body: String,
        dismissed: Boolean = false,
    ) = Notice(
        id = id,
        agentId = "agent",
        title = "Agent",
        body = body,
        kind = kind,
        at = 1L,
        dismissed = dismissed,
    )
}
