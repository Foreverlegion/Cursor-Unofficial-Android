package com.cursorandroid.app.ui.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.api.ActiveEnv
import com.cursorandroid.app.data.api.AgentSummary
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.GitSnap
import com.cursorandroid.app.data.api.activeEnvs
import com.cursorandroid.app.data.api.isLiveStatus
import com.cursorandroid.app.data.api.isWorking
import com.cursorandroid.app.data.api.mergeInboxAgents
import com.cursorandroid.app.data.api.sortKey
import com.cursorandroid.app.data.api.visibleInbox
import com.cursorandroid.app.data.notify.Notice
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.repo.ChatMeta
import com.cursorandroid.app.data.repo.ChatShare
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.ui.chat.RenameChatDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    container: AppContainer,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onCompose: (envType: String, envName: String?) -> Unit,
    onSettings: () -> Unit,
    showEnvs: Boolean = true,
    showRemote: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf(container.catalog.agents().sortedByDescending { it.sortKey() }) }
    var computers by remember { mutableStateOf(container.catalog.computers()) }
    var metas by remember { mutableStateOf(container.chats.snapshot()) }
    var notices by remember { mutableStateOf(container.notices.visible()) }
    var git by remember { mutableStateOf(container.catalog.gitSnaps()) }
    var live by remember { mutableStateOf(container.conversations.liveStatuses()) }
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(container.chats.inboxShowArchived) }
    var showHidden by remember { mutableStateOf(container.chats.inboxShowHidden) }
    var workingOnly by remember { mutableStateOf(container.chats.inboxWorkingOnly) }
    val hiddenIds = metas.filter { it.value.hidden }.keys
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<AgentSummary?>(null) }
    var deleting by remember { mutableStateOf<AgentSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val tabs = remember(showEnvs, showRemote) { InboxTabs.visible(showEnvs, showRemote) }
    var selectedTab by remember { mutableStateOf(InboxTab.Agents) }
    val tab = if (selectedTab in tabs) selectedTab else InboxTab.Agents
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun reload(showSpinner: Boolean = true) {
        scope.launch {
            if (showSpinner) refreshing = true
            error = null
            try {
                val page = container.repo.listAllAgents(includeArchived = true)
                val agents = page.entries().sortedByDescending { it.sortKey() }
                items = agents
                nextCursor = page.nextCursor
                container.catalog.saveAgents(agents)
                live = container.conversations.liveStatuses()
                container.notices.reconcile(agents, live, container.chatTitles())
                notices = container.notices.visible()
                RunWatchScheduler.watchActive(context.applicationContext, agents)
                scope.launch {
                    runCatching { container.repo.refreshGitSnaps(agents) }
                    git = container.catalog.gitSnaps()
                }
            } catch (e: Exception) {
                if (items.isEmpty()) error = e.message ?: "Failed to load agents"
            } finally {
                refreshing = false
            }
        }
        scope.launch {
            val next = runCatching { container.repo.listComputers(items) }.getOrDefault(computers)
            computers = next
            container.catalog.saveComputers(next)
        }
        scope.launch {
            runCatching { container.repo.repositories() }
        }
    }

    LaunchedEffect(Unit) {
        reload(showSpinner = items.isEmpty())
        while (true) {
            delay(3_000)
            try {
                val page = container.repo.listAgentsPage(includeArchived = true)
                val agents = mergeInboxAgents(items, page.entries())
                items = agents
                container.catalog.saveAgents(agents)
                metas = container.chats.snapshot()
                live = container.conversations.liveStatuses()
                container.notices.reconcile(agents, live, container.chatTitles())
                notices = container.notices.visible()
                RunWatchScheduler.watchActive(context.applicationContext, agents)
            } catch (_: Exception) {
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(tab.title)
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onCompose("cloud", null) }) {
                Icon(Icons.Outlined.Add, contentDescription = "New agent")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NoticeTray(
                notices = notices,
                onOpen = onSelect,
                onDismiss = { id ->
                    container.notices.dismiss(id)
                    container.notifier.rememberDismissed(id)
                    notices = container.notices.visible()
                },
                onClear = {
                    val ids = notices.map { it.id }
                    container.notices.dismissAll()
                    ids.forEach { container.notifier.rememberDismissed(it) }
                    notices = container.notices.visible()
                },
            )
            if (tabs.size > 1) {
                PrimaryTabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0)) {
                    tabs.forEach { item ->
                        Tab(
                            selected = tab == item,
                            onClick = { selectedTab = item },
                            text = { Text(item.title) },
                        )
                    }
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { reload(showSpinner = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (tab == InboxTab.Agents) {
                    AgentList(
                        items = items.visibleInbox(showArchived, hiddenIds, showHidden),
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
                        onRename = { renaming = it },
                        onHide = { agent ->
                            container.chats.setHidden(agent.id, true)
                            metas = container.chats.snapshot()
                        },
                        onUnhide = { agent ->
                            container.chats.setHidden(agent.id, false)
                            metas = container.chats.snapshot()
                        },
                        onArchive = { agent ->
                            scope.launch {
                                runCatching { container.repo.archive(agent.id) }
                                reload()
                            }
                        },
                        onUnarchive = { agent ->
                            scope.launch {
                                runCatching { container.repo.unarchive(agent.id) }
                                reload()
                            }
                        },
                        onDelete = { deleting = it },
                    )
                } else if (tab == InboxTab.Envs) {
                    EnvList(
                        envs = items.visibleInbox(showArchived, hiddenIds, showHidden).activeEnvs(),
                        refreshing = refreshing,
                        onOpen = { env ->
                            env.latestId?.let(onSelect)
                        },
                        onCompose = { env ->
                            onCompose(env.type, env.composeName())
                        },
                    )
                } else {
                    ComputerList(
                        computers = computers,
                        refreshing = refreshing,
                        onSelect = { onCompose("machine", it.name) },
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
    deleting?.let { agent ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete chat") },
            text = { Text("Permanently delete this agent on Cursor. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = agent.id
                        deleting = null
                        scope.launch {
                            val ok = runCatching { container.repo.deleteAgent(id) }.isSuccess
                            if (ok) container.forgetLocal(id)
                            reload()
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NoticeTray(
    notices: List<Notice>,
    onOpen: (String) -> Unit,
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
                    .clickable { onOpen(notice.agentId) }
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

@Composable
private fun AgentList(
    items: List<AgentSummary>,
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
    onRename: (AgentSummary) -> Unit,
    onHide: (AgentSummary) -> Unit,
    onUnhide: (AgentSummary) -> Unit,
    onArchive: (AgentSummary) -> Unit,
    onUnarchive: (AgentSummary) -> Unit,
    onDelete: (AgentSummary) -> Unit,
) {
    val needle = query.trim()
    val favoriteIds = metas.filter { it.value.favorite }.keys
    val newest = items
        .map { overlayWorking(it, live) }
        .filter { agent ->
            if (workingOnly && !agent.isWorking()) return@filter false
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
    val favorites = newest.filter { it.id in favoriteIds }
    val rest = newest.filter { it.id !in favoriteIds }
    when {
        error != null && items.isEmpty() -> {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp),
            )
        }
        items.isEmpty() && !refreshing -> {
            Text(
                "No agents yet. Start one on a cloud VM or a named machine.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                item(key = "search") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search chats") },
                            singleLine = true,
                        )
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
                if (favorites.isNotEmpty()) {
                    item(key = "hdr-fav") {
                        SectionLabel("Favorites")
                    }
                    items(favorites, key = { "fav-${it.id}" }) { agent ->
                        AgentRow(
                            agent = agent,
                            title = metas[agent.id]?.title,
                            git = git[agent.id],
                            selected = agent.id == selectedId,
                            favorite = true,
                            hidden = metas[agent.id]?.hidden == true,
                            onClick = { onSelect(agent.id) },
                            onToggleFavorite = { onToggleFavorite(agent.id) },
                            onRename = { onRename(agent) },
                            onHide = { onHide(agent) },
                            onUnhide = { onUnhide(agent) },
                            onArchive = { onArchive(agent) },
                            onUnarchive = { onUnarchive(agent) },
                            onDelete = { onDelete(agent) },
                        )
                        HorizontalDivider()
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
                        selected = agent.id == selectedId,
                        favorite = false,
                        hidden = metas[agent.id]?.hidden == true,
                        onClick = { onSelect(agent.id) },
                        onToggleFavorite = { onToggleFavorite(agent.id) },
                        onRename = { onRename(agent) },
                        onHide = { onHide(agent) },
                        onUnhide = { onUnhide(agent) },
                        onArchive = { onArchive(agent) },
                        onUnarchive = { onUnarchive(agent) },
                        onDelete = { onDelete(agent) },
                    )
                    HorizontalDivider()
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
                "No active environments. Start an agent on cloud, a named machine, or a pool.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
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
private fun ComputerList(
    computers: List<Computer>,
    refreshing: Boolean,
    onSelect: (Computer) -> Unit,
) {
    val online = computers.filter { it.online }
    val offline = computers.filter { !it.online }
    when {
        computers.isEmpty() && !refreshing -> {
            Text(
                "No remotes online. Open Cursor on a PC, stay signed in, and enable Remote Control.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(online + offline, key = { it.workerId ?: it.name }) { computer ->
                    ComputerRow(computer = computer, onClick = { onSelect(computer) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ComputerRow(
    computer: Computer,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
private fun AgentRow(
    agent: AgentSummary,
    title: String?,
    git: GitSnap?,
    selected: Boolean,
    favorite: Boolean,
    hidden: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val env = agent.env?.type?.lowercase() ?: "cloud"
    val name = title?.takeIf { it.isNotBlank() } ?: agent.name?.ifBlank { null } ?: agent.id
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val gitLine = git?.line().orEmpty()
    val archived = agent.archived == true
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    buildString {
                        append(agent.status ?: "UNKNOWN")
                        append(" · ")
                        append(env)
                        agent.env?.name?.let { append(" · "); append(it) }
                        if (gitLine.isNotBlank()) {
                            append(" · ")
                            append(gitLine)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val prUrl = git?.prUrl
                if (!prUrl.isNullOrBlank()) {
                    Text(
                        "Open PR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            SafeLinks.open(context, prUrl)
                        },
                    )
                }
            }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More")
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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

private fun overlayWorking(agent: AgentSummary, live: Map<String, String>): AgentSummary {
    val local = live[agent.id]
    return if (isLiveStatus(local) && !agent.isWorking()) agent.copy(status = local) else agent
}
