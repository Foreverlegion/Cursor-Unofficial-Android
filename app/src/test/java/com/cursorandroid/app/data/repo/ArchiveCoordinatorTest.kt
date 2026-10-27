package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.ApiException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArchiveCoordinatorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var ui: UiPrefsStore
    private lateinit var catalog: CatalogCache
    private lateinit var signals: InboxSignals
    private lateinit var scope: CoroutineScope
    private val open = mutableListOf<UiPrefsStore>()
    private var now = 5_000L
    private val sent = mutableListOf<String>()
    private val tasks = mutableListOf<Scheduled>()
    private var fail: Exception? = null
    private var gate: CompletableDeferred<Unit>? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(tmp.newFolder(), "ui_prefs.preferences_pb")
        ui = UiPrefsStore.open(LegacyUiPrefs(), file).also { open += it }
        catalog = CatalogCache(context)
        catalog.saveAgents(listOf(AgentSummary(id = "a", name = "Alpha", status = "FINISHED")))
        signals = InboxSignals()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        scope.cancel()
        open.forEach { runCatching { it.close() } }
    }

    @Test
    fun undoInsideTheWindowRestoresTheRowAndNeverCallsArchive() {
        val archives = coordinator()
        assertTrue(archives.enqueue("a", "FINISHED", "Alpha"))
        assertEquals(true, catalog.agents().single().archived)
        val undone = archives.undo("a")
        assertEquals("FINISHED", undone?.previousStatus)
        assertEquals(false, catalog.agents().single().archived)
        assertEquals("FINISHED", catalog.agents().single().status)
        assertTrue(archives.toasts.value.isEmpty())
        now += ARCHIVE_SEND_DELAY_MS + 1_000
        fireDue()
        assertTrue(sent.isEmpty())
    }

    @Test
    fun theUndoNoteStaysUpUntilTheArchiveIsSent() {
        val archives = coordinator()
        val started = now
        archives.enqueue("a", "FINISHED", "Alpha")
        val toast = archives.toasts.value.single()
        assertEquals("Alpha archived", toast.message)
        assertEquals(ARCHIVE_SEND_DELAY_MS, ARCHIVE_TOAST_MS)
        assertEquals(started + ARCHIVE_SEND_DELAY_MS, toast.until)
        now += 5_000
        assertTrue(archives.toasts.value.single().until > now)
        archives.dismissToast("a")
        assertTrue(archives.toasts.value.isEmpty())
        assertEquals(setOf("a"), archives.pendingIds())
        fireDue()
        assertTrue(sent.isEmpty())
        now = started + ARCHIVE_SEND_DELAY_MS
        fireDue()
        assertEquals(listOf("a"), sent)
        assertEquals(true, catalog.agents().single().archived)
    }

    @Test
    fun aFailedSendRollsTheRowBackAndReportsTheReason() {
        fail = ApiException(500, "offline")
        val archives = coordinator()
        archives.enqueue("a", "FINISHED", "Alpha")
        now += ARCHIVE_SEND_DELAY_MS
        fireDue()
        assertEquals(false, catalog.agents().single().archived)
        assertEquals("FINISHED", catalog.agents().single().status)
        assertTrue(archives.toasts.value.isEmpty())
        assertTrue(archives.pendingIds().isEmpty())
        assertEquals(listOf("Couldn't archive. offline"), signals.drainErrors())
        assertEquals("a", signals.drainRestores().single().id)
    }

    @Test
    fun anAlreadyArchivedResponseCountsAsSuccess() {
        fail = ApiException(409, "agent_archived", errorCode = "agent_archived")
        val archives = coordinator()
        archives.enqueue("a", "FINISHED", "Alpha")
        now += ARCHIVE_SEND_DELAY_MS
        fireDue()
        assertEquals(true, catalog.agents().single().archived)
        assertTrue(archives.pendingIds().isEmpty())
        assertTrue(signals.drainErrors().isEmpty())
    }

    @Test
    fun undoAfterTheSendStartsDoesNotUnarchive() {
        gate = CompletableDeferred()
        val archives = coordinator()
        archives.enqueue("a", "FINISHED", "Alpha")
        archives.flush()
        assertEquals(listOf("a"), sent)
        assertNull(archives.undo("a"))
        assertEquals(true, catalog.agents().single().archived)
        gate?.complete(Unit)
    }

    @Test
    fun backgroundFlushSendsPendingArchivesImmediately() {
        val archives = coordinator()
        archives.enqueue("a", "FINISHED", "Alpha")
        now += 1_000
        archives.flush()
        assertEquals(listOf("a"), sent)
        assertTrue(archives.pendingIds().isEmpty())
    }

    @Test
    fun theNextLaunchSendsArchivesLeftByAKilledProcess() {
        val first = coordinator()
        first.enqueue("a", "FINISHED", "Alpha")
        assertEquals(listOf("a"), PendingArchiveStore(ui).load().map { it.agentId })
        sent.clear()
        val second = coordinator()
        second.recoverAfterProcessDeath()
        assertEquals(listOf("a"), sent)
        assertTrue(second.pendingIds().isEmpty())
        assertTrue(second.toasts.value.isEmpty())
    }

    private fun coordinator(): ArchiveCoordinator {
        return ArchiveCoordinator(
            store = PendingArchiveStore(ui),
            scope = scope,
            catalog = catalog,
            archive = { id ->
                sent += id
                gate?.await()
                fail?.let { throw it }
            },
            signals = signals,
            clock = { now },
            schedule = { delayMs, block ->
                Scheduled(now + delayMs, block).also { tasks += it }
            },
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
}
