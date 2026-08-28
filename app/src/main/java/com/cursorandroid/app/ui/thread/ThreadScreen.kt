package com.cursorandroid.app.ui.thread

import androidx.activity.compose.BackHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.api.AgentDetail
import com.cursorandroid.app.data.api.AgentUsageResponse
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.ArtifactItem
import com.cursorandroid.app.data.api.GitSnap
import com.cursorandroid.app.data.api.ModelSelection
import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.Run
import com.cursorandroid.app.data.api.StreamEvent
import com.cursorandroid.app.data.api.gitPath
import com.cursorandroid.app.data.api.isActive
import com.cursorandroid.app.data.api.isCloudEnvType
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.api.isWorking
import com.cursorandroid.app.data.repo.ConversationSnap
import com.cursorandroid.app.data.repo.coalesceTranscript
import com.cursorandroid.app.data.repo.mergeTranscript
import com.cursorandroid.app.data.api.isTerminal
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.notify.VisibleAgent
import com.cursorandroid.app.data.repo.TranscriptLine
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.repo.ArtifactSaver
import com.cursorandroid.app.data.repo.AttachItem
import com.cursorandroid.app.data.repo.Attachments
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.data.repo.ChatDraft
import com.cursorandroid.app.data.repo.ChatShare
import com.cursorandroid.app.data.repo.QueuedItem
import com.cursorandroid.app.data.repo.RepoBehind
import com.cursorandroid.app.data.repo.looksLikeGitSha
import com.cursorandroid.app.data.repo.toDraft
import com.cursorandroid.app.data.repo.toItems
import com.cursorandroid.app.ui.chat.AttachButton
import com.cursorandroid.app.ui.chat.AttachChips
import com.cursorandroid.app.ui.chat.RenameChatDialog
import com.cursorandroid.app.ui.chat.VoiceButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ThreadViewModel(
    private val container: AppContainer,
    val agentId: String,
) : ViewModel() {
    var agent by mutableStateOf<AgentDetail?>(null)
        private set
    var run by mutableStateOf<Run?>(null)
        private set
    var lines by mutableStateOf<List<TranscriptLine>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var streaming by mutableStateOf(false)
        private set
    var receiving by mutableStateOf(false)
        private set
    var pinnedArtifact by mutableStateOf<ArtifactItem?>(null)
        private set
    var artifactHistory by mutableStateOf<List<ArtifactItem>>(emptyList())
        private set
    var usage by mutableStateOf<AgentUsageResponse?>(null)
        private set
    var behind by mutableStateOf<RepoBehind?>(null)
        private set

    private var streamJob: Job? = null
    private var pollJob: Job? = null
    private var watchJob: Job? = null
    private var assistantBuf = StringBuilder()
    private var thinkingBuf = StringBuilder()
    private var lastEventId: String? = null
    private var streamedRunId: String? = null
    private val outbound = ArrayList<QueuedOutbound>()
    private var sendingId: String? = null
    private var appContext: android.content.Context? = null
    private val refreshLock = Mutex()

    init {
        viewModelScope.launch { refresh() }
        startWatch()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshLock.withLock {
                try {
                    error = null
                    val snap = container.conversations.loadSnap(agentId)
                    lines = mergeTranscript(lines, snap.lines)
                    restoreQueue()
                    loadLocalArtifacts()
                    restoreRun(snap)
                    persist(immediate = true)
                    val detail = container.repo.getAgent(agentId)
                    agent = detail
                    mergeServerRuns()
                    val runId = detail.latestRunId
                    if (runId != null) {
                        val latest = container.repo.getRun(agentId, runId)
                        latest.git?.branches?.firstOrNull()?.let { git ->
                            container.catalog.saveGit(
                                GitSnap(agentId, git.branch, git.prUrl, git.repoUrl),
                            )
                        }
                        usage = container.repo.usage(agentId)
                        adoptRun(latest)
                    } else if (run?.isActive() == true) {
                        attachRun(run!!.id)
                    }
                    ingestArtifacts(runCatching { container.repo.artifacts(agentId) }.getOrDefault(emptyList()))
                    if (!detail.isWorking() && run?.isActive() != true) {
                        checkBehind(detail)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = displayError(e)
                }
            }
        }
    }

    fun persistNow() {
        persist(immediate = true)
        container.conversations.flush(agentId)
    }

    var followMode by mutableStateOf("")
    var followModel by mutableStateOf("")

    fun followUp(
        prompt: Prompt,
        label: String,
        thumbs: List<String>,
        appContext: android.content.Context,
        attaches: List<AttachItem> = emptyList(),
    ) {
        val shown = label.ifBlank { prompt.text }.trim()
        if (shown.isEmpty() && prompt.images.isNullOrEmpty()) return
        this.appContext = appContext
        hidePinnedArtifact()
        val localId = "user-local-${UUID.randomUUID()}"
        upsert(
            localId,
            "user",
            shown,
            queued = run?.isActive() == true || outbound.isNotEmpty(),
            thumbs = thumbs,
        )
        outbound.add(QueuedOutbound(localId, prompt, attaches))
        persistQueue()
        viewModelScope.launch { flushOutbound() }
    }

    fun cancelQueued(id: String) {
        if (id == sendingId) return
        outbound.removeAll { it.id == id }
        lines = lines.filterNot { it.id == id }
        persist()
        persistQueue()
    }

    fun editQueued(id: String): QueuedOutbound? {
        if (id == sendingId) return null
        val item = outbound.firstOrNull { it.id == id } ?: return null
        cancelQueued(id)
        return item
    }

    fun archive(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                container.repo.archive(agentId)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = displayError(e)
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                container.repo.deleteAgent(agentId)
                container.forgetLocal(agentId)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = displayError(e)
            }
        }
    }

    suspend fun artifactLink(path: String): String = container.repo.artifactUrl(agentId, path)

    private fun loadLocalArtifacts() {
        artifactHistory = container.artifactHistory.history(agentId)
        pinnedArtifact = container.artifactHistory.visible(agentId)
    }

    private fun ingestArtifacts(items: List<ArtifactItem>) {
        pinnedArtifact = container.artifactHistory.ingest(agentId, items)
        artifactHistory = container.artifactHistory.history(agentId)
    }

    private fun hidePinnedArtifact() {
        container.artifactHistory.hideLatest(agentId)
        pinnedArtifact = null
    }

    private fun refreshArtifacts() {
        viewModelScope.launch {
            ingestArtifacts(runCatching { container.repo.artifacts(agentId) }.getOrDefault(emptyList()))
        }
    }

    fun keepCurrentCheckout() {
        val remote = behind?.remoteSha
        behind = null
        container.chats.ignoreRemote(agentId, remote)
    }

    fun pullNewest(appContext: android.content.Context) {
        val stale = behind ?: return
        behind = null
        container.chats.ignoreRemote(agentId, stale.remoteSha)
        val text = stale.pullPrompt()
        followUp(Prompt(text), text, emptyList(), appContext)
    }

    private suspend fun checkBehind(detail: AgentDetail) {
        val meta = container.chats.meta(agentId)
        val snap = container.catalog.gitSnaps()[agentId]
        val git = run?.git?.branches?.firstOrNull()
        val repoUrl = detail.repos?.firstOrNull()?.url
            ?: snap?.repoUrl
            ?: git?.repoUrl
            ?: meta.repoUrl
            ?: return
        val fullRepo = if (repoUrl.startsWith("http")) repoUrl else "https://$repoUrl"
        val requested = detail.repos?.firstOrNull()?.startingRef
        val baseBranch = meta.baseBranch
            ?: requested?.takeUnless { looksLikeGitSha(it) }
            ?: container.catalog.repos().firstOrNull {
                it.url.contains(gitPath(fullRepo), ignoreCase = true)
            }?.defaultBranch
            ?: "main"
        if (meta.baseBranch == null || meta.startSha == null) {
            val startSha = meta.startSha
                ?: requested?.takeIf { looksLikeGitSha(it) }
            container.chats.setRepoBase(agentId, fullRepo, baseBranch, startSha)
        }
        val stale = runCatching {
            container.repo.repoBehind(
                repoUrl = fullRepo,
                baseBranch = baseBranch,
                agentBranch = git?.branch ?: snap?.branch,
                startSha = meta.startSha ?: requested?.takeIf { looksLikeGitSha(it) },
            )
        }.getOrNull()
        behind = if (stale != null && stale.remoteSha != meta.ignoredRemoteSha) stale else null
    }

    fun cancel() {
        val runId = run?.id ?: return
        viewModelScope.launch {
            try {
                container.repo.cancel(agentId, runId)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = displayError(e)
            }
        }
    }

    private suspend fun flushOutbound() {
        if (busy) return
        val next = outbound.firstOrNull() ?: return
        if (run?.isActive() == true) return
        busy = true
        sendingId = next.id
        lines = lines.map { line ->
            if (line.id == next.id) line.copy(queued = false) else line
        }
        persist()
        error = null
        try {
            val created = container.repo.followUp(
                agentId,
                next.prompt,
                mode = followMode.takeIf { it.isNotBlank() },
                model = followModel.takeIf { it.isNotBlank() }?.let { ModelSelection(it) },
            )
            outbound.removeAll { it.id == next.id }
            persistQueue()
            run = created
            assistantBuf = StringBuilder()
            thinkingBuf = StringBuilder()
            lastEventId = null
            streamedRunId = created.id
            markUserSent(next.id, created.id)
            appContext?.let { ctx ->
                RunWatchScheduler.watch(ctx, agentId, created.id, agent?.name)
            }
            container.notifier.notifyIfNeeded(agentId, agent?.name, created.id, created.status, null)
            startStream(created.id, replay = false)
            startPoll(created.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ApiException && e.isBusy) {
                error = null
                markUsersQueued()
            } else {
                lines = lines.map { line ->
                    if (line.id == next.id) line.copy(queued = true) else line
                }
                persist()
                error = displayError(e)
            }
        } finally {
            sendingId = null
            busy = false
        }
    }

    private fun restoreQueue() {
        if (outbound.isNotEmpty()) return
        val saved = container.drafts.loadQueue(agentId)
        if (saved.isEmpty()) {
            val leftover = lines.filter { it.kind == "user" && it.queued && it.runId == null }
            leftover.forEach { line ->
                outbound.add(QueuedOutbound(line.id, Prompt(line.text), emptyList()))
            }
        } else {
            saved.forEach { item ->
                val attaches = item.attaches.toItems()
                outbound.add(
                    QueuedOutbound(
                        id = item.id,
                        prompt = Attachments.prompt(item.text, attaches),
                        attaches = attaches,
                    ),
                )
            }
        }
        if (outbound.isNotEmpty()) markUsersQueued()
    }

    private fun persistQueue() {
        container.drafts.saveQueue(
            agentId,
            outbound.map { item ->
                QueuedItem(item.id, item.prompt.text, item.attaches.toDraft())
            },
        )
    }

    private suspend fun mergeServerRuns() {
        val runs = runCatching { container.repo.listRuns(agentId) }.getOrDefault(emptyList())
        val existing = lines.associateBy { it.id }.toMutableMap()
        val merged = lines.toMutableList()
        for (item in runs.asReversed()) {
            val full = if (item.isTerminal() && item.result.isNullOrBlank()) {
                runCatching { container.repo.getRun(agentId, item.id) }.getOrDefault(item)
            } else {
                item
            }
            val assistantId = "assistant-${full.id}"
            if (full.result.isNullOrBlank()) continue
            if (coversAssistant(merged, full.id, full.result)) continue
            val line = TranscriptLine(assistantId, "assistant", full.result, full.id)
            if (insertAssistant(merged, line, full.id)) {
                existing[assistantId] = line
            }
        }
        if (merged != lines) {
            lines = coalesceTranscript(merged)
            persist()
        }
    }

    private fun insertAssistant(merged: MutableList<TranscriptLine>, line: TranscriptLine, runId: String): Boolean {
        val userIdx = merged.indexOfLast { it.kind == "user" && (it.runId == runId || it.id == "user-$runId") }
        if (userIdx < 0) {
            merged.add(line)
            return true
        }
        var at = userIdx + 1
        while (at < merged.size && merged[at].kind == "tool") {
            at += 1
        }
        if (at < merged.size && coversAssistant(listOf(merged[at]), runId, line.text)) return false
        merged.add(at, line)
        return true
    }

    private fun coversAssistant(items: List<TranscriptLine>, runId: String, result: String?): Boolean {
        val needle = result?.trim().orEmpty()
        return items.any { line ->
            if (line.kind != "assistant") return@any false
            if (line.id == "assistant-$runId" || line.runId == runId) return@any true
            val text = line.text.trim()
            needle.isNotEmpty() && text == needle
        }
    }

    private fun restoreRun(snap: ConversationSnap) {
        if (run != null) return
        val id = snap.runId ?: return
        run = Run(id = id, status = snap.runStatus)
        if (run?.isActive() == true) streaming = true
    }

    private suspend fun adoptRun(latest: Run) {
        val current = run
        if (current != null && current.isActive() && current.id != latest.id && latest.isTerminal()) {
            attachRun(current.id)
            persist()
            return
        }
        run = latest
        persist()
        if (latest.isActive()) {
            attachRun(latest.id)
            return
        }
        if (streamedRunId == latest.id || streamJob?.isActive != true) {
            streaming = false
            receiving = false
        }
        if (!latest.result.isNullOrBlank()) {
            upsert("assistant-${latest.id}", "assistant", latest.result, latest.id)
        }
        flushOutbound()
    }

    private fun attachRun(runId: String) {
        val live = streamedRunId == runId && streamJob?.isActive == true
        if (!live) startStream(runId, replay = streamedRunId != runId)
        if (pollJob?.isActive != true || run?.id != runId) startPoll(runId)
    }

    private fun startStream(runId: String, replay: Boolean) {
        streamJob?.cancel()
        if (replay || streamedRunId != runId) {
            assistantBuf = StringBuilder()
            thinkingBuf = StringBuilder()
            lastEventId = null
            streamedRunId = runId
        }
        streaming = true
        receiving = false
        streamJob = viewModelScope.launch {
            container.repo.stream(agentId, runId, lastEventId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    val api = e as? ApiException
                    if (api?.isStreamGone == true || e.message?.contains("no longer available", true) == true) {
                        handleStreamGone(runId)
                    } else if (!isCancelMessage(e.message)) {
                        error = displayError(e)
                        streaming = false
                        receiving = false
                    }
                }
                .collect { event ->
                    event.eventId?.let { lastEventId = it }
                    when (event) {
                        is StreamEvent.Assistant -> {
                            receiving = true
                            assistantBuf.append(event.text)
                            upsert("assistant-$runId", "assistant", assistantBuf.toString(), runId)
                        }
                        is StreamEvent.Thinking -> {
                            receiving = true
                            thinkingBuf.append(event.text)
                            upsert("think-$runId", "thinking", thinkingBuf.toString(), runId)
                        }
                        is StreamEvent.ToolCall -> {
                            receiving = true
                            upsert(
                                event.callId ?: "tool-$runId-${event.name}",
                                "tool",
                                "${event.name ?: "tool"} ${event.status ?: ""}".trim(),
                                runId,
                            )
                            container.notifier.notifyApproval(
                                agentId = agentId,
                                agentName = agent?.name,
                                runId = runId,
                                callId = event.callId,
                                name = event.name,
                                status = event.status,
                                args = event.args,
                            )
                        }
                        is StreamEvent.Status -> {
                            run = run?.copy(status = event.status) ?: run
                        }
                        is StreamEvent.Result -> {
                            run = run?.copy(status = event.status, result = event.text)
                            if (!event.text.isNullOrBlank()) {
                                upsert("assistant-$runId", "assistant", event.text, runId)
                            }
                            streaming = false
                            receiving = false
                            container.notifier.notifyIfNeeded(agentId, agent?.name, runId, event.status, event.text)
                            refreshArtifacts()
                            flushOutbound()
                        }
                        is StreamEvent.StreamError -> {
                            if (event.recoverable || event.message == "stream_expired") {
                                handleStreamGone(runId)
                            } else if (event.message != "stream_canceled") {
                                error = event.message
                                streaming = false
                                receiving = false
                            }
                        }
                        StreamEvent.Done -> {
                            streaming = false
                            receiving = false
                            val latest = runCatching { container.repo.getRun(agentId, runId) }.getOrNull()
                            if (latest != null) {
                                run = latest
                                if (!latest.result.isNullOrBlank()) {
                                    upsert("assistant-$runId", "assistant", latest.result, runId)
                                }
                            }
                            refreshArtifacts()
                            flushOutbound()
                        }
                    }
                }
        }
    }

    private fun startWatch() {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                if (!busy) attachLatestRun()
            }
        }
    }

    private suspend fun attachLatestRun() {
        val detail = runCatching { container.repo.getAgent(agentId) }.getOrNull() ?: return
        agent = detail
        val runId = detail.latestRunId ?: return
        val live = runId == streamedRunId && (streaming || streamJob?.isActive == true)
        if (live) return
        if (runId == run?.id && run?.isActive() != true && !streaming) return
        val latest = runCatching { container.repo.getRun(agentId, runId) }.getOrNull() ?: return
        run = latest
        latest.git?.branches?.firstOrNull()?.let { git ->
            container.catalog.saveGit(
                GitSnap(agentId, git.branch, git.prUrl, git.repoUrl),
            )
        }
        if (latest.isActive()) {
            adoptRun(latest)
            return
        }
        receiving = false
        if (!latest.result.isNullOrBlank() && !coversAssistant(lines, runId, latest.result)) {
            upsert("assistant-$runId", "assistant", latest.result, runId)
            refreshArtifacts()
        }
        if (latest.isTerminal()) {
            container.notifier.notifyIfNeeded(agentId, agent?.name, latest.id, latest.status, latest.result)
        }
    }

    private fun startPoll(runId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive && run?.id == runId && run?.isActive() == true) {
                delay(4_000)
                val latest = runCatching { container.repo.getRun(agentId, runId) }.getOrNull() ?: continue
                run = latest
                if (!latest.isActive()) {
                    streaming = false
                    receiving = false
                    if (!latest.result.isNullOrBlank()) {
                        upsert("assistant-$runId", "assistant", latest.result, runId)
                    }
                    container.notifier.notifyIfNeeded(
                        agentId,
                        agent?.name,
                        latest.id,
                        latest.status,
                        latest.result,
                    )
                    refreshArtifacts()
                    flushOutbound()
                    break
                }
            }
        }
    }

    private suspend fun handleStreamGone(runId: String) {
        val latest = runCatching { container.repo.getRun(agentId, runId) }.getOrNull()
        if (latest != null) {
            run = latest
            if (!latest.result.isNullOrBlank()) {
                upsert("assistant-$runId", "assistant", latest.result, runId)
            }
            if (latest.isActive()) {
                startStream(runId, replay = lastEventId == null)
                return
            }
        }
        streaming = false
        receiving = false
        error = null
        flushOutbound()
    }

    private fun upsert(
        id: String,
        kind: String,
        text: String,
        runId: String? = null,
        queued: Boolean = false,
        thumbs: List<String> = emptyList(),
    ) {
        val next = lines.toMutableList()
        val idx = next.indexOfFirst { existing ->
            existing.id == id ||
                (kind == "assistant" && runId != null && existing.kind == "assistant" && existing.runId == runId)
        }
        val existingThumbs = next.getOrNull(idx)?.thumbs.orEmpty()
        val line = TranscriptLine(id, kind, text, runId, queued, thumbs.ifEmpty { existingThumbs })
        if (idx >= 0) next[idx] = line else next.add(line)
        lines = coalesceTranscript(next)
        persist()
    }

    private fun markUserSent(localId: String, runId: String) {
        val next = lines.toMutableList()
        val idx = next.indexOfFirst { it.id == localId }
        if (idx >= 0) {
            next[idx] = next[idx].copy(id = "user-$runId", runId = runId, queued = false)
            lines = next
            persist()
        }
    }

    private fun markUsersQueued() {
        lines = lines.map { line ->
            if (line.kind == "user" && line.runId == null) line.copy(queued = true) else line
        }
        persist()
    }

    private fun persist(immediate: Boolean = false) {
        container.conversations.save(
            agentId,
            lines,
            runId = run?.id,
            runStatus = run?.status,
            immediate = immediate,
        )
    }

    private fun displayError(e: Throwable): String {
        return when (e) {
            is ApiException -> e.displayMessage()
            else -> e.message?.takeIf { !isCancelMessage(it) } ?: "Request failed"
        }
    }

    private fun isCancelMessage(message: String?): Boolean {
        val text = message.orEmpty()
        return text.contains("canceled due to", ignoreCase = true) ||
            text.contains("cancelled due to", ignoreCase = true) ||
            text == "stream_canceled"
    }

    override fun onCleared() {
        persistNow()
        streamJob?.cancel()
        pollJob?.cancel()
        watchJob?.cancel()
        super.onCleared()
    }
}

data class QueuedOutbound(
    val id: String,
    val prompt: Prompt,
    val attaches: List<AttachItem> = emptyList(),
)

class ThreadVmFactory(
    private val container: AppContainer,
    private val agentId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ThreadViewModel(container, agentId) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    container: AppContainer,
    agentId: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onRemoved: () -> Unit = onBack,
    modifier: Modifier = Modifier,
) {
    val vm: ThreadViewModel = viewModel(
        key = agentId,
        factory = ThreadVmFactory(container, agentId),
    )
    var draft by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(container.chats.isFavorite(agentId)) }
    var localTitle by remember { mutableStateOf(container.chats.title(agentId)) }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var artifactHistoryOpen by remember { mutableStateOf(false) }
    var chatPropertiesOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var attaches by remember { mutableStateOf<List<AttachItem>>(emptyList()) }
    var models by remember { mutableStateOf<List<ModelItem>>(emptyList()) }
    var draftReady by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val listState = remember(agentId) { LazyListState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val working = showWorkBar(
        lines = vm.lines,
        receiving = vm.receiving,
        busy = vm.busy,
        agentStatus = vm.agent?.status,
        runStatus = vm.run?.status,
    )
    val canKill = isLiveStatus(vm.agent?.status) || vm.run?.isActive() == true
    val title = localTitle ?: vm.agent?.name ?: "Agent"
    var showTools by remember { mutableStateOf(container.store.showToolCalls) }
    var showThinking by remember { mutableStateOf(container.store.showThinking) }
    var showMicrophone by remember { mutableStateOf(container.store.showMicrophone) }
    val rows = remember(vm.lines, showTools, showThinking) {
        groupChatRows(vm.lines, showTools, showThinking)
    }
    val currentRunId = vm.run?.id
    val hasReply = currentRunId != null && rows.any { row ->
        row is ChatRow.Message && row.line.kind == "assistant" &&
            (row.line.runId == currentRunId || row.line.id == "assistant-$currentRunId")
    }
    val showTyping = working && !hasReply
    val waitText = if (showTyping) {
        waitCopy(
            receiving = vm.receiving,
            agentStatus = vm.agent?.status,
            runStatus = vm.run?.status,
            envType = vm.agent?.env?.type,
        )
    } else {
        null
    }

    fun openArtifact(item: ArtifactItem) {
        scope.launch {
            val url = runCatching { vm.artifactLink(item.path) }.getOrNull()
            if (!url.isNullOrBlank()) {
                SafeLinks.open(context, url)
            }
        }
    }

    fun saveArtifact(item: ArtifactItem) {
        scope.launch {
            val url = runCatching { vm.artifactLink(item.path) }.getOrNull()
            if (!url.isNullOrBlank()) {
                ArtifactSaver.enqueue(context, url, item.fileName())
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        vm.persistNow()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        showTools = container.store.showToolCalls
        showThinking = container.store.showThinking
        showMicrophone = container.store.showMicrophone
        vm.refresh()
    }

    DisposableEffect(agentId) {
        VisibleAgent.set(agentId)
        onDispose { VisibleAgent.set(null) }
    }

    if (showBack) BackHandler(onBack = onBack)

    LaunchedEffect(agentId) {
        val saved = container.drafts.load(agentId)
        draft = saved.text
        vm.followMode = saved.mode
        vm.followModel = saved.modelId
        attaches = saved.toItems()
        models = runCatching { container.repo.models() }.getOrDefault(emptyList())
        draftReady = true
    }

    LaunchedEffect(draft, vm.followMode, vm.followModel, attaches, draftReady) {
        if (!draftReady) return@LaunchedEffect
        container.drafts.save(
            agentId,
            ChatDraft(draft, vm.followMode, vm.followModel, attaches.toDraft()),
        )
    }

    var stickToBottom by remember(agentId) { mutableStateOf(true) }
    var programmaticScroll by remember(agentId) { mutableStateOf(false) }
    val tailLen = rows.filterIsInstance<ChatRow.Message>().lastOrNull()?.line?.text?.length ?: 0

    suspend fun snapToBottom() {
        programmaticScroll = true
        stickToBottom = true
        try {
            listState.scrollToBottom()
        } finally {
            programmaticScroll = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.isScrollInProgress to listState.isPinnedToBottom()
        }.collect { (scrolling, pinned) ->
            if (programmaticScroll) return@collect
            if (scrolling && !pinned) stickToBottom = false
            else if (!scrolling && pinned) stickToBottom = true
        }
    }

    LaunchedEffect(agentId, rows.size, showTyping, vm.pinnedArtifact != null, tailLen, stickToBottom) {
        if (stickToBottom && !listState.isScrollInProgress) {
            snapToBottom()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (canKill) {
                            DropdownMenuItem(
                                text = { Text("Kill process") },
                                onClick = {
                                    menu = false
                                    vm.cancel()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menu = false
                                renaming = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (favorite) "Unfavorite" else "Favorite") },
                            onClick = {
                                menu = false
                                favorite = container.chats.toggleFavorite(agentId)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                menu = false
                                ChatShare.send(context, title, agentId, vm.agent?.url)
                            },
                        )
                        if (isCloudEnvType(vm.agent?.env?.type)) {
                            DropdownMenuItem(
                                text = { Text("Open cloud computer") },
                                onClick = {
                                    menu = false
                                    SafeLinks.open(context, ChatShare.url(agentId, vm.agent?.url))
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Chat properties") },
                            onClick = {
                                menu = false
                                chatPropertiesOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Artifacts history") },
                            onClick = {
                                menu = false
                                artifactHistoryOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = {
                                menu = false
                                vm.archive(onRemoved)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menu = false
                                confirmDelete = true
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (working) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (vm.error != null) {
                Text(
                    vm.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { row ->
                        when (row) {
                            is ChatRow.Message -> row.line.id
                            is ChatRow.Tools -> "tools-${row.id}"
                        }
                    }) { row ->
                        when (row) {
                            is ChatRow.Message ->                             TranscriptBubble(
                                line = row.line,
                                onCopy = { text -> copyMessage(context, text) },
                                onQuote = { text ->
                                    val block = quoteBlock(text)
                                    draft = if (draft.isBlank()) "$block\n\n" else "${draft.trimEnd()}\n\n$block\n\n"
                                },
                                onEditQueued = if (row.line.queued) {
                                    {
                                        vm.editQueued(row.line.id)?.let { item ->
                                            draft = item.prompt.text
                                            attaches = item.attaches
                                        }
                                    }
                                } else {
                                    null
                                },
                                onCancelQueued = if (row.line.queued) {
                                    { vm.cancelQueued(row.line.id) }
                                } else {
                                    null
                                },
                            )
                            is ChatRow.Tools -> ToolCallsBlock(
                                tools = row.tools,
                                onCopy = { text -> copyMessage(context, text) },
                            )
                        }
                    }
                    val latest = vm.pinnedArtifact
                    if (latest != null) {
                        item(key = "artifact-${latest.path}") {
                            LatestArtifactCard(
                                item = latest,
                                onOpen = { openArtifact(latest) },
                                onSave = { saveArtifact(latest) },
                            )
                        }
                    }
                    if (showTyping) {
                        item(key = "typing") {
                            TypingBubble(detail = waitText)
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !stickToBottom,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 8.dp),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch { snapToBottom() }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Jump to latest")
                    }
                }
            }
            AttachChips(items = attaches, onItems = { attaches = it })
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = vm.followMode != "plan",
                    onClick = { vm.followMode = "agent" },
                    label = { Text("Agent") },
                )
                FilterChip(
                    selected = vm.followMode == "plan",
                    onClick = { vm.followMode = "plan" },
                    label = { Text("Plan") },
                )
                val modelLabel = models.firstOrNull { it.id == vm.followModel }?.displayName
                    ?: vm.followModel.ifBlank { "Model" }
                Box {
                    FilterChip(
                        selected = vm.followModel.isNotBlank(),
                        onClick = { modelMenu = true },
                        label = {
                            Text(
                                modelLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        },
                    )
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Account default") },
                            onClick = {
                                vm.followModel = ""
                                modelMenu = false
                            },
                        )
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName ?: model.id) },
                                onClick = {
                                    vm.followModel = model.id
                                    modelMenu = false
                                },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachButton(items = attaches, onItems = { attaches = it })
                if (showMicrophone) {
                    VoiceButton(enabled = true) { spoken ->
                        draft = if (draft.isBlank()) spoken else "$draft $spoken"
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("chat", maxLines = 1) },
                    maxLines = 4,
                )
                IconButton(
                    onClick = {
                        val ready = attaches.filter { it.ok }
                        if (draft.isNotBlank() || ready.isNotEmpty()) {
                            val prompt = Attachments.prompt(draft, ready)
                            val label = Attachments.label(draft, ready)
                            vm.followUp(
                                prompt,
                                label,
                                ready.mapNotNull { it.thumbPath },
                                context.applicationContext,
                                ready,
                            )
                            draft = ""
                            attaches = emptyList()
                            container.drafts.clear(agentId)
                            scope.launch { snapToBottom() }
                        }
                    },
                    enabled = draft.isNotBlank() || attaches.any { it.ok },
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
                }
            }
        }
    }
    if (renaming) {
        RenameChatDialog(
            current = title,
            onDismiss = { renaming = false },
            onConfirm = { name ->
                container.renameChat(agentId, name)
                localTitle = name
                renaming = false
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete chat") },
            text = { Text("Permanently delete this agent on Cursor. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vm.delete(onRemoved)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
    val stale = vm.behind
    if (stale != null) {
        BehindBranchDialog(
            stale = stale,
            onKeep = { vm.keepCurrentCheckout() },
            onPull = { vm.pullNewest(context.applicationContext) },
        )
    }
    if (chatPropertiesOpen) {
        val git = vm.run?.git?.branches?.firstOrNull()
        val cached = container.catalog.gitSnaps()[agentId]
        val env = listOfNotNull(vm.agent?.env?.type, vm.agent?.env?.name)
            .joinToString(" · ")
            .ifBlank { null }
        ChatPropertiesDialog(
            title = title,
            status = vm.agent?.status ?: vm.run?.status,
            env = env,
            branch = git?.branch ?: cached?.branch,
            repoUrl = git?.repoUrl ?: cached?.repoUrl,
            prUrl = git?.prUrl ?: cached?.prUrl,
            tokens = vm.usage?.totalUsage?.totalTokens,
            onOpenUrl = { url ->
                SafeLinks.open(context, url)
            },
            onDismiss = { chatPropertiesOpen = false },
        )
    }
    if (artifactHistoryOpen) {
        ArtifactHistoryDialog(
            items = vm.artifactHistory,
            onOpen = { openArtifact(it) },
            onSave = { saveArtifact(it) },
            onDismiss = { artifactHistoryOpen = false },
        )
    }
}

private sealed class ChatRow {
    data class Message(val line: TranscriptLine) : ChatRow()
    data class Tools(val id: String, val tools: List<TranscriptLine>) : ChatRow()
}

private fun groupChatRows(
    lines: List<TranscriptLine>,
    showTools: Boolean,
    showThinking: Boolean,
): List<ChatRow> {
    val rows = ArrayList<ChatRow>(lines.size)
    val pendingTools = ArrayList<TranscriptLine>()
    val pendingThink = LinkedHashMap<String, TranscriptLine>()

    fun flushTools() {
        if (pendingTools.isEmpty()) return
        if (showTools) {
            rows += ChatRow.Tools(pendingTools.first().id, pendingTools.toList())
        }
        pendingTools.clear()
    }

    fun thinkKey(line: TranscriptLine): String {
        return line.runId?.takeIf { it.isNotBlank() }
            ?: line.id.removePrefix("think-").takeIf { line.id.startsWith("think-") && it.isNotBlank() }
            ?: line.id
    }

    fun asstKey(line: TranscriptLine): String? {
        return line.runId?.takeIf { it.isNotBlank() }
            ?: line.id.removePrefix("assistant-").takeIf { line.id.startsWith("assistant-") && it.isNotBlank() }
    }

    fun hasAssistant(run: String): Boolean {
        return rows.any { row ->
            row is ChatRow.Message && row.line.kind == "assistant" &&
                (row.line.runId == run || row.line.id == "assistant-$run")
        }
    }

    fun emitThink(line: TranscriptLine) {
        if (showThinking && line.text.isNotBlank()) {
            rows += ChatRow.Message(line)
        }
    }

    fun flushThink(run: String? = null) {
        if (run != null) {
            pendingThink.remove(run)?.let(::emitThink)
            return
        }
        if (pendingThink.isEmpty()) return
        pendingThink.values.forEach(::emitThink)
        pendingThink.clear()
    }

    for (line in lines) {
        when (line.kind) {
            "tool" -> if (showTools) pendingTools += line
            "thinking" -> {
                flushTools()
                val run = thinkKey(line)
                if (hasAssistant(run)) emitThink(line) else pendingThink[run] = line
            }
            "assistant" -> {
                flushTools()
                rows += ChatRow.Message(line)
                flushThink(asstKey(line))
            }
            "user", "notice" -> {
                flushTools()
                flushThink()
                rows += ChatRow.Message(line)
            }
            else -> {
                flushTools()
                rows += ChatRow.Message(line)
            }
        }
    }
    flushTools()
    flushThink()
    return rows
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TranscriptBubble(
    line: TranscriptLine,
    onCopy: (String) -> Unit = {},
    onQuote: (String) -> Unit = {},
    onEditQueued: (() -> Unit)? = null,
    onCancelQueued: (() -> Unit)? = null,
) {
    if (line.kind == "thinking") {
        ThinkingBlock(line, onCopy = onCopy)
        return
    }
    if (line.kind == "notice") {
        NoticeBlock(line, onCopy = onCopy)
        return
    }
    val isUser = line.kind == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = if (isUser) {
        RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    }
    val label = when {
        line.kind == "user" && line.queued -> "You · queued"
        line.kind == "user" -> "You"
        else -> "Agent"
    }
    var menu by remember(line.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val copyText = line.text.trim()
    val quotable = line.kind == "user" || line.kind == "assistant"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Box {
            Box(
                modifier = Modifier
                    .widthIn(max = 520.dp, min = 48.dp)
                    .clip(shape)
                    .background(bubbleColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (copyText.isBlank() && onEditQueued == null && onCancelQueued == null) {
                                return@combinedClickable
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (line.thumbs.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            line.thumbs.forEach { path ->
                                val bmp = remember(path) { BitmapFactory.decodeFile(path) }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }
                    }
                    Text(line.text, color = textColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
            MessageClipMenu(
                expanded = menu,
                onDismiss = { menu = false },
                copyText = copyText,
                quoteText = line.text.takeIf { quotable },
                onCopy = onCopy,
                onQuote = onQuote,
                onEdit = onEditQueued,
                onCancel = onCancelQueued,
            )
        }
        if (onEditQueued != null || onCancelQueued != null) {
            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (onEditQueued != null) {
                    TextButton(onClick = onEditQueued) { Text("Edit") }
                }
                if (onCancelQueued != null) {
                    TextButton(onClick = onCancelQueued) { Text("Cancel") }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoticeBlock(
    line: TranscriptLine,
    onCopy: (String) -> Unit = {},
) {
    var menu by remember(line.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val copyText = line.text.trim()
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            "Note",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Box {
            Text(
                line.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (copyText.isBlank()) return@combinedClickable
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            MessageClipMenu(
                expanded = menu,
                onDismiss = { menu = false },
                copyText = copyText,
                quoteText = null,
                onCopy = onCopy,
                onQuote = {},
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThinkingBlock(
    line: TranscriptLine,
    onCopy: (String) -> Unit = {},
) {
    var open by remember(line.id) { mutableStateOf(false) }
    var menu by remember(line.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val copyText = line.text.trim()
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Box {
            Row(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .combinedClickable(
                        onClick = { open = !open },
                        onLongClick = {
                            if (copyText.isBlank()) return@combinedClickable
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Thinking",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (open) {
                        Text(
                            line.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                Icon(
                    imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse thinking" else "Expand thinking",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MessageClipMenu(
                expanded = menu,
                onDismiss = { menu = false },
                copyText = copyText,
                quoteText = null,
                onCopy = onCopy,
                onQuote = {},
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolCallsBlock(
    tools: List<TranscriptLine>,
    onCopy: (String) -> Unit = {},
) {
    var open by remember(tools.firstOrNull()?.id ?: "tools") { mutableStateOf(false) }
    var menu by remember(tools.firstOrNull()?.id ?: "tools") { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val copyText = tools.joinToString("\n") { it.text }.trim()
    val title = if (tools.size == 1) {
        tools.first().text
    } else {
        "${tools.size} tool calls"
    }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Box {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(
                    onClick = { open = !open },
                    onLongClick = {
                        if (copyText.isBlank()) return@combinedClickable
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Tools",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse tools" else "Expand tools",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tools.forEach { tool ->
                        Text(
                            tool.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
            MessageClipMenu(
                expanded = menu,
                onDismiss = { menu = false },
                copyText = copyText,
                quoteText = null,
                onCopy = onCopy,
                onQuote = {},
            )
        }
    }
}

@Composable
private fun TypingBubble(detail: String? = null) {
    val transition = rememberInfiniteTransition(label = "typing")
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            "Agent",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (!detail.isNullOrBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 380,
                            delayMillis = index * 140,
                            easing = FastOutSlowInEasing,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot-$index",
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun MessageClipMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    copyText: String,
    quoteText: String?,
    onCopy: (String) -> Unit,
    onQuote: (String) -> Unit,
    onEdit: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Copy") },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            onClick = {
                onDismiss()
                onCopy(copyText)
            },
        )
        if (!quoteText.isNullOrBlank()) {
            DropdownMenuItem(
                text = { Text("Quote") },
                leadingIcon = {
                    Icon(Icons.Outlined.FormatQuote, contentDescription = null)
                },
                onClick = {
                    onDismiss()
                    onQuote(quoteText)
                },
            )
        }
        if (onEdit != null) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    onDismiss()
                    onEdit()
                },
            )
        }
        if (onCancel != null) {
            DropdownMenuItem(
                text = { Text("Cancel") },
                onClick = {
                    onDismiss()
                    onCancel()
                },
            )
        }
    }
}

private fun copyMessage(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("message", text))
}

internal fun quoteBlock(text: String): String {
    return text.trim().lines().joinToString("\n") { line ->
        if (line.isBlank()) ">" else "> $line"
    }
}

private suspend fun LazyListState.scrollToBottom() {
    val last = layoutInfo.totalItemsCount - 1
    if (last < 0) return
    scrollToItem(last)
    val info = layoutInfo
    val lastItem = info.visibleItemsInfo.lastOrNull() ?: return
    val overflow = lastItem.offset + lastItem.size - info.viewportEndOffset
    if (overflow != 0) {
        scrollBy(overflow.toFloat())
    }
}

/** True only when the last row's bottom is near the viewport bottom. */
private fun LazyListState.isPinnedToBottom(thresholdPx: Int = 80): Boolean {
    val info = layoutInfo
    val last = info.totalItemsCount - 1
    if (last < 0) return true
    val lastItem = info.visibleItemsInfo.lastOrNull() ?: return false
    if (lastItem.index != last) return false
    val gap = info.viewportEndOffset - (lastItem.offset + lastItem.size)
    return gap >= -thresholdPx
}
