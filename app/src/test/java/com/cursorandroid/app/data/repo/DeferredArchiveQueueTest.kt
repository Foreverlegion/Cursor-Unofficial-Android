package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredArchiveQueueTest {
    private var now = 1_000L
    private val sink = MemoryArchiveSink()
    private val due = mutableListOf<String>()
    private val rolled = mutableListOf<Pair<String, String>>()
    private val tasks = mutableListOf<Scheduled>()

    @Test
    fun undoBeforeSevenSecondsCancelsTheSend() {
        val queue = queue()
        assertTrue(queue.enqueue("a", "FINISHED"))
        now += 6_999
        fireDue()
        assertTrue(due.isEmpty())
        val undone = queue.undo("a")
        assertEquals("a", undone?.agentId)
        assertEquals("FINISHED", undone?.previousStatus)
        now += ARCHIVE_SEND_DELAY_MS
        fireDue()
        assertTrue(due.isEmpty())
        assertTrue(sink.items.isEmpty())
        assertTrue(queue.pendingIds().isEmpty())
    }

    @Test
    fun sendFiresAtSevenSeconds() {
        val queue = queue()
        queue.enqueue("a", "RUNNING")
        now += ARCHIVE_SEND_DELAY_MS - 1
        fireDue()
        assertTrue(due.isEmpty())
        now += 1
        fireDue()
        assertEquals(listOf("a"), due)
        assertTrue(queue.pendingSnapshot().single().sending)
        queue.complete("a", ok = true)
        assertTrue(queue.pendingIds().isEmpty())
        assertTrue(sink.items.isEmpty())
    }

    @Test
    fun failureRollsBackAndClearsThePendingRecord() {
        val queue = queue()
        queue.enqueue("a", "FINISHED")
        now += ARCHIVE_SEND_DELAY_MS
        fireDue()
        queue.complete("a", ok = false, reason = "offline")
        assertEquals(listOf("a" to "offline"), rolled)
        assertTrue(queue.pendingIds().isEmpty())
        assertTrue(sink.items.isEmpty())
    }

    @Test
    fun aBlankFailureReasonStillRollsBack() {
        val queue = queue()
        queue.enqueue("a", "FINISHED")
        queue.flush()
        queue.complete("a", ok = false)
        assertEquals(listOf("a" to "Request failed"), rolled)
    }

    @Test
    fun flushSendsEveryWaitingArchiveOnce() {
        val queue = queue()
        queue.enqueue("a", "FINISHED")
        now += 3_000
        queue.enqueue("b", "ERROR")
        now += 1_000
        queue.flush()
        assertEquals(setOf("a", "b"), due.toSet())
        queue.flush()
        assertEquals(2, due.size)
        assertTrue(queue.pendingSnapshot().all { it.sending })
    }

    @Test
    fun twoArchivesFireOnTheirOwnTimers() {
        val queue = queue()
        queue.enqueue("a", null)
        now += 3_000
        queue.enqueue("b", null)
        now += 4_000
        fireDue()
        assertEquals(listOf("a"), due)
        now += 3_000
        fireDue()
        assertEquals(listOf("a", "b"), due)
    }

    @Test
    fun aSecondEnqueueOfTheSameIdIsIgnored() {
        val queue = queue()
        assertTrue(queue.enqueue("a", "FINISHED"))
        assertFalse(queue.enqueue("a", "RUNNING"))
        assertEquals("FINISHED", queue.pendingSnapshot().single().previousStatus)
        assertEquals(1, tasks.count { !it.cancelled })
    }

    @Test
    fun undoAfterSendingStartedReturnsNull() {
        val queue = queue()
        queue.enqueue("a", "FINISHED")
        queue.flush()
        assertNull(queue.undo("a"))
        assertEquals(setOf("a"), queue.pendingIds())
    }

    @Test
    fun loadAfterAKillRetriesIdsThatWereAlreadySending() {
        val first = queue()
        first.enqueue("a", "FINISHED")
        first.enqueue("b", "RUNNING")
        first.flush()
        assertTrue(sink.items.all { it.sending })
        val secondDue = mutableListOf<String>()
        val second = queue(onDue = { secondDue += it.agentId })
        second.load()
        assertEquals(setOf("a", "b"), second.pendingIds())
        assertTrue(second.pendingSnapshot().none { it.sending })
        second.flush()
        assertEquals(setOf("a", "b"), secondDue.toSet())
    }

    private fun queue(onDue: (PendingArchive) -> Unit = { due += it.agentId }): DeferredArchiveQueue {
        return DeferredArchiveQueue(
            clock = { now },
            schedule = { delayMs, block ->
                Scheduled(now + delayMs, block).also { tasks += it }
            },
            store = sink,
            onDue = onDue,
            onRollback = { item, reason -> rolled += item.agentId to reason },
        )
    }

    private fun fireDue() {
        val ready = tasks.filter { !it.cancelled && it.dueAt <= now }
        ready.forEach { task ->
            if (!task.cancelled) task.block()
        }
    }

    private class Scheduled(
        val dueAt: Long,
        val block: () -> Unit,
    ) : ArchiveTask {
        var cancelled = false
        override fun cancel() {
            cancelled = true
        }
    }

    private class MemoryArchiveSink : PendingArchiveSink {
        var items: List<PendingArchive> = emptyList()
        override fun load(): List<PendingArchive> = items
        override fun save(items: List<PendingArchive>) {
            this.items = items
        }
    }
}
