package com.cursorandroid.app.ui.grok

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.data.api.Prompt
import com.cursorandroid.app.data.api.StreamEvent
import com.cursorandroid.app.data.api.defaultParams
import com.cursorandroid.app.data.api.retainEventId
import com.cursorandroid.app.data.repo.GrokChatSession
import com.cursorandroid.app.data.repo.GrokMessage
import com.cursorandroid.app.data.repo.filterGrokModels
import com.cursorandroid.app.data.repo.grokCreateRequest
import com.cursorandroid.app.data.repo.grokDisplayUserText
import com.cursorandroid.app.data.repo.grokFollowUpPrompt
import com.cursorandroid.app.data.repo.grokMessageId
import com.cursorandroid.app.data.repo.grokModelSelection
import com.cursorandroid.app.data.repo.needsNewGrokAgent
import com.cursorandroid.app.data.repo.pickDefaultGrokModel
import com.cursorandroid.app.ui.chat.ModelParamRow
import com.cursorandroid.app.ui.inbox.InboxBottomNav
import com.cursorandroid.app.ui.status.PlayColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class GrokChatViewModel(
    private val container: AppContainer,
) : ViewModel() {
    var localSession by mutableStateOf(container.grokChat.load())
        private set

    val grokMessages: List<GrokMessage> get() = localSession.messages
    var models by mutableStateOf<List<ModelItem>>(emptyList())
        private set
    var selectedModel by mutableStateOf<ModelItem?>(null)
        private set
    var modelParams by mutableStateOf<List<ModelParam>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var streaming by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    private var streamJob: Job? = null
    private var lastEventId: String? = null
    private val assistantBuf = StringBuilder()

    init {
        viewModelScope.launch {
            val all = runCatching { container.repo.models() }.getOrDefault(emptyList())
            models = filterGrokModels(all)
            val saved = localSession
            selectedModel = models.firstOrNull { it.id == saved.modelId } ?: pickDefaultGrokModel(all)
            modelParams = saved.modelParams.takeIf { it.isNotEmpty() }
                ?: selectedModel?.defaultParams().orEmpty()
        }
    }

    fun persist() {
        container.grokChat.save(
            localSession.copy(
                modelId = selectedModel?.id.orEmpty(),
                modelParams = modelParams,
            ),
        )
    }

    fun newChat() {
        streamJob?.cancel()
        streaming = false
        busy = false
        assistantBuf.clear()
        localSession = GrokChatSession(
            modelId = selectedModel?.id.orEmpty(),
            modelParams = modelParams,
        )
        persist()
    }

    fun selectModel(item: ModelItem) {
        selectedModel = item
        modelParams = item.defaultParams()
        if (needsNewGrokAgent(localSession, item.id, modelParams)) {
            localSession = localSession.copy(agentId = null, activeRunId = null)
        }
        persist()
    }

    fun applyModelParams(params: List<ModelParam>) {
        modelParams = params
        val id = selectedModel?.id.orEmpty()
        if (needsNewGrokAgent(localSession, id, params)) {
            localSession = localSession.copy(agentId = null, activeRunId = null)
        }
        persist()
    }

    fun send(userText: String, onAssistantDone: (String) -> Unit) {
        val text = userText.trim()
        if (text.isEmpty() || busy || streaming) return
        val model = grokModelSelection(selectedModel, modelParams)
        if (model == null) {
            error = "No Grok model available for your account."
            return
        }
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val runId: String
                val agentId: String
                if (localSession.agentId.isNullOrBlank() || needsNewGrokAgent(localSession, model.id, modelParams)) {
                    val created = container.repo.createAgent(grokCreateRequest(text, model))
                    agentId = created.agent.id
                    runId = created.run.id
                    container.chats.setHidden(agentId, true)
                    val userLine = GrokMessage(
                        id = grokMessageId("user", runId),
                        role = "user",
                        text = text,
                        runId = runId,
                    )
                    localSession = localSession.copy(
                        agentId = agentId,
                        modelId = model.id,
                        modelParams = modelParams,
                        activeRunId = runId,
                        messages = localSession.messages + userLine,
                    )
                } else {
                    agentId = localSession.agentId!!
                    val pendingRun = UUID.randomUUID().toString()
                    val userLine = GrokMessage(
                        id = grokMessageId("user", pendingRun),
                        role = "user",
                        text = text,
                        runId = pendingRun,
                        pending = true,
                    )
                    localSession = localSession.copy(messages = localSession.messages + userLine)
                    persist()
                    val run = container.repo.followUp(agentId, grokFollowUpPrompt(text), model = model)
                    runId = run.id
                    localSession = localSession.copy(
                        messages = localSession.messages.map {
                            if (it.id == userLine.id) it.copy(runId = runId, pending = false) else it
                        },
                        activeRunId = runId,
                    )
                }
                persist()
                startStream(agentId, runId, onAssistantDone)
            } catch (e: Exception) {
                busy = false
                error = (e as? ApiException)?.displayMessage() ?: e.message ?: "Send failed"
            }
        }
    }

    private fun startStream(agentId: String, runId: String, onAssistantDone: (String) -> Unit) {
        streamJob?.cancel()
        assistantBuf.clear()
        lastEventId = null
        streaming = true
        busy = true
        val assistantId = grokMessageId("assistant", runId)
        localSession = localSession.copy(
            activeRunId = runId,
            messages = localSession.messages.filterNot { it.id == assistantId } +
                GrokMessage(assistantId, "assistant", "", runId, pending = true),
        )
        persist()
        streamJob = viewModelScope.launch {
            container.repo.stream(agentId, runId, lastEventId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    streaming = false
                    busy = false
                    error = (e as? ApiException)?.displayMessage() ?: e.message
                }
                .collect { event ->
                    lastEventId = retainEventId(lastEventId, event.eventId)
                    when (event) {
                        is StreamEvent.Assistant -> {
                            assistantBuf.append(event.text)
                            patchAssistant(assistantId, assistantBuf.toString(), runId)
                        }
                        is StreamEvent.Result -> {
                            val body = event.text?.takeIf { it.isNotBlank() } ?: assistantBuf.toString()
                            patchAssistant(assistantId, body, runId, pending = false)
                            streaming = false
                            busy = false
                            persist()
                            if (body.isNotBlank()) onAssistantDone(body)
                        }
                        is StreamEvent.StreamError -> {
                            if (event.recoverable) return@collect
                            error = event.message
                            streaming = false
                            busy = false
                        }
                        StreamEvent.Done -> {
                            streaming = false
                            busy = false
                            val run = runCatching {
                                container.repo.getRun(agentId, runId)
                            }.getOrNull()
                            val body = run?.result?.takeIf { it.isNotBlank() }
                                ?: assistantBuf.toString()
                            patchAssistant(assistantId, body, runId, pending = false)
                            persist()
                            if (body.isNotBlank()) onAssistantDone(body)
                        }
                        else -> Unit
                    }
                }
        }
    }

    private fun patchAssistant(id: String, text: String, runId: String, pending: Boolean = true) {
        localSession = localSession.copy(
            messages = localSession.messages.map {
                if (it.id == id) it.copy(text = text, pending = pending) else it
            },
        )
    }

    override fun onCleared() {
        streamJob?.cancel()
        persist()
        super.onCleared()
    }
}

class GrokChatVmFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GrokChatViewModel(container) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrokChatScreen(
    container: AppContainer,
    showBack: Boolean,
    onBack: () -> Unit,
    onInbox: () -> Unit = onBack,
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: GrokChatViewModel = viewModel(factory = GrokChatVmFactory(container))
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var draft by remember { mutableStateOf("") }
    var voicePartial by remember { mutableStateOf("") }
    var handsFree by remember { mutableStateOf(false) }
    var readAloud by remember { mutableStateOf(true) }
    val handsFreeFlag = remember { AtomicBoolean(false) }
    LaunchedEffect(handsFree) {
        handsFreeFlag.set(handsFree)
    }
    var modelMenu by remember { mutableStateOf(false) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        if (!granted) {
            scope.launch { snack.showSnackbar("Microphone access is needed for Grok voice.") }
        }
    }
    fun ensureMic(): Boolean {
        if (micGranted) return true
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
        return false
    }
    val speechSlot = remember { arrayOfNulls<GrokSpeechEngine>(1) }
    val speech = remember {
        GrokSpeechEngine(
            context = context,
            onPartial = { voicePartial = it },
            onFinal = { spoken ->
                voicePartial = ""
                draft = spoken
                if (handsFreeFlag.get()) {
                    vm.send(spoken) { reply ->
                        if (readAloud) speechSlot[0]?.speak(reply)
                    }
                }
            },
            onError = { msg ->
                scope.launch { snack.showSnackbar(msg) }
            },
        ).also { speechSlot[0] = it }
    }
    DisposableEffect(Unit) {
        onDispose {
            speech.setHandsFree(false)
            speech.release()
        }
    }
    LaunchedEffect(handsFree) {
        if (handsFree) {
            if (ensureMic()) speech.setHandsFree(true)
            else handsFree = false
        } else {
            speech.setHandsFree(false)
        }
    }
    LaunchedEffect(vm.error) {
        val msg = vm.error ?: return@LaunchedEffect
        snack.showSnackbar(msg)
        vm.error = null
    }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.grokMessages.size, vm.grokMessages.lastOrNull()?.text) {
        if (vm.grokMessages.isNotEmpty()) {
            listState.animateScrollToItem(vm.grokMessages.lastIndex)
        }
    }
    if (showBack) BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Grok") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.newChat() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "New chat")
                    }
                    TextButton(onClick = { modelMenu = true }) {
                        Text(vm.selectedModel?.displayName ?: "Model")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        vm.models.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.displayName ?: item.id) },
                                onClick = {
                                    vm.selectModel(item)
                                    modelMenu = false
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PlayColors.Card),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            InboxBottomNav(
                grokActive = true,
                onInbox = onInbox,
                onGrok = {},
                onSettings = onSettings,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            vm.selectedModel?.let { model ->
                if (!model.parameters.isNullOrEmpty()) {
                    ModelParamRow(
                        model = model,
                        params = vm.modelParams,
                        onParams = vm::applyModelParams,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            if (voicePartial.isNotBlank()) {
                Text(
                    voicePartial,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.grokMessages, key = { it.id }) { msg ->
                    GrokBubble(msg)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilterChip(
                    selected = handsFree,
                    onClick = {
                        if (!handsFree && !ensureMic()) return@FilterChip
                        handsFree = !handsFree
                    },
                    label = { Text("Hands-free") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Read replies", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = readAloud, onCheckedChange = { readAloud = it })
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(
                    onClick = {},
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (!ensureMic()) return@detectTapGestures
                                handsFree = false
                                speech.startPushToTalk()
                                tryAwaitRelease()
                                speech.stopPushToTalk()
                            },
                        )
                    },
                    enabled = !vm.busy && speech.available,
                ) {
                    Icon(Icons.Outlined.Mic, contentDescription = "Hold to talk")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Grok") },
                    maxLines = 5,
                )
                IconButton(
                    onClick = {
                        val text = draft
                        draft = ""
                        vm.send(text) { reply ->
                            if (readAloud) speech.speak(reply)
                        }
                    },
                    enabled = draft.isNotBlank() && !vm.busy && !vm.streaming,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun GrokBubble(msg: GrokMessage) {
    val user = msg.role == "user"
    val align = if (user) Alignment.CenterEnd else Alignment.CenterStart
    val bg = if (user) PlayColors.Teal.copy(alpha = 0.18f) else PlayColors.Card
    val shown = if (user) grokDisplayUserText(msg.text) else msg.text
    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(bg, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                shown.ifBlank { if (msg.pending) "…" else "" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (user) FontWeight.Normal else FontWeight.Medium,
            )
        }
    }
}
