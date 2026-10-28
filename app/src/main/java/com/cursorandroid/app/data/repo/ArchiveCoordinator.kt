package com.cursorandroid.app.data.repo

import com.cursorandroid.app.data.api.actionReason
import com.cursorandroid.app.data.api.alreadyArchived
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArchiveToast(
    val id: String,
    val message: String,
    val until: Long,
)

/**
 * Hides a chat at once and sends the archive [ARCHIVE_SEND_DELAY_MS] later.
 * Each id keeps its own timer. The Undo note is only a view of that timer: dismissing the note
 * does not send early and does not cancel. A kill persists the pending ids in [PendingArchiveStore]
 * and [recoverAfterProcessDeath] sends them. Backgrounding calls [flush] so they are not left waiting.
 */
class ArchiveCoordinator(
    store: PendingArchiveStore,
    private val scope: CoroutineScope,
    private val catalog: CatalogCache,
    private val archive: suspend (String) -> Unit,
    private val signals: InboxSignals,
    private val clock: () -> Long = { System.currentTimeMillis() },
    schedule: ((Long, () -> Unit) -> ArchiveTask)? = null,
) {
    private val queue = DeferredArchiveQueue(
        clock = clock,
        schedule = schedule ?: { delayMs, block ->
            val job = scope.launch {
                kotlinx.coroutines.delay(delayMs)
                block()
            }
            ArchiveTask { job.cancel() }
        },
        store = store,
        onRollback = { item, reason -> rollback(item, reason) },
    )

    private val toastState = MutableStateFlow<List<ArchiveToast>>(emptyList())
    val toasts: StateFlow<List<ArchiveToast>> = toastState

    init {
        queue.onDue = { item ->
            scope.launch {
                try {
                    archive(item.agentId)
                    queue.complete(item.agentId, ok = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.alreadyArchived()) queue.complete(item.agentId, ok = true)
                    else queue.complete(item.agentId, ok = false, reason = e.actionReason())
                }
            }
        }
    }

    fun enqueue(agentId: String, previousStatus: String?, label: String): Boolean {
        if (!queue.enqueue(agentId, previousStatus)) return false
        patch(agentId, archived = true, status = "ARCHIVED")
        val toast = ArchiveToast(
            id = agentId,
            message = "$label archived",
            until = clock() + ARCHIVE_TOAST_MS,
        )
        toastState.update { current -> current.filterNot { it.id == agentId } + toast }
        return true
    }

    fun undo(agentId: String): PendingArchive? {
        val item = queue.undo(agentId) ?: return null
        toastState.update { current -> current.filterNot { it.id == agentId } }
        patch(agentId, archived = false, status = item.previousStatus)
        return item
    }

    /** The note is gone. The pending archive still sends at its original time. */
    fun dismissToast(agentId: String) {
        toastState.update { current ->
            if (current.none { it.id == agentId }) current else current.filterNot { it.id == agentId }
        }
    }

    fun pendingIds(): Set<String> = queue.pendingIds()

    fun flush() = queue.flush()

    fun recoverAfterProcessDeath() {
        queue.load()
        queue.flush()
    }

    private fun rollback(item: PendingArchive, reason: String) {
        patch(item.agentId, archived = false, status = item.previousStatus)
        catalog.agents().firstOrNull { it.id == item.agentId }?.let { signals.restore(it) }
        toastState.update { current -> current.filterNot { it.id == item.agentId } }
        signals.error("Couldn't archive. $reason")
    }

    private fun patch(id: String, archived: Boolean, status: String?) {
        catalog.updateAgent(id) { it.copy(archived = archived, status = status) }
    }
}
