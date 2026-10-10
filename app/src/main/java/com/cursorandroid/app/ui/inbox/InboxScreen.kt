package com.cursorandroid.app.ui.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.ui.status.PlayColors
import com.cursorandroid.app.ui.status.StatusPill
import com.cursorandroid.app.ui.status.agentCardSubtitle
import com.cursorandroid.app.ui.status.relativeAge
import com.cursorandroid.app.ui.status.runIndicator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.ui.AppInsets
import com.cursorandroid.app.ui.scaffoldBars
import com.cursorandroid.app.data.api.ActiveEnv
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.repo.RepoGroupPrefs
import com.cursorandroid.app.data.repo.markLocalActive
import com.cursorandroid.app.data.repo.settleAgents
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.repo.machineKey
import com.cursorandroid.app.data.repo.visibleMachines
import com.cursorandroid.app.ui.MachineActionsDialog
import com.cursorandroid.app.ui.MachineMenuRow
import com.cursorandroid.app.data.api.GitSnap
import com.cursorandroid.app.data.api.isArchived
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.api.isWorking
import com.cursorandroid.app.data.api.markCloudArchived
import com.cursorandroid.app.data.api.mergeInboxAgents
import com.cursorandroid.app.data.api.sortKey
import com.cursorandroid.app.data.api.visibleInbox
import com.cursorandroid.app.data.notify.Notice
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.repo.ChatMeta
import com.cursorandroid.app.data.repo.ChatShare
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.ui.chat.RenameChatDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    container: AppContainer,
    selectedId: String?,
    linkNote: String? = null,
    onDismissLinkNote: () -> Unit = {},
    onSelect: (String) -> Unit,
    onCompose: (envType: String, envName: String?) -> Unit,
    onSettings: () -> Unit,
    showEnvs: Boolean = true,
    showRemote: Boolean = true,
    settingsEpoch: Int = 0,
    modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf(container.catalog.agents().sortedByDescending { it.sortKey() }) }
    var computers by remember { mutableStateOf(container.catalog.computers()) }
    var machinePrefs by remember { mutableStateOf(container.machines.prefs()) }
    var machineAction by remember { mutableStateOf<Computer?>(null) }
    val shownComputers = remember(computers, machinePrefs) {
        visibleMachines(computers, machinePrefs, container.machines.now())
    }
    machineAction?.let { target ->
        MachineActionsDialog(
            name = target.name,
            onHide = {
                container.machines.hide(target.machineKey(), target.name)
                machinePrefs = container.machines.prefs()
                machineAction = null
            },
            onDelete = {
                container.machines.forget(target.machineKey(), target.name)
                machinePrefs = container.machines.prefs()
                machineAction = null
            },
            onDismiss = { machineAction = null },
        )
    }
    var metas by remember { mutableStateOf(container.chats.snapshot()) }
    val notices by container.notices.feed.collectAsStateWithLifecycle()
    fun loadGit(): Map<String, GitSnap> = withRepoFallbacks(
        container.catalog.gitSnaps(),
        container.catalog.agentRepos(),
        container.chats.snapshot().mapValues { it.value.repoUrl },
    )
    var git by remember { mutableStateOf(loadGit()) }
    var live by remember { mutableStateOf(container.conversations.liveStatuses()) }
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(container.chats.inboxShowArchived) }
    var showHidden by remember { mutableStateOf(container.chats.inboxShowHidden) }
    var workingOnly by remember { mutableStateOf(container.chats.inboxWorkingOnly) }
    var groupByRepo by remember { mutableStateOf(container.chats.groupByRepo) }
    var compactCards by remember { mutableStateOf(container.chats.compactCards) }
    var hideFinishedDays by remember { mutableIntStateOf(container.chats.hideFinishedDays) }
    var collapsedRepos by remember { mutableStateOf(container.chats.collapsedRepos) }
    var groupPrefs by remember { mutableStateOf(container.chats.repoGroupPrefs) }
    var cloudFilter by remember { mutableStateOf(CloudFilter.All) }
    var revealFinished by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val hiddenIds = metas.filter { it.value.hidden }.keys
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<AgentSummary?>(null) }
    var deleteIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val tabs = remember(showEnvs, showRemote) { InboxTabs.visible(showEnvs, showRemote) }
    var selectedTab by remember { mutableStateOf(InboxTab.Agents) }
    val tab = if (selectedTab in tabs) selectedTab else InboxTab.Agents
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var reloadJob by remember { mutableStateOf<Job?>(null) }

    fun applyAgents(incoming: List<AgentSummary>, cursor: String?, watch: Boolean = false) {
        val agents = settleAgents(incoming, container.runSettle.settled.value)
        items = agents
        nextCursor = cursor
        container.catalog.saveAgents(agents)
        live = container.conversations.liveStatuses()
        container.notices.reconcile(agents, live, container.chatTitles())
        container.notifier.acknowledgeKnown(agents)
        if (watch) {
            RunWatchScheduler.watchActive(context.applicationContext, agents)
        } else {
            RunWatchScheduler.rememberStatuses(context.applicationContext, agents)
        }
    }

    fun reload(showSpinner: Boolean = true) {
        reloadJob?.cancel()
        reloadJob = scope.launch {
            if (showSpinner) refreshing = true
            error = null
            try {
                val page = container.repo.listAgentsPage(includeArchived = true)
                ensureActive()
                val incoming = container.repo.hydrateStatuses(page.entries())
                var latest = mergeInboxAgents(items, incoming)
                applyAgents(latest, page.nextCursor, watch = true)
                refreshing = false
                metas = container.chats.snapshot()
                scope.launch {
                    runCatching { container.repo.refreshGitSnaps(latest) }
                    runCatching { container.repo.resolveAgentRepos(latest) }
                    git = loadGit()
                    val url = container.chats.claimFinishedPr(git)
                    if (!url.isNullOrBlank()) SafeLinks.open(context, url)
                }
                val next = runCatching { container.repo.listComputers(latest) }.getOrDefault(computers)
                computers = next
                machinePrefs = container.machines.prefs()
                container.catalog.saveComputers(next)
                var cursor = page.nextCursor
                while (!cursor.isNullOrBlank()) {
                    ensureActive()
                    val more = container.repo.listAgentsPage(includeArchived = true, cursor = cursor)
                    if (more.entries().isEmpty()) break
                    latest = mergeInboxAgents(latest, more.entries())
                    applyAgents(latest, more.nextCursor, watch = false)
                    cursor = more.nextCursor?.takeIf { it.isNotBlank() && it != cursor }
                }
                val marked = runCatching {
                    val active = container.repo.listAllAgents(includeArchived = false)
                    markCloudArchived(latest, active.entries())
                }.getOrNull()
                if (marked != null) {
                    applyAgents(marked, nextCursor, watch = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (items.isEmpty()) error = e.message ?: "Failed to load agents"
            } finally {
                refreshing = false
            }
        }
        scope.launch {
            runCatching { container.repo.repositories() }
        }
    }

    val settledRuns by container.runSettle.settled.collectAsStateWithLifecycle()
    val localActive by container.runSettle.localActive.collectAsStateWithLifecycle()
    LaunchedEffect(settledRuns) {
        val next = settleAgents(items, settledRuns)
        if (next !== items) {
            items = next
            container.catalog.saveAgents(next)
            live = container.conversations.liveStatuses()
            container.notices.reconcile(next, live, container.chatTitles())
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifeState by lifecycle.currentStateAsState()
    LaunchedEffect(settingsEpoch) {
        showArchived = container.chats.inboxShowArchived
        showHidden = container.chats.inboxShowHidden
        workingOnly = container.chats.inboxWorkingOnly
        groupByRepo = container.chats.groupByRepo
        compactCards = container.chats.compactCards
        hideFinishedDays = container.chats.hideFinishedDays
        collapsedRepos = container.chats.collapsedRepos
        groupPrefs = container.chats.repoGroupPrefs
        revealFinished = false
    }
    LaunchedEffect(Unit) {
        reload(showSpinner = items.isEmpty())
    }
    LaunchedEffect(lifeState.isAtLeast(Lifecycle.State.STARTED)) {
        if (!lifeState.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect
        while (true) {
            delay(5_000)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect
            if (reloadJob?.isActive == true && refreshing) continue
            try {
                val page = container.repo.listAgentsPage(includeArchived = true)
                val incoming = container.repo.hydrateStatuses(page.entries())
                val agents = mergeInboxAgents(items, incoming)
                applyAgents(agents, page.nextCursor ?: nextCursor, watch = true)
                container.runSettle.sweep(items) { agentId, runId ->
                    runCatching { container.repo.getRun(agentId, runId) }.getOrNull()
                }
                metas = container.chats.snapshot()
                runCatching { container.repo.resolveAgentRepos(agents, budget = 10) }
                git = loadGit()
                val next = runCatching { container.repo.listComputers(agents) }.getOrNull()
                if (next != null) {
                    computers = next
                    machinePrefs = container.machines.prefs()
                    container.catalog.saveComputers(next)
                }
            } catch (_: Exception) {
            }
        }
    }

    val approvalIds = notices.mapNotNull { notice ->
        notice.agentId.takeIf { notice.kind == "approval" }
    }.toSet()
    val pendingArchive = remember { HashSet<String>() }
    fun archiveWithUndo(agent: AgentSummary) {
        if (!pendingArchive.add(agent.id)) return
        val name = container.chats.displayName(agent.id, agent.name)
        val previous = agent.status
        scope.launch {
            val ok = runCatching { container.repo.archive(agent.id) }.isSuccess
            if (!ok) {
                pendingArchive.remove(agent.id)
                snackbar.showSnackbar("Couldn't archive")
                return@launch
            }
            items = items.map {
                if (it.id == agent.id) it.copy(archived = true, status = "ARCHIVED") else it
            }
            container.catalog.saveAgents(items)
            val result = snackbar.showSnackbar(
                message = "$name archived",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                runCatching { container.repo.unarchive(agent.id) }
                items = items.map {
                    if (it.id == agent.id) it.copy(archived = false, status = previous) else it
                }
                container.catalog.saveAgents(items)
            }
            pendingArchive.remove(agent.id)
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = AppInsets.bars,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = {
                    Text("Agents", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                },
                actions = {
                    Button(
                        onClick = { onCompose(tab.composeTarget(), null) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlayColors.Teal,
                            contentColor = PlayColors.TealInk,
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text("+ New", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
        bottomBar = {
            InboxBottomNav(onSettings = onSettings)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scaffoldBars(padding),
        ) {
            if (!linkNote.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        linkNote,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = onDismissLinkNote) {
                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
                    }
                }
            }
            NoticeTray(
                notices = notices,
                onOpen = { notice ->
                    container.notices.dismiss(notice.id)
                    container.notifier.rememberDismissed(notice.id)
                    onSelect(notice.agentId)
                },
                onDismiss = { id ->
                    container.notices.dismiss(id)
                    container.notifier.rememberDismissed(id)
                },
                onClear = {
                    val ids = notices.map { it.id }
                    container.notices.dismissAll()
                    ids.forEach { container.notifier.rememberDismissed(it) }
                },
            )
            InboxTabStrip(
                tabs = tabs,
                selected = tab,
                onSelect = { selectedTab = it },
            )
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { reload(showSpinner = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                val listed = markLocalActive(items, localActive).visibleInbox(showArchived, hiddenIds, showHidden).forInboxTab(tab, tabs)
                run {
                    AgentList(
                        items = listed,
                        approvalIds = approvalIds,
                        computers = if (tab == InboxTab.Remote) shownComputers else emptyList(),
                        onSelectComputer = { onCompose("machine", it.name) },
                        onComputerMenu = { machineAction = it },
                        selectedId = selectedId,
                        metas = metas,
                        git = git,
                        live = live,
                        query = query,
                        onQueryChange = { query = it },
                        showArchived = showArchived,
                        onShowArchived = {
                            showArchived = it
                            container.chats.inboxShowArchived = it
                        },
                        showHidden = showHidden,
                        onShowHidden = {
                            showHidden = it
                            container.chats.inboxShowHidden = it
                        },
                        workingOnly = workingOnly,
                        onWorkingOnly = {
                            workingOnly = it
                            container.chats.inboxWorkingOnly = it
                        },
                        canLoadMore = nextCursor != null,
                        onLoadMore = {
                            val cursor = nextCursor ?: return@AgentList
                            scope.launch {
                                val page = runCatching {
                                    container.repo.listAgentsPage(includeArchived = true, cursor = cursor)
                                }.getOrNull() ?: return@launch
                                items = mergeInboxAgents(items, page.entries())
                                nextCursor = page.nextCursor
                                container.catalog.saveAgents(items)
                            }
                        },
                        error = error,
                        refreshing = refreshing,
                        onSelect = onSelect,
                        onToggleFavorite = { id ->
                            container.chats.toggleFavorite(id)
                            metas = container.chats.snapshot()
                        },
                        onToggleMute = { id ->
                            val muted = container.chats.toggleMuted(id)
                            if (muted) container.notices.cancelShadeForAgent(id)
                            metas = container.chats.snapshot()
                        },
                        onFavoriteIds = { ids, favorite ->
                            ids.forEach { container.chats.setFavorite(it, favorite) }
                            metas = container.chats.snapshot()
                        },
                        onRename = { renaming = it },
                        onHide = { agent ->
                            container.chats.setHidden(agent.id, true)
                            metas = container.chats.snapshot()
                        },
                        onUnhide = { agent ->
                            container.chats.setHidden(agent.id, false)
                            metas = container.chats.snapshot()
                        },
                        onHideIds = { ids, hidden ->
                            ids.forEach { container.chats.setHidden(it, hidden) }
                            metas = container.chats.snapshot()
                        },
                        onArchive = { agent -> archiveWithUndo(agent) },
                        onUnarchive = { agent ->
                            scope.launch {
                                runCatching { container.repo.unarchive(agent.id) }
                                items = items.map {
                                    if (it.id == agent.id) it.copy(archived = false) else it
                                }
                                container.catalog.saveAgents(items)
                            }
                        },
                        onArchiveIds = { ids, archive ->
                            scope.launch {
                                ids.forEach { id ->
                                    runCatching {
                                        if (archive) container.repo.archive(id) else container.repo.unarchive(id)
                                    }
                                }
                                items = items.map { agent ->
                                    if (agent.id in ids) {
                                        agent.copy(archived = archive, status = if (archive) "ARCHIVED" else agent.status)
                                    } else {
                                        agent
                                    }
                                }
                                container.catalog.saveAgents(items)
                            }
                        },
                        onDelete = { deleteIds = listOf(it.id) },
                        onDeleteIds = { deleteIds = it.toList() },
                        cloud = tab == InboxTab.Agents,
                        groupByRepo = groupByRepo,
                        compactCards = compactCards,
                        hideFinishedDays = hideFinishedDays,
                        cloudFilter = cloudFilter,
                        onCloudFilter = { cloudFilter = it },
                        revealFinished = revealFinished,
                        onRevealFinished = { revealFinished = !revealFinished },
                        groupPrefs = groupPrefs,
                        onGroupPrefs = { next ->
                            groupPrefs = next
                            container.chats.repoGroupPrefs = next
                        },
                        collapsedRepos = collapsedRepos,
                        onToggleRepo = { key ->
                            collapsedRepos = if (key in collapsedRepos) collapsedRepos - key else collapsedRepos + key
                            container.chats.collapsedRepos = collapsedRepos
                        },
                        onTogglePin = { id ->
                            val pinned = metas[id]?.pinned == true
                            container.chats.setPinned(id, !pinned)
                            metas = container.chats.snapshot()
                        },
                    )
                }
            }
        }
    }
    renaming?.let { agent ->
        RenameChatDialog(
            current = container.chats.displayName(agent.id, agent.name),
            onDismiss = { renaming = null },
            onConfirm = { name ->
                container.renameChat(agent.id, name)
                metas = container.chats.snapshot()
                renaming = null
            },
        )
    }
    if (deleteIds.isNotEmpty()) {
        val count = deleteIds.size
        AlertDialog(
            onDismissRequest = { deleteIds = emptyList() },
            title = { Text(if (count == 1) "Delete chat" else "Delete $count chats") },
            text = { Text("Permanently delete on Cursor. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = deleteIds
                        deleteIds = emptyList()
                        scope.launch {
                            ids.forEach { id ->
                                val ok = runCatching { container.repo.deleteAgent(id) }.isSuccess
                                if (ok) container.forgetLocal(id)
                            }
                            items = items.filter { it.id !in ids }
                            container.catalog.saveAgents(items)
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteIds = emptyList() }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NoticeTray(
    notices: List<Notice>,
    onOpen: (Notice) -> Unit,
    onDismiss: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (notices.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Notifications", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onClear) { Text("Clear") }
        }
        notices.forEach { notice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(notice) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(notice.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        notice.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (notice.kind) {
                            "working", "approval" -> MaterialTheme.colorScheme.primary
                            "error" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = { onDismiss(notice.id) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentList(
    items: List<AgentSummary>,
    approvalIds: Set<String>,
    computers: List<Computer>,
    onSelectComputer: (Computer) -> Unit,
    onComputerMenu: (Computer) -> Unit,
    selectedId: String?,
    metas: Map<String, ChatMeta>,
    git: Map<String, GitSnap>,
    live: Map<String, String>,
    query: String,
    onQueryChange: (String) -> Unit,
    showArchived: Boolean,
    onShowArchived: (Boolean) -> Unit,
    showHidden: Boolean,
    onShowHidden: (Boolean) -> Unit,
    workingOnly: Boolean,
    onWorkingOnly: (Boolean) -> Unit,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    error: String?,
    refreshing: Boolean,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleMute: (String) -> Unit,
    onFavoriteIds: (Collection<String>, Boolean) -> Unit,
    onRename: (AgentSummary) -> Unit,
    onHide: (AgentSummary) -> Unit,
    onUnhide: (AgentSummary) -> Unit,
    onHideIds: (Collection<String>, Boolean) -> Unit,
    onArchive: (AgentSummary) -> Unit,
    onUnarchive: (AgentSummary) -> Unit,
    onArchiveIds: (Collection<String>, Boolean) -> Unit,
    onDelete: (AgentSummary) -> Unit,
    onDeleteIds: (Collection<String>) -> Unit,
    cloud: Boolean,
    groupByRepo: Boolean,
    compactCards: Boolean,
    hideFinishedDays: Int,
    cloudFilter: CloudFilter,
    onCloudFilter: (CloudFilter) -> Unit,
    revealFinished: Boolean,
    onRevealFinished: () -> Unit,
    groupPrefs: RepoGroupPrefs,
    onGroupPrefs: (RepoGroupPrefs) -> Unit,
    collapsedRepos: Set<String>,
    onToggleRepo: (String) -> Unit,
    onTogglePin: (String) -> Unit,
) {
    var selecting by remember { mutableStateOf(false) }
    var checkedIds by remember { mutableStateOf(setOf<String>()) }
    var bulkMenu by remember { mutableStateOf(false) }
    if (selecting) {
        BackHandler {
            selecting = false
            checkedIds = emptySet()
        }
    }
    fun toggleChecked(id: String) {
        checkedIds = if (id in checkedIds) checkedIds - id else checkedIds + id
    }
    fun startSelecting(id: String) {
        selecting = true
        checkedIds = setOf(id)
    }
    fun stopSelecting() {
        selecting = false
        checkedIds = emptySet()
        bulkMenu = false
    }
    val needle = query.trim()
    val favoriteIds = metas.filter { it.value.favorite }.keys
    val newest = items
        .map { overlayWorking(it, live) }
        .filter { agent ->
            if (!cloud && workingOnly && !agent.isWorking()) return@filter false
            if (needle.isBlank()) return@filter true
            val title = metas[agent.id]?.title
            val snap = git[agent.id]
            listOfNotNull(
                title,
                agent.name,
                agent.status,
                agent.env?.type,
                agent.env?.name,
                snap?.branch,
                snap?.prUrl,
                snap?.repoUrl,
            ).any { it.contains(needle, ignoreCase = true) }
        }
        .sortedByDescending { it.sortKey() }
    val arranged = if (cloud) {
        arrangeCloudAgents(
            agents = newest,
            git = git,
            pinnedAt = metas.filterValues { it.pinned }.mapValues { it.value.pinnedAt },
            favoriteIds = favoriteIds,
            approvalIds = approvalIds,
            filter = cloudFilter,
            hideFinishedDays = hideFinishedDays,
            revealFinished = revealFinished,
            groupByRepo = groupByRepo,
            nowMillis = System.currentTimeMillis(),
            groupPrefs = groupPrefs,
        )
    } else {
        null
    }
    val favorites = arranged?.favorites ?: newest.filter { it.id in favoriteIds }
    val rest = arranged?.rest ?: newest.filter { it.id !in favoriteIds }
    val cloudEmpty = arranged != null &&
        arranged.pinned.isEmpty() &&
        arranged.groups.isEmpty() &&
        arranged.favorites.isEmpty() &&
        arranged.rest.isEmpty() &&
        arranged.hiddenFinished == 0
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "search") {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search chats") },
                        singleLine = true,
                    )
                    if (cloud) {
                        FlowRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CloudFilter.entries.forEach { item ->
                                FilterChip(
                                    selected = cloudFilter == item,
                                    onClick = { onCloudFilter(item) },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = workingOnly,
                                onClick = { onWorkingOnly(!workingOnly) },
                                label = { Text("Working") },
                            )
                            FilterChip(
                                selected = showArchived,
                                onClick = { onShowArchived(!showArchived) },
                                label = { Text("Archived") },
                            )
                            FilterChip(
                                selected = showHidden,
                                onClick = { onShowHidden(!showHidden) },
                                label = { Text("Hidden") },
                            )
                        }
                    }
                }
                if (selecting) {
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { stopSelecting() }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cancel selection")
                            }
                            Text(
                                if (checkedIds.isEmpty()) "Select chats" else "${checkedIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Box {
                                TextButton(
                                    onClick = { bulkMenu = true },
                                    enabled = checkedIds.isNotEmpty(),
                                ) { Text("Bulk actions") }
                                DropdownMenu(expanded = bulkMenu, onDismissRequest = { bulkMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Favorite") },
                                        onClick = {
                                            bulkMenu = false
                                            onFavoriteIds(checkedIds, true)
                                            stopSelecting()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Unfavorite") },
                                        onClick = {
                                            bulkMenu = false
                                            onFavoriteIds(checkedIds, false)
                                            stopSelecting()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Hide") },
                                        onClick = {
                                            bulkMenu = false
                                            onHideIds(checkedIds, true)
                                            stopSelecting()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Unhide") },
                                        onClick = {
                                            bulkMenu = false
                                            onHideIds(checkedIds, false)
                                            stopSelecting()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Archive") },
                                        onClick = {
                                            bulkMenu = false
                                            onArchiveIds(checkedIds, true)
                                            stopSelecting()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Unarchive") },
                                        onClick = {
                                            bulkMenu = false
                                            onArchiveIds(checkedIds, false)
                                            stopSelecting()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            bulkMenu = false
                                            onDeleteIds(checkedIds)
                                            stopSelecting()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        when {
            error != null && items.isEmpty() -> {
                item(key = "error") {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            (if (cloud) cloudEmpty else newest.isEmpty()) && computers.isEmpty() && !refreshing -> {
                item(key = "empty") {
                    Text(
                        if (cloud) {
                            cloudEmptyCopy(cloudFilter, query, showHidden, showArchived)
                        } else {
                            inboxEmptyCopy(showHidden, showArchived, workingOnly, query)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            else -> {
                if (computers.isNotEmpty()) {
                    item(key = "machines") { SectionLabel("Machines") }
                    items(computers, key = { "pc:${it.workerId ?: it.name}" }) { computer ->
                        ComputerRow(
                            computer = computer,
                            onClick = { onSelectComputer(computer) },
                            onMenu = { onComputerMenu(computer) },
                        )
                    }
                }
                if (arranged != null) {
                    cloudAgentBlocks(
                        arrangement = arranged,
                        groupActions = RepoGroupActions(
                            manualOrder = groupPrefs.order.isNotEmpty(),
                            onRename = { key, name ->
                                onGroupPrefs(groupPrefs.withStyle(key) { it.copy(name = name) })
                            },
                            onFavorite = { key ->
                                onGroupPrefs(groupPrefs.withStyle(key) { it.copy(favorite = !it.favorite) })
                            },
                            onColor = { key, color ->
                                onGroupPrefs(groupPrefs.withStyle(key) { it.copy(color = color) })
                            },
                            onOrder = { keys -> onGroupPrefs(groupPrefs.withOrder(keys)) },
                            onResetOrder = { onGroupPrefs(groupPrefs.resetOrder()) },
                        ),
                        groupByRepo = groupByRepo,
                        collapsed = collapsedRepos,
                        onToggleGroup = onToggleRepo,
                        revealFinished = revealFinished,
                        onToggleReveal = onRevealFinished,
                    ) { agent ->
                        SwipeArchiveRow(
                            enabled = !selecting && !agent.isArchived(),
                            onArchive = { onArchive(agent) },
                        ) {
                            AgentRow(
                                agent = agent,
                                title = metas[agent.id]?.title,
                                git = if (compactCards) null else git[agent.id],
                                needsApproval = agent.id in approvalIds,
                                selected = agent.id == selectedId,
                                favorite = agent.id in favoriteIds,
                                hidden = metas[agent.id]?.hidden == true,
                                muted = metas[agent.id]?.muted == true,
                                selecting = selecting,
                                checked = agent.id in checkedIds,
                                compact = compactCards,
                                pinned = metas[agent.id]?.pinned == true,
                                longPressMenu = true,
                                onClick = {
                                    if (selecting) toggleChecked(agent.id) else onSelect(agent.id)
                                },
                                onLongClick = { startSelecting(agent.id) },
                                onToggleFavorite = { onToggleFavorite(agent.id) },
                                onToggleMute = { onToggleMute(agent.id) },
                                onTogglePin = { onTogglePin(agent.id) },
                                onRename = { onRename(agent) },
                                onHide = { onHide(agent) },
                                onUnhide = { onUnhide(agent) },
                                onArchive = { onArchive(agent) },
                                onUnarchive = { onUnarchive(agent) },
                                onDelete = { onDelete(agent) },
                            )
                        }
                    }
                } else {
                if (favorites.isNotEmpty()) {
                    item(key = "hdr-fav") {
                        SectionLabel("Favorites")
                    }
                    items(favorites, key = { "fav-${it.id}" }) { agent ->
                        AgentRow(
                            agent = agent,
                            title = metas[agent.id]?.title,
                            git = git[agent.id],
                            needsApproval = agent.id in approvalIds,
                            selected = agent.id == selectedId,
                            favorite = true,
                            hidden = metas[agent.id]?.hidden == true,
                            muted = metas[agent.id]?.muted == true,
                            selecting = selecting,
                            checked = agent.id in checkedIds,
                            onClick = {
                                if (selecting) toggleChecked(agent.id) else onSelect(agent.id)
                            },
                            onLongClick = { startSelecting(agent.id) },
                            onToggleFavorite = { onToggleFavorite(agent.id) },
                            onToggleMute = { onToggleMute(agent.id) },
                            onRename = { onRename(agent) },
                            onHide = { onHide(agent) },
                            onUnhide = { onUnhide(agent) },
                            onArchive = { onArchive(agent) },
                            onUnarchive = { onUnarchive(agent) },
                            onDelete = { onDelete(agent) },
                        )
                    }
                    item(key = "hdr-all") {
                        SectionLabel("All")
                    }
                }
                items(rest, key = { it.id }) { agent ->
                    AgentRow(
                        agent = agent,
                        title = metas[agent.id]?.title,
                        git = git[agent.id],
                        needsApproval = agent.id in approvalIds,
                        selected = agent.id == selectedId,
                        favorite = false,
                        hidden = metas[agent.id]?.hidden == true,
                        muted = metas[agent.id]?.muted == true,
                        selecting = selecting,
                        checked = agent.id in checkedIds,
                        onClick = {
                            if (selecting) toggleChecked(agent.id) else onSelect(agent.id)
                        },
                        onLongClick = { startSelecting(agent.id) },
                        onToggleFavorite = { onToggleFavorite(agent.id) },
                        onToggleMute = { onToggleMute(agent.id) },
                        onRename = { onRename(agent) },
                        onHide = { onHide(agent) },
                        onUnhide = { onUnhide(agent) },
                        onArchive = { onArchive(agent) },
                        onUnarchive = { onUnarchive(agent) },
                        onDelete = { onDelete(agent) },
                    )
                }
                }
            }
        }
        if (canLoadMore) {
            item(key = "more") {
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier.padding(16.dp),
                ) { Text("Load older chats") }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun EnvList(
    envs: List<ActiveEnv>,
    refreshing: Boolean,
    onOpen: (ActiveEnv) -> Unit,
    onCompose: (ActiveEnv) -> Unit,
) {
    when {
        envs.isEmpty() && !refreshing -> {
            Text(
                "No active environments. Start an agent on cloud or a pool.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(envs, key = { "${it.type}:${it.name}" }) { env ->
                    EnvRow(env = env, onOpen = { onOpen(env) }, onCompose = { onCompose(env) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EnvRow(
    env: ActiveEnv,
    onOpen: () -> Unit,
    onCompose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(env.name, style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append(env.typeLabel())
                    append(" · ")
                    if (env.working > 0) {
                        append(env.working)
                        append(" working · ")
                    }
                    append(env.chats)
                    append(if (env.chats == 1) " chat" else " chats")
                    env.latestStatus?.let {
                        append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (env.working > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        TextButton(onClick = onCompose) { Text("New") }
    }
}

@Composable
private fun RemotePane(
    computers: List<Computer>,
    envs: List<ActiveEnv>,
    refreshing: Boolean,
    onOpenEnv: (ActiveEnv) -> Unit,
    onComposeEnv: (ActiveEnv) -> Unit,
    onSelectComputer: (Computer) -> Unit,
) {
    val envNames = envs.map { it.name.lowercase() }.toHashSet()
    val extraOnline = computers.filter { it.online && it.name.lowercase() !in envNames }
    val extraOffline = computers.filter { !it.online && it.name.lowercase() !in envNames }
    when {
        computers.isEmpty() && envs.isEmpty() && !refreshing -> {
            Text(
                "No remotes online. Open Cursor on a PC, stay signed in, and enable Remote Control.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (extraOnline.isNotEmpty()) {
                    item(key = "online") { SectionLabel("Online") }
                    items(extraOnline, key = { "on:${it.workerId ?: it.name}" }) { computer ->
                        ComputerRow(computer = computer, onClick = { onSelectComputer(computer) })
                        HorizontalDivider()
                    }
                }
                if (envs.isNotEmpty()) {
                    item(key = "chats") { SectionLabel("Chats") }
                    items(envs, key = { "env:${it.type}:${it.name}" }) { env ->
                        EnvRow(env = env, onOpen = { onOpenEnv(env) }, onCompose = { onComposeEnv(env) })
                        HorizontalDivider()
                    }
                }
                if (extraOffline.isNotEmpty()) {
                    item(key = "offline") { SectionLabel("Offline") }
                    items(extraOffline, key = { "off:${it.workerId ?: it.name}" }) { computer ->
                        ComputerRow(computer = computer, onClick = { onSelectComputer(computer) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ComputerRow(
    computer: Computer,
    onClick: () -> Unit,
    onMenu: () -> Unit = {},
) {
    MachineMenuRow(onClick = onClick, onLongClick = onMenu, onMore = onMenu) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(computer.name, style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append(if (computer.online) "Online" else "Offline")
                    if (computer.online) append(if (computer.inUse) " · busy" else " · idle")
                    computer.detail?.let {
                        append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (computer.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AgentRow(
    agent: AgentSummary,
    title: String?,
    git: GitSnap?,
    needsApproval: Boolean,
    selected: Boolean,
    favorite: Boolean,
    hidden: Boolean,
    muted: Boolean,
    selecting: Boolean,
    checked: Boolean,
    compact: Boolean = false,
    pinned: Boolean = false,
    longPressMenu: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit = {},
    onRename: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val name = title?.takeIf { it.isNotBlank() } ?: agent.name?.ifBlank { null } ?: agent.id
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val archived = agent.isArchived()
    val indicator = runIndicator(agent.status, approvalPending = needsApproval)
    val subtitle = if (compact) {
        if (muted) "Muted" else ""
    } else {
        buildString {
            append(agentCardSubtitle(agent.env?.type, agent.env?.name, git?.repoUrl))
            if (muted) append(" · muted")
            git?.line()?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        }
    }
    val whenLabel = relativeAge(agent.updatedAt ?: agent.createdAt)
    val compactTime = listOfNotNull(
        whenLabel.takeIf { it.isNotBlank() },
        "Muted".takeIf { muted },
    ).joinToString(" · ")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) Color(0xFF24302E) else PlayColors.Card)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (longPressMenu) menu = true else onLongClick()
                    },
                )
                .padding(start = 14.dp, end = 6.dp, top = if (compact) 6.dp else 10.dp, bottom = if (compact) 6.dp else 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (selecting) {
                Checkbox(checked = checked, onCheckedChange = null)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = if (compact) 1 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = PlayColors.Muted)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill(indicator)
                    val lineTime = if (compact) compactTime else whenLabel
                    if (lineTime.isNotBlank()) {
                        Text(
                            lineTime,
                            style = MaterialTheme.typography.labelMedium,
                            color = PlayColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!compact && subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = PlayColors.Muted,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                val prUrl = git?.prUrl
                if (!compact && !prUrl.isNullOrBlank()) {
                    Text(
                        "Open PR",
                        style = MaterialTheme.typography.labelSmall,
                        color = PlayColors.Teal,
                        modifier = Modifier.clickable {
                            SafeLinks.open(context, prUrl)
                        },
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (longPressMenu) {
                DropdownMenuItem(
                    text = { Text(if (pinned) "Unpin" else "Pin") },
                    onClick = {
                        menu = false
                        onTogglePin()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Select") },
                    onClick = {
                        menu = false
                        onLongClick()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(if (favorite) "Unfavorite" else "Favorite") },
                onClick = {
                    menu = false
                    onToggleFavorite()
                },
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menu = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(if (hidden) "Unhide" else "Hide") },
                onClick = {
                    menu = false
                    if (hidden) onUnhide() else onHide()
                },
            )
            DropdownMenuItem(
                text = { Text(if (muted) "Unmute notifications" else "Mute notifications") },
                onClick = {
                    menu = false
                    onToggleMute()
                },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = {
                    menu = false
                    ChatShare.send(context, name, agent.id, agent.url)
                },
            )
            DropdownMenuItem(
                text = { Text(if (archived) "Unarchive" else "Archive") },
                onClick = {
                    menu = false
                    if (archived) onUnarchive() else onArchive()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun InboxTabStrip(
    tabs: List<InboxTab>,
    selected: InboxTab,
    onSelect: (InboxTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        tabs.forEach { item ->
            val active = item == selected
            Column(
                modifier = Modifier
                    .clickable { onSelect(item) }
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                Text(
                    item.title,
                    color = if (active) PlayColors.Teal else PlayColors.Muted,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (active) 28.dp else 0.dp)
                        .background(PlayColors.Teal),
                )
            }
        }
    }
}

@Composable
private fun InboxBottomNav(onSettings: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(AppInsets.navigation)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = PlayColors.Card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                "Inbox",
                color = PlayColors.Teal,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Settings",
                color = PlayColors.Muted,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable(onClick = onSettings),
            )
        }
    }
}

private fun overlayWorking(agent: AgentSummary, live: Map<String, String>): AgentSummary {
    val local = live[agent.id]
    return if (isLiveStatus(local) && !agent.isWorking()) agent.copy(status = local) else agent
}
