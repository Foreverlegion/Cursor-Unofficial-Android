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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import com.cursorandroid.app.ui.theme.LocalAppearance
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.ui.AppInsets
import com.cursorandroid.app.ui.scaffoldBars
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
import com.cursorandroid.app.data.api.AgentConversation
import com.cursorandroid.app.data.api.ConversationMessage
import com.cursorandroid.app.data.api.isWorking
import com.cursorandroid.app.data.repo.ConversationSnap
import com.cursorandroid.app.data.repo.coalesceTranscript
import com.cursorandroid.app.data.repo.RunSettled
import com.cursorandroid.app.data.repo.mergeConversationTranscript
import com.cursorandroid.app.data.repo.runsOldestFirst
import com.cursorandroid.app.data.repo.settledAgentStatus
import com.cursorandroid.app.data.repo.mergeRunTranscript
import com.cursorandroid.app.data.repo.mergeTranscript
import com.cursorandroid.app.data.api.isTerminal
import com.cursorandroid.app.data.notify.ApprovalCopy
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.notify.VisibleAgent
import com.cursorandroid.app.data.repo.RunStopper
import com.cursorandroid.app.data.repo.StopOutcome
import com.cursorandroid.app.data.repo.TranscriptLine
import com.cursorandroid.app.data.repo.visibleUserText
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.data.api.defaultParams
import com.cursorandroid.app.data.repo.ArtifactSaver
import com.cursorandroid.app.data.repo.AttachItem
import com.cursorandroid.app.data.repo.Attachments
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.data.repo.ChatDraft
import com.cursorandroid.app.data.repo.ChatShare
import com.cursorandroid.app.data.repo.QueuedItem
import com.cursorandroid.app.data.repo.QueueProbe
import com.cursorandroid.app.data.repo.applyQueuedFlags
import com.cursorandroid.app.data.repo.leftoverLocalLines
import com.cursorandroid.app.data.repo.lineShowsQueued
import com.cursorandroid.app.data.repo.staleQueueIds
import com.cursorandroid.app.data.repo.RepoBehind
import com.cursorandroid.app.data.repo.looksLikeGitSha
import com.cursorandroid.app.data.repo.toDraft
import com.cursorandroid.app.data.repo.toItems
import com.cursorandroid.app.ui.chat.AttachButton
import com.cursorandroid.app.ui.chat.AttachChips
import com.cursorandroid.app.ui.chat.ModelParamRow
import com.cursorandroid.app.ui.chat.RenameChatDialog
import com.cursorandroid.app.ui.chat.VoiceButton
import com.cursorandroid.app.ui.status.PlayColors
import com.cursorandroid.app.ui.status.foreground
import com.cursorandroid.app.ui.status.runIndicator
import com.cursorandroid.app.ui.status.repoNameOnly
import com.cursorandroid.app.ui.status.shortRepo
import com.cursorandroid.app.ui.status.threadSubtitle
import com.cursorandroid.app.ui.status.toolCallParts
import com.cursorandroid.app.ui.status.toolCallText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

private const val STOPPING = "Stopping..."
private const val STOP_LOOKUP_LIMIT = 5
private const val PROMPT_LOOKUPS = 6

data class SnackEvent(val seq: Int, val text: String, val indefinite: Boolean = false)

internal fun stopMessage(outcome: StopOutcome): String = when (outcome) {
    StopOutcome.Stopped -> "Stopped"
    StopOutcome.AlreadyFinished -> "Run already finished"
    is StopOutcome.Failed -> "Couldn't stop: ${outcome.reason}"
}

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
    var snack by mutableStateOf<SnackEvent?>(null)
        private set
    private var snackSeq = 0
    private var stopping = false
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
    var pendingPrUrl by mutableStateOf<String?>(null)
        private set
    var modelBook by mutableStateOf(container.runModels.load(agentId))
        private set

    private fun noteApiModels(detail: AgentDetail? = null, runs: List<Run> = emptyList()) {
        val fromRuns = runs.mapNotNull { item -> item.model?.let { item.id to it } }.toMap()
        if (fromRuns.isEmpty() && detail?.model == null) return
        modelBook = container.runModels.recordApi(agentId, fromRuns, detail?.model)
    }

    fun consumePendingPr() {
        pendingPrUrl = null
    }
    var behind by mutableStateOf<RepoBehind?>(null)
        private set
    var approvalPending by mutableStateOf(false)
        private set

    private var streamJob: Job? = null
    private var pollJob: Job? = null
    private var watchJob: Job? = null
    private var assistantBuf = StringBuilder()
    private var thinkingBuf = StringBuilder()
    private var lastEventId: String? = null
    private var streamedRunId: String? = null
    private val pendingApprovals = HashSet<String>()
    private val outbound = ArrayList<QueuedOutbound>()
    private var sendingId: String? = null
    private var appContext: android.content.Context? = null
    private val refreshLock = Mutex()
    private val settleLock = Mutex()
    private val settledRuns = HashSet<String>()
    private var runOrder: List<String> = emptyList()
    private var settledKey: String? = null
    private var afterNonAssistant = false
    private val lineGate = Any()

    @Volatile
    private var refreshedAt = 0L

    @Volatile
    var foreground = true

    init {
        refresh(force = true)
        startWatch()
        viewModelScope.launch {
            container.runSettle.settled.collect { map -> map[agentId]?.let { onSettled(it) } }
        }
    }

    private suspend fun onSettled(ended: RunSettled) {
        val current = run
        if (current != null && current.id != ended.runId) return
        val fetched = runCatching { container.repo.getRun(agentId, ended.runId) }.getOrNull()
        val latest = fetched ?: (current ?: Run(id = ended.runId)).copy(
            status = ended.status,
            result = ended.result ?: current?.result,
        )
        if (latest.isActive()) return
        settleRun(latest)
    }

    private suspend fun settleRun(ended: Run) {
        settleLock.withLock {
            val key = "${ended.id}:${ended.status}"
            if (settledKey == key) return
            val current = run
            if (current != null && current.id != ended.id && current.isActive()) return
            if (streamedRunId == ended.id) streamJob?.cancel()
            clearApprovals()
            streaming = false
            receiving = false
            run = ended
            settledRuns += ended.id
            if (!ended.result.isNullOrBlank()) {
                upsert("assistant-${ended.id}", "assistant", ended.result, ended.id)
            }
            persist(immediate = true)
            runCatching { container.repo.getAgent(agentId) }.getOrNull()?.let { detail ->
                agent = detail
                noteApiModels(detail = detail)
            }
            var covered = false
            for (attempt in 0 until SETTLE_FETCHES) {
                if (attempt > 0) delay(SETTLE_RETRY_MS)
                val convo = mergeConversation()
                if (convo != null && conversationCovers(convo.messages, ended.result)) {
                    covered = true
                    break
                }
            }
            if (!covered) mergeConversation()
            refreshArtifacts()
            settledKey = key
            persist(immediate = true)
        }
        flushOutbound()
    }

    fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - refreshedAt in 0 until REFRESH_MIN_GAP_MS) return
        refreshedAt = now
        viewModelScope.launch {
            refreshLock.withLock {
                try {
                    error = null
                    val snap = container.conversations.loadSnap(agentId)
                    synchronized(lineGate) {
                        lines = mergeTranscript(lines, snap.lines)
                    }
                    restoreQueue()
                    loadLocalArtifacts()
                    restoreRun(snap)
                    persist(immediate = true)
                    val detail = container.repo.getAgent(agentId)
                    agent = detail
                    noteApiModels(detail = detail)
                    val runId = detail.latestRunId
                    // Runs, conversation and artifacts of a finished latest run do not change; served from disk.
                    val settled = runId != null && !detail.isWorking() &&
                        container.catalog.settledRun(agentId) == runId && lines.isNotEmpty() && outbound.isEmpty()
                    if (!settled) {
                        val serverRuns = mergeServerRuns()
                        val serverTexts = mergeConversationHistory()
                        dropDeliveredQueue(
                            serverRuns,
                            serverTexts,
                            idle = !detail.isWorking() && run?.isActive() != true && !busy,
                        )
                    }
                    if (runId != null) {
                        val latest = container.repo.getRun(agentId, runId)
                        noteApiModels(runs = listOf(latest))
                        latest.git?.branches?.firstOrNull()?.let { git ->
                            container.catalog.saveGit(
                                GitSnap(agentId, git.branch, git.prUrl, git.repoUrl),
                            )
                            container.chats.claimFinishedPr(container.catalog.gitSnaps())?.let { pendingPrUrl = it }
                        }
                        usage = container.repo.settledUsage(agentId, runId, latest.isTerminal())
                        adoptRun(latest)
                        if (!settled) {
                            ingestArtifacts(runCatching { container.repo.artifacts(agentId) }.getOrDefault(emptyList()))
                        }
                        container.catalog.saveSettledRun(agentId, runId.takeIf { latest.isTerminal() && !detail.isWorking() })
                    } else {
                        if (run?.isActive() == true) attachRun(run!!.id)
                        ingestArtifacts(runCatching { container.repo.artifacts(agentId) }.getOrDefault(emptyList()))
                    }
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
    var followParams by mutableStateOf<List<ModelParam>>(emptyList())

    fun followUp(
        prompt: Prompt,
        label: String,
        thumbs: List<String>,
        appContext: android.content.Context,
        attaches: List<AttachItem> = emptyList(),
        caption: String = "",
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
        outbound.add(QueuedOutbound(localId, prompt, attaches, caption, at = System.currentTimeMillis()))
        persistQueue()
        viewModelScope.launch {
            if (run?.isActive() == true) {
                val item = outbound.lastOrNull { it.id == localId }
                if (item != null && trySteer(item)) {
                    outbound.removeAll { it.id == localId }
                    persistQueue()
                    lines = lines.map { line ->
                        if (line.id == localId) line.copy(queued = false) else line
                    }
                    persist()
                    return@launch
                }
            }
            flushOutbound()
        }
    }

    private suspend fun trySteer(item: QueuedOutbound): Boolean {
        val runId = run?.id ?: return false
        if (run?.isActive() != true) return false
        return runCatching { container.repo.steer(agentId, runId, outboundPrompt(item)) }.getOrDefault(false)
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
        pinnedArtifact = container.artifactHistory.ingest(agentId, items, artifactProducedAt())
        artifactHistory = container.artifactHistory.history(agentId)
    }

    private fun artifactProducedAt(): String? {
        return run?.updatedAt?.takeIf { it.isNotBlank() }
            ?: run?.createdAt?.takeIf { it.isNotBlank() }
            ?: agent?.updatedAt?.takeIf { it.isNotBlank() }
            ?: agent?.createdAt?.takeIf { it.isNotBlank() }
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
        val now = System.currentTimeMillis()
        val last = behindCheckedAt[agentId] ?: 0L
        if (now - last in 0 until BEHIND_MIN_GAP_MS) return
        behindCheckedAt[agentId] = now
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

    private val stopper = RunStopper(
        cancel = { id, runId -> container.repo.cancel(id, runId) },
        listRuns = { id -> container.repo.listRuns(id, STOP_LOOKUP_LIMIT) },
    )

    fun cancel() {
        if (stopping) return
        stopping = true
        viewModelScope.launch {
            try {
                say(STOPPING, indefinite = true)
                val outcome = stopper.stop(agentId, run?.id, agent?.latestRunId)
                say(stopMessage(outcome))
                refresh(force = true)
            } finally {
                stopping = false
            }
        }
    }

    private fun say(text: String, indefinite: Boolean = false) {
        snack = SnackEvent(++snackSeq, text, indefinite)
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
            val sent = followModel.takeIf { it.isNotBlank() }?.let {
                ModelSelection(it, followParams.takeIf { params -> params.isNotEmpty() })
            }
            val created = container.repo.followUp(
                agentId,
                outboundPrompt(next),
                mode = followMode.takeIf { it.isNotBlank() },
                model = sent,
            )
            outbound.removeAll { it.id == next.id }
            persistQueue()
            Attachments.forget(next.attaches)
            run = created
            container.runSettle.clear(agentId)
            modelBook = container.runModels.recordRun(
                agentId,
                created.id,
                sent,
                explicit = false,
            )
            assistantBuf = StringBuilder()
            thinkingBuf = StringBuilder()
            lastEventId = null
            streamedRunId = created.id
            markUserSent(next.id, created.id)
            appContext?.let { ctx ->
                RunWatchScheduler.watch(ctx, agentId, created.id, agent?.name, created.status)
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
        if (outbound.isEmpty()) {
            val saved = container.drafts.loadQueue(agentId)
            if (saved.isEmpty()) {
                leftoverLocalLines(lines).forEach { line ->
                    val thumbs = line.thumbs.mapIndexed { index, path ->
                        Attachments.fromCache(path, "image-$index.jpg", "image/jpeg")
                    }.filter { it.ok }
                    outbound.add(
                        QueuedOutbound(
                            id = line.id,
                            prompt = Attachments.prompt(line.text, thumbs),
                            attaches = thumbs,
                            caption = line.text,
                        ),
                    )
                }
            } else {
                saved.forEach { item ->
                    val attaches = item.attaches.toItems()
                    val caption = item.caption.ifBlank { item.text }
                    outbound.add(
                        QueuedOutbound(
                            id = item.id,
                            prompt = Attachments.prompt(caption, attaches),
                            attaches = attaches,
                            caption = caption,
                            at = item.at,
                        ),
                    )
                }
            }
        }
        syncQueuedFlags()
    }

    private fun syncQueuedFlags() {
        synchronized(lineGate) {
            val next = applyQueuedFlags(lines, outbound.filter { it.id != sendingId }.map { it.id }.toSet())
            if (next !== lines) {
                lines = next
                persist()
            }
        }
    }

    private fun dropDeliveredQueue(runs: List<Run>, serverTexts: List<String>, idle: Boolean) {
        if (outbound.isEmpty()) return
        val stale = staleQueueIds(
            outbound.map { QueueProbe(it.id, listOf(it.caption, it.prompt.text), it.at) },
            runs,
            serverTexts,
            idle,
        )
        if (stale.isEmpty()) return
        outbound.removeAll { it.id in stale }
        persistQueue()
        syncQueuedFlags()
    }

    private fun outboundPrompt(item: QueuedOutbound): Prompt {
        val ready = item.attaches.filter { it.ok }.ifEmpty {
            item.attaches.mapNotNull { attach ->
                val thumb = attach.thumbPath ?: return@mapNotNull null
                Attachments.fromCache(thumb, attach.name, "image/jpeg").takeIf { it.ok }
            }
        }
        return if (ready.any { it.image != null || it.excerpt != null }) {
            Attachments.prompt(item.caption, ready)
        } else {
            item.prompt
        }
    }

    private fun persistQueue() {
        container.drafts.saveQueue(
            agentId,
            outbound.map { item ->
                QueuedItem(item.id, item.prompt.text, item.attaches.toDraft(), item.caption, item.at)
            },
        )
    }

    private var probedPrompts = false

    var runHints by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private val promptChecked = HashSet<String>()

    private fun noteRunPrompt(item: Run) {
        val text = item.prompt?.text
        if (text.isNullOrBlank()) return
        promptChecked += item.id
        val hint = promptModelHint(visibleUserText(text)) ?: return
        if (runHints[item.id] != hint) runHints = runHints + (item.id to hint)
    }

    /** The run's own prompt carries the hint. List Runs may omit the prompt, so Get Run fills it in. */
    suspend fun ensureRunHint(runId: String?) {
        val id = runId?.takeIf { it.isNotBlank() && it !in promptChecked } ?: return
        val full = runCatching { container.repo.getRun(agentId, id) }.getOrNull() ?: return
        noteRunPrompt(full)
        if (full.prompt?.text.isNullOrBlank()) promptChecked += id
    }

    private suspend fun mergeServerRuns(): List<Run> {
        val runs = runCatching { container.repo.listRuns(agentId) }.getOrDefault(emptyList())
        noteApiModels(runs = runs)
        if (runs.isNotEmpty()) runOrder = runsOldestFirst(runs).map { it.id }
        runs.forEach { noteRunPrompt(it) }
        runsOldestFirst(runs).asReversed()
            .filter { it.id !in promptChecked }
            .take(PROMPT_LOOKUPS)
            .forEach { ensureRunHint(it.id) }
        var filled = runs.map { item -> hydrateRun(item, force = false) }
        filled.filter { it.isTerminal() }.forEach { settledRuns += it.id }
        var merged = mergeRunTranscript(lines, filled)
        val hasUser = merged.any { it.kind == "user" }
        val hasPrompt = filled.any { !it.prompt?.text.isNullOrBlank() }
        if (!hasUser && !hasPrompt && !probedPrompts && filled.isNotEmpty()) {
            probedPrompts = true
            val sample = listOfNotNull(filled.lastOrNull(), filled.firstOrNull()).distinctBy { it.id }
            val probed = sample.map { hydrateRun(it, force = true) }
            if (probed.any { !it.prompt?.text.isNullOrBlank() }) {
                filled = filled.map { item ->
                    probed.firstOrNull { it.id == item.id } ?: hydrateRun(item, force = true)
                }
                merged = mergeRunTranscript(lines, filled)
            }
        }
        synchronized(lineGate) {
            val latest = mergeRunTranscript(lines, filled)
            if (latest != lines) {
                lines = latest
                persist()
            }
        }
        return filled
    }

    private suspend fun mergeConversationHistory(): List<String> {
        val convo = mergeConversation() ?: return emptyList()
        return convo.messages.filter { it.transcriptKind() == "user" }.map { it.text.orEmpty() }
    }

    private suspend fun mergeConversation(): AgentConversation? {
        val convo = runCatching { container.repo.conversation(agentId) }.getOrNull() ?: return null
        if (convo.messages.isEmpty()) return null
        synchronized(lineGate) {
            val liveRun = (runOrder + listOfNotNull(run?.id)).distinct().lastOrNull()
            val merged = mergeConversationTranscript(lines, convo.messages, settledRuns.toSet(), runOrder, liveRun)
            if (merged != lines) {
                lines = merged
                persist()
            }
        }
        return convo
    }

    private suspend fun hydrateRun(item: Run, force: Boolean): Run {
        val needResult = item.isTerminal() && item.result.isNullOrBlank()
        if (!force && !needResult) return item
        val full = runCatching { container.repo.getRun(agentId, item.id) }.getOrDefault(item)
        return item.copy(
            result = full.result ?: item.result,
            prompt = full.prompt ?: item.prompt,
            status = full.status ?: item.status,
            createdAt = full.createdAt ?: item.createdAt,
            git = full.git ?: item.git,
        )
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
        if (latest.isTerminal()) viewModelScope.launch { settleRun(latest) }
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
            afterNonAssistant = false
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
                            appendAssistantSegment(assistantBuf, event.text, afterNonAssistant)
                            afterNonAssistant = false
                            upsert("assistant-$runId", "assistant", assistantBuf.toString(), runId)
                        }
                        is StreamEvent.Thinking -> {
                            receiving = true
                            afterNonAssistant = true
                            thinkingBuf.append(event.text)
                            upsert("think-$runId", "thinking", thinkingBuf.toString(), runId)
                        }
                        is StreamEvent.ToolCall -> {
                            receiving = true
                            afterNonAssistant = true
                            noteToolApproval(event.callId, event.status)
                            upsert(
                                event.callId ?: "tool-$runId-${event.name}",
                                "tool",
                                toolCallText(event.name, event.args),
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
                            clearApprovals()
                            run = run?.copy(
                                status = event.status,
                                result = event.text,
                                durationMs = event.durationMs ?: run?.durationMs,
                                git = event.git ?: run?.git,
                            )
                            event.git?.branches?.firstOrNull()?.let { git ->
                                container.catalog.saveGit(GitSnap(agentId, git.branch, git.prUrl, git.repoUrl))
                                container.chats.claimFinishedPr(container.catalog.gitSnaps())?.let { pendingPrUrl = it }
                            }
                            if (!event.text.isNullOrBlank()) {
                                upsert("assistant-$runId", "assistant", event.text, runId)
                            }
                            streaming = false
                            receiving = false
                            container.notifier.notifyIfNeeded(
                                agentId,
                                agent?.name,
                                runId,
                                event.status,
                                event.text,
                                event.git?.branches?.firstOrNull()?.prUrl,
                            )
                            run?.let { ended -> viewModelScope.launch { settleRun(ended) } }
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
                            clearApprovals()
                            streaming = false
                            receiving = false
                            val latest = runCatching { container.repo.getRun(agentId, runId) }.getOrNull()
                            if (latest != null) {
                                run = latest
                                if (!latest.result.isNullOrBlank()) {
                                    upsert("assistant-$runId", "assistant", latest.result, runId)
                                }
                                if (!latest.isActive()) viewModelScope.launch { settleRun(latest) }
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
                delay(WATCH_MS)
                if (!foreground || busy) continue
                attachLatestRun()
            }
        }
    }

    private fun streamLive(): Boolean = streaming && streamJob?.isActive == true

    private suspend fun attachLatestRun() {
        val detail = runCatching { container.repo.getAgent(agentId) }.getOrNull() ?: return
        agent = detail
        noteApiModels(detail = detail)
        val runId = detail.latestRunId ?: return
        val live = runId == streamedRunId && (streaming || streamJob?.isActive == true)
        if (live) {
            if (isLiveStatus(detail.status) || run?.isActive() != true) return
            val ended = runCatching { container.repo.getRun(agentId, runId) }.getOrNull() ?: return
            if (ended.isActive()) return
            settleRun(ended)
            container.notifier.notifyIfNeeded(agentId, agent?.name, ended.id, ended.status, ended.result)
            return
        }
        if (runId == run?.id && run?.isActive() != true && !streaming) return
        val latest = runCatching { container.repo.getRun(agentId, runId) }.getOrNull() ?: return
        noteApiModels(runs = listOf(latest))
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
            settleRun(latest)
            container.notifier.notifyIfNeeded(
                agentId,
                agent?.name,
                latest.id,
                latest.status,
                latest.result,
                latest.git?.branches?.firstOrNull()?.prUrl,
            )
        }
    }

    private fun startPoll(runId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var tick = 0
            while (isActive && run?.id == runId && run?.isActive() == true) {
                val step = tick++
                delay(runPollDelayMs(receiving, step))
                if (pullConversationOnTick(receiving, step)) mergeConversationHistory()
                if (!foreground && streamLive()) continue
                val latest = runCatching { container.repo.getRun(agentId, runId) }.getOrNull() ?: continue
                run = latest
                if (!latest.isActive()) {
                    settleRun(latest)
                    container.notifier.notifyIfNeeded(
                        agentId,
                        agent?.name,
                        latest.id,
                        latest.status,
                        latest.result,
                    )
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
            viewModelScope.launch { settleRun(latest) }
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
        synchronized(lineGate) {
            val next = lines.toMutableList()
            val idx = next.indexOfFirst { existing ->
                existing.id == id ||
                    (kind == "assistant" && runId != null && existing.kind == "assistant" && existing.runId == runId)
            }
            val existingThumbs = next.getOrNull(idx)?.thumbs.orEmpty()
            val line = TranscriptLine(id, kind, text, runId, queued, thumbs.ifEmpty { existingThumbs })
            if (idx >= 0) next[idx] = line else next.add(line)
            lines = coalesceTranscript(next)
        }
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
        syncQueuedFlags()
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

    private fun noteToolApproval(callId: String?, status: String?) {
        val id = callId?.takeIf { it.isNotBlank() } ?: return
        if (ApprovalCopy.isPending(status)) pendingApprovals += id else pendingApprovals -= id
        approvalPending = pendingApprovals.isNotEmpty()
    }

    private fun clearApprovals() {
        pendingApprovals.clear()
        approvalPending = false
    }

    override fun onCleared() {
        persistNow()
        streamJob?.cancel()
        pollJob?.cancel()
        watchJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val WATCH_MS = 2_000L
        const val REFRESH_MIN_GAP_MS = 15_000L

        // Behind-base check uses unauthenticated GitHub/GitLab calls (60/hour/IP on GitHub).
        const val BEHIND_MIN_GAP_MS = 10L * 60L * 1000L
        val behindCheckedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    }
}

data class QueuedOutbound(
    val id: String,
    val prompt: Prompt,
    val attaches: List<AttachItem> = emptyList(),
    val caption: String = "",
    val at: Long = 0L,
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
    var muted by remember { mutableStateOf(container.chats.isMuted(agentId)) }
    var localTitle by remember { mutableStateOf(container.chats.title(agentId)) }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var artifactHistoryOpen by remember { mutableStateOf(false) }
    var chatPropertiesOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var attaches by remember { mutableStateOf<List<AttachItem>>(emptyList()) }
    var models by remember { mutableStateOf(container.repo.cachedModels()) }
    var draftReady by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val listState = remember(agentId) { LazyListState() }
    val context = LocalContext.current
    val pendingPr = vm.pendingPrUrl
    LaunchedEffect(pendingPr) {
        if (!pendingPr.isNullOrBlank()) {
            SafeLinks.open(context, pendingPr)
            vm.consumePendingPr()
        }
    }
    val scope = rememberCoroutineScope()
    val agentStatus = settledAgentStatus(vm.agent?.status, vm.agent?.latestRunId, vm.run)
    val runState = threadRunState(
        lines = vm.lines,
        agentStatus = agentStatus,
        runStatus = vm.run?.status,
        busy = vm.busy,
        streaming = vm.streaming,
        receiving = vm.receiving,
        approvalPending = vm.approvalPending,
    )
    val working = runState.active
    val canKill = runState.active
    LaunchedEffect(runState.active, agentId) {
        container.runSettle.setLocalActive(agentId, runState.active)
    }
    DisposableEffect(agentId) {
        onDispose { container.runSettle.setLocalActive(agentId, false) }
    }
    val title = localTitle ?: vm.agent?.name ?: "Agent"
    val indicator = runState.indicator
    val repoLabel = repoNameOnly(vm.run?.git?.branches?.firstOrNull()?.repoUrl)
        ?: repoNameOnly(container.catalog.gitSnaps()[agentId]?.repoUrl)
        ?: repoNameOnly(vm.agent?.repos?.firstOrNull()?.url)
        ?: vm.agent?.env?.name?.takeIf { it.isNotBlank() }
    val subtitle = threadSubtitle(repoLabel, indicator)
    val latestTool = vm.lines.lastOrNull { it.kind == "tool" }?.let { toolCallParts(it.text).first }
    val activity = workActivityLine(
        receiving = vm.receiving,
        agentStatus = agentStatus,
        runStatus = vm.run?.status,
        envType = vm.agent?.env?.type,
        toolName = if (working) latestTool else null,
    )
    var showTools by remember { mutableStateOf(container.store.showToolCalls) }
    var showThinking by remember { mutableStateOf(container.store.showThinking) }
    var showMicrophone by remember { mutableStateOf(container.store.showMicrophone) }
    val shownLines by produceState(vm.lines, vm) {
        snapshotFlow { vm.lines }.coalesced(STREAM_COALESCE_MS).collect { value = it }
    }
    val rows = remember(shownLines, showTools, showThinking) {
        groupChatRows(shownLines, showTools, showThinking)
    }
    val runHints = vm.runHints
    val lineHints = remember(shownLines, runHints) { lineModelHints(shownLines, runHints) }
    val workingModel = runModelLabel(
        vm.modelBook,
        vm.run?.id,
        currentModelHint(shownLines, runHints, vm.run?.id),
        models,
    )
    LaunchedEffect(vm.run?.id) { vm.ensureRunHint(vm.run?.id) }
    val currentRunId = vm.run?.id
    val liveThink = remember(shownLines, currentRunId, working) {
        liveThinkingLine(shownLines, currentRunId, working)
    }
    val showLiveThink = showThinking && liveThink != null
    val showTyping = working && !showLiveThink
    val waitText = if (showTyping) {
        waitCopy(
            receiving = vm.receiving,
            agentStatus = agentStatus,
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

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.foreground = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.foreground = false
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        showTools = container.store.showToolCalls
        showThinking = container.store.showThinking
        showMicrophone = container.store.showMicrophone
        muted = container.chats.isMuted(agentId)
        favorite = container.chats.isFavorite(agentId)
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
        vm.followParams = saved.modelParams
        attaches = saved.toItems()
        models = runCatching { container.repo.models() }.getOrDefault(models)
        if (vm.followParams.isEmpty() && vm.followModel.isNotBlank()) {
            vm.followParams = models.firstOrNull { it.id == vm.followModel }?.defaultParams().orEmpty()
        }
        draftReady = true
    }

    LaunchedEffect(draft, vm.followMode, vm.followModel, vm.followParams, attaches, draftReady) {
        if (!draftReady) return@LaunchedEffect
        container.drafts.save(
            agentId,
            ChatDraft(
                text = draft,
                mode = vm.followMode,
                modelId = vm.followModel,
                attaches = attaches.toDraft(),
                modelParams = vm.followParams,
            ),
        )
    }

    var stickToBottom by remember(agentId) { mutableStateOf(true) }
    var programmaticScroll by remember(agentId) { mutableStateOf(false) }
    val growKey = threadScrollKey(rows, liveThink, showTyping)

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

    LaunchedEffect(agentId, rows.size, showTyping, vm.pinnedArtifact != null, growKey, stickToBottom) {
        if (stickToBottom && !listState.isScrollInProgress) {
            snapToBottom()
        }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.snack) {
        val event = vm.snack ?: return@LaunchedEffect
        snackbar.showSnackbar(
            event.text,
            duration = if (event.indefinite) SnackbarDuration.Indefinite else SnackbarDuration.Short,
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = AppInsets.bars,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text(
                            subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = indicator.foreground(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
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
                            text = { Text(if (muted) "Unmute notifications" else "Mute notifications") },
                            onClick = {
                                menu = false
                                muted = container.chats.toggleMuted(agentId)
                                if (muted) container.notices.cancelShadeForAgent(agentId)
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
                .scaffoldBars(padding),
        ) {
            if (working) {
                WorkingBar(activity, workingModel, onStop = if (canKill) ({ vm.cancel() }) else null)
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
                    .fillMaxWidth()
                    .clipToBounds()
                    .testTag("chat-list"),
            ) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(LocalAppearance.current.rowGap.dp, Alignment.Top),
                ) {
                    if (liveThink != null && showThinking) {
                        item(key = LIVE_TAIL_KEY, contentType = "live-tail") {
                            ThinkingBlock(liveThink, onCopy = { text -> copyMessage(context, text) })
                        }
                    } else if (showTyping) {
                        item(key = LIVE_TAIL_KEY, contentType = "live-tail") {
                            TypingBubble(detail = waitText, model = workingModel)
                        }
                    }
                    val latest = vm.pinnedArtifact
                    if (latest != null) {
                        item(key = "artifact-${latest.path}-${latest.whenIso().orEmpty()}") {
                            LatestArtifactCard(
                                item = latest,
                                onOpen = { openArtifact(latest) },
                                onSave = { saveArtifact(latest) },
                            )
                        }
                    }
                    items(rows.asReversed(), key = ::chatRowKey, contentType = ::chatRowType) { row ->
                        when (row) {
                            is ChatRow.Message -> TranscriptBubble(
                                line = row.line,
                                modelText = if (row.line.kind == "assistant") {
                                    runModelLabel(vm.modelBook, lineRunId(row.line), lineHints[row.line.id], models)
                                } else {
                                    null
                                },
                                onCopy = { text -> copyMessage(context, text) },
                                onQuote = { text ->
                                    val block = quoteBlock(text)
                                    draft = if (draft.isBlank()) "$block\n\n" else "${draft.trimEnd()}\n\n$block\n\n"
                                },
                                onEditQueued = if (lineShowsQueued(row.line)) {
                                    {
                                        vm.editQueued(row.line.id)?.let { item ->
                                            draft = item.caption.ifBlank { item.prompt.text }
                                            attaches = item.attaches
                                        }
                                    }
                                } else {
                                    null
                                },
                                onCancelQueued = if (lineShowsQueued(row.line)) {
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
                                vm.followParams = emptyList()
                                modelMenu = false
                            },
                        )
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName ?: model.id) },
                                onClick = {
                                    vm.followModel = model.id
                                    vm.followParams = model.defaultParams()
                                    modelMenu = false
                                },
                            )
                        }
                    }
                }
            }
            ModelParamRow(
                model = models.firstOrNull { it.id == vm.followModel },
                params = vm.followParams,
                onParams = { vm.followParams = it },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Row(
                modifier = Modifier
                    .testTag("chat-composer")
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(PlayColors.Card)
                    .padding(start = 4.dp, end = 6.dp),
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
                    placeholder = { Text("Send a follow-up...", maxLines = 1) },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
                Button(
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
                                caption = draft,
                            )
                            draft = ""
                            attaches = emptyList()
                            container.drafts.clear(agentId)
                            scope.launch { snapToBottom() }
                        }
                    },
                    enabled = draft.isNotBlank() || attaches.any { it.ok },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PlayColors.Teal,
                        contentColor = PlayColors.TealInk,
                        disabledContainerColor = PlayColors.Teal.copy(alpha = 0.35f),
                        disabledContentColor = PlayColors.TealInk.copy(alpha = 0.6f),
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Send", fontWeight = FontWeight.SemiBold)
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
            status = agentStatus ?: vm.run?.status,
            env = env,
            branch = git?.branch ?: cached?.branch,
            repoUrl = git?.repoUrl ?: cached?.repoUrl,
            prUrl = git?.prUrl ?: cached?.prUrl,
            tokens = vm.usage?.totalUsage?.totalTokens,
            lastRun = lastRunLine(
                vm.run?.durationMs,
                vm.usage?.runs?.firstOrNull { it.id == vm.run?.id }?.usage?.totalTokens,
            ),
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

/** Streaming deltas arrive per token. The list takes at most one update per this window. */
internal const val STREAM_COALESCE_MS = 120L

internal const val LIVE_TAIL_KEY = "live-tail"

private const val SETTLE_FETCHES = 3
private const val SETTLE_RETRY_MS = 1_500L

internal fun appendAssistantSegment(buf: StringBuilder, text: String, afterOther: Boolean) {
    if (afterOther && buf.isNotEmpty() && text.isNotEmpty() && !buf.endsWith("\n")) buf.append("\n\n")
    buf.append(text)
}

internal fun conversationCovers(messages: List<ConversationMessage>, result: String?): Boolean {
    val needle = result?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
    if (needle.isEmpty()) return messages.isNotEmpty()
    return messages.any { msg ->
        msg.transcriptKind() == "assistant" &&
            msg.text.orEmpty().replace(Regex("\\s+"), " ").contains(needle)
    }
}

/** Emits at once after a quiet period, then at most once per [windowMs], always ending on the latest value. */
internal fun <T> Flow<T>.coalesced(windowMs: Long): Flow<T> = flow {
    conflate().collect { value ->
        emit(value)
        delay(windowMs)
    }
}

internal sealed class ChatRow {
    data class Message(val line: TranscriptLine) : ChatRow()
    data class Tools(val id: String, val tools: List<TranscriptLine>) : ChatRow()
}

internal fun chatRowKey(row: ChatRow): String = when (row) {
    is ChatRow.Message -> "m-${row.line.id}"
    is ChatRow.Tools -> "tools-${row.id}"
}

internal fun chatRowType(row: ChatRow): String = when (row) {
    is ChatRow.Message -> "m-${row.line.kind}"
    is ChatRow.Tools -> "tools"
}

internal fun threadScrollKey(
    rows: List<ChatRow>,
    liveThink: TranscriptLine? = null,
    showTyping: Boolean = false,
): String {
    val last = when (val row = rows.lastOrNull()) {
        is ChatRow.Message -> "${row.line.id}:${row.line.text.length}"
        is ChatRow.Tools -> "tools-${row.id}-${row.tools.size}-${row.tools.lastOrNull()?.text?.length ?: 0}"
        null -> "empty"
    }
    val think = liveThink?.let { "${it.id}:${it.text.length}" }.orEmpty()
    val tools = rows.filterIsInstance<ChatRow.Tools>().lastOrNull()
        ?.let { "${it.id}:${it.tools.size}" }
        .orEmpty()
    return "$last|$think|$tools|${rows.size}|$showTyping"
}

internal fun liveThinkingLine(
    lines: List<TranscriptLine>,
    runId: String?,
    working: Boolean,
): TranscriptLine? {
    if (!working) return null
    val think = if (runId.isNullOrBlank()) {
        lines.lastOrNull { it.kind == "thinking" }
    } else {
        lines.lastOrNull { line ->
            line.kind == "thinking" && (line.runId == runId || line.id == "think-$runId")
        }
    }
    return think?.takeIf { it.text.isNotBlank() }
}

internal fun groupChatRows(
    lines: List<TranscriptLine>,
    showTools: Boolean,
    showThinking: Boolean,
): List<ChatRow> {
    val rows = ArrayList<ChatRow>(lines.size)
    val pendingTools = ArrayList<TranscriptLine>()

    fun flushTools() {
        if (pendingTools.isEmpty()) return
        if (showTools) {
            rows += ChatRow.Tools(pendingTools.first().id, pendingTools.toList())
        }
        pendingTools.clear()
    }

    for (line in lines) {
        when (line.kind) {
            "tool" -> if (showTools) pendingTools += line
            "thinking" -> flushTools()
            "assistant", "user", "notice" -> {
                flushTools()
                rows += ChatRow.Message(line)
            }
            else -> {
                flushTools()
                rows += ChatRow.Message(line)
            }
        }
    }
    flushTools()
    return rows
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TranscriptBubble(
    line: TranscriptLine,
    modelText: String? = null,
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
    val appearance = LocalAppearance.current
    val isUser = line.kind == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) PlayColors.UserBubble else PlayColors.AgentBubble
    val textColor = Color.White
    val shape = if (isUser) {
        RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    }
    val label = when {
        lineShowsQueued(line) -> "Queued"
        line.kind == "assistant" -> senderLabel(modelText)
        else -> null
    }
    var menu by remember(line.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val copyText = line.text.trim()
    val quotable = line.kind == "user" || line.kind == "assistant"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
    ) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
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
                    .padding(horizontal = appearance.bubblePadH.dp, vertical = appearance.bubblePadV.dp),
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
                    MessageText(line.text, textColor)
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
    var menu by remember(tools.firstOrNull()?.id ?: "tools") { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val copyText = tools.joinToString("\n") { it.text }.trim()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tools.forEach { tool ->
            val (name, path) = toolCallParts(tool.text)
            Box {
                Row(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlayColors.Card)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (copyText.isBlank()) return@combinedClickable
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = true
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = PlayColors.Muted,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        if (!path.isNullOrBlank()) {
                            Text(
                                path,
                                style = MaterialTheme.typography.bodySmall,
                                color = PlayColors.Muted,
                            )
                        }
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

@Composable
internal fun WorkingBar(text: String, model: String? = null, onStop: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PlayColors.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(PlayColors.Teal),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!model.isNullOrBlank()) {
                Text(
                    model,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onStop != null) {
            TextButton(onClick = onStop, modifier = Modifier.testTag("stop-run")) { Text("Stop") }
        }
    }
}

private fun lineRunId(line: TranscriptLine): String? =
    line.runId ?: line.id.removePrefix("assistant-").takeIf { line.id.startsWith("assistant-") }

@Composable
private fun TypingBubble(detail: String? = null, model: String? = null) {
    val transition = rememberInfiniteTransition(label = "typing")
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            senderLabel(model),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

/**
 * The chat list is laid out bottom-up, so index 0 is the newest row and growth of any row near the
 * bottom extends upward without a scroll call. This only re-anchors after rows are added or removed
 * at the bottom, where the list would otherwise keep the previous first row in place.
 */
internal suspend fun LazyListState.scrollToBottom() {
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) scrollToItem(0)
}

/** True while the newest row's bottom is near the viewport bottom. */
private fun LazyListState.isPinnedToBottom(thresholdPx: Int = 80): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= thresholdPx
