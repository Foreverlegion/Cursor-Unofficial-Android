package com.cursorandroid.app.ui.composeAgent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.Env
import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.data.api.cloudCreateTarget
import com.cursorandroid.app.data.api.defaultParams
import com.cursorandroid.app.data.api.namedCloudEnvironments
import com.cursorandroid.app.data.api.selection
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.RepositoryItem
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.repo.AttachItem
import com.cursorandroid.app.data.repo.Attachments
import com.cursorandroid.app.data.repo.TranscriptLine
import com.cursorandroid.app.data.repo.withStartupNotice
import com.cursorandroid.app.data.repo.ChatDraft
import com.cursorandroid.app.data.repo.DraftStore
import com.cursorandroid.app.data.repo.DraftSubagent
import com.cursorandroid.app.data.repo.GithubRepos
import com.cursorandroid.app.data.repo.toApi
import com.cursorandroid.app.data.repo.toDraft
import com.cursorandroid.app.ui.chat.AttachButton
import com.cursorandroid.app.ui.chat.AttachChips
import com.cursorandroid.app.ui.chat.ModelParamRow
import com.cursorandroid.app.ui.chat.VoiceButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAgentScreen(
    container: AppContainer,
    showBack: Boolean,
    onBack: () -> Unit,
    onCreated: (agentId: String) -> Unit,
    modifier: Modifier = Modifier,
    initialEnvType: String = "cloud",
    initialEnvName: String? = null,
    resetTick: Int = 0,
) {
    var agentName by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var envType by remember { mutableStateOf(initialEnvType) }
    var envName by remember { mutableStateOf(initialEnvName.orEmpty()) }
    var repos by remember { mutableStateOf(container.catalog.repos()) }
    var provider by remember { mutableStateOf("") }
    var repoUrl by remember { mutableStateOf("") }
    var startingRef by remember { mutableStateOf("") }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingRepos by remember { mutableStateOf(repos.isEmpty()) }
    var loadingBranches by remember { mutableStateOf(false) }
    var autoPr by remember { mutableStateOf(true) }
    var workOnBranch by remember { mutableStateOf(false) }
    var skipReviewer by remember { mutableStateOf(false) }
    var prUrl by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("agent") }
    var models by remember { mutableStateOf<List<ModelItem>>(emptyList()) }
    var modelId by remember { mutableStateOf("") }
    var modelParams by remember { mutableStateOf<List<ModelParam>>(emptyList()) }
    var modelMenu by remember { mutableStateOf(false) }
    var computers by remember { mutableStateOf(container.catalog.computers()) }
    var computerMenu by remember { mutableStateOf(false) }
    var pools by remember { mutableStateOf(container.catalog.pools()) }
    var poolMenu by remember { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }
    var repoMenu by remember { mutableStateOf(false) }
    var branchMenu by remember { mutableStateOf(false) }
    var repoQuery by remember { mutableStateOf("") }
    var createRepo by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("") }
    var newRepoPrivate by remember { mutableStateOf(true) }
    var cloudFromEnv by remember {
        mutableStateOf(!initialEnvName.isNullOrBlank() && initialEnvType == "cloud")
    }
    var cloudEnvMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var attaches by remember { mutableStateOf<List<AttachItem>>(emptyList()) }
    var subagents by remember { mutableStateOf<List<DraftSubagent>>(emptyList()) }
    var subName by remember { mutableStateOf("") }
    var subDesc by remember { mutableStateOf("") }
    var subPrompt by remember { mutableStateOf("") }
    var subModel by remember { mutableStateOf("") }
    var draftReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val providers = remember(repos) {
        (listOf("GitHub") + repos.map { it.providerLabel() }).distinct().sortedWith(
            compareBy<String> {
                when (it) {
                    "GitHub" -> 0
                    "GitLab" -> 1
                    else -> 2
                }
            }.thenBy { it },
        )
    }
    val providerRepos = remember(repos, provider) {
        repos.filter { provider.isBlank() || it.providerLabel() == provider }
    }
    val selectedRepo = providerRepos.firstOrNull { it.url == repoUrl }
    val cloudEnvs = remember(repos, envName) {
        container.catalog.agents().namedCloudEnvironments(container.catalog.cloudEnvs() + listOf(envName))
    }

    LaunchedEffect(resetTick) {
        val saved = container.drafts.load(DraftStore.NEW_AGENT)
        if (saved.agentName.isNotBlank()) agentName = saved.agentName
        if (saved.text.isNotBlank()) prompt = saved.text
        if (saved.modelId.isNotBlank()) {
            modelId = saved.modelId
        } else if (container.store.defaultModel.isNotBlank()) {
            modelId = container.store.defaultModel
        }
        if (saved.mode.isNotBlank()) mode = saved.mode
        if (saved.attaches.isNotEmpty()) attaches = saved.toItems()
        if (!initialEnvName.isNullOrBlank() || initialEnvType != "cloud") {
            envType = initialEnvType
            envName = initialEnvName.orEmpty()
            cloudFromEnv = initialEnvType == "cloud" && !initialEnvName.isNullOrBlank()
        } else {
            if (saved.envType.isNotBlank()) envType = saved.envType
            if (saved.envName.isNotBlank()) envName = saved.envName
            cloudFromEnv = saved.envType == "cloud" && saved.envName.isNotBlank() && saved.repoUrl.isBlank()
        }
        if (saved.provider.isNotBlank()) provider = saved.provider
        if (saved.repoUrl.isNotBlank()) repoUrl = saved.repoUrl
        if (saved.startingRef.isNotBlank()) startingRef = saved.startingRef
        if (saved.autoPr != null) autoPr = saved.autoPr
        if (saved.workOnBranch != null) workOnBranch = saved.workOnBranch
        if (saved.skipReviewer != null) skipReviewer = saved.skipReviewer
        if (saved.prUrl.isNotBlank()) prUrl = saved.prUrl
        if (saved.modelParams.isNotEmpty()) modelParams = saved.modelParams
        subagents = saved.resolvedSubagents()
        draftReady = true
    }

    LaunchedEffect(
        agentName, prompt, modelId, modelParams, mode, attaches, envType, envName, provider, repoUrl,
        startingRef, autoPr, workOnBranch, skipReviewer, prUrl, subagents, draftReady,
    ) {
        if (!draftReady) return@LaunchedEffect
        val first = subagents.firstOrNull()
        container.drafts.save(
            DraftStore.NEW_AGENT,
            ChatDraft(
                text = prompt,
                mode = mode,
                modelId = modelId,
                attaches = attaches.toDraft(),
                envType = envType,
                envName = envName,
                provider = provider,
                repoUrl = repoUrl,
                startingRef = startingRef,
                autoPr = autoPr,
                subName = first?.name.orEmpty(),
                subDesc = first?.description.orEmpty(),
                subPrompt = first?.prompt.orEmpty(),
                agentName = agentName,
                workOnBranch = workOnBranch,
                skipReviewer = skipReviewer,
                prUrl = prUrl,
                modelParams = modelParams,
                subagents = subagents,
            ),
        )
    }

    LaunchedEffect(Unit) {
        models = runCatching { container.repo.models() }.getOrDefault(emptyList())
        scope.launch {
            computers = runCatching { container.repo.listComputers() }.getOrDefault(computers)
            pools = runCatching { container.repo.listPools() }.getOrDefault(pools)
            if (envType == "machine" && envName.isBlank()) {
                computers.firstOrNull { it.online }?.let { envName = it.name }
            }
            if (envType == "pool" && envName.isBlank()) {
                pools.firstOrNull()?.let { envName = it.poolName }
            }
        }
        loadingRepos = repos.isEmpty()
        repos = runCatching { container.repo.repositories() }.getOrDefault(repos)
        loadingRepos = false
    }

    LaunchedEffect(providers) {
        if (provider.isBlank() || provider !in providers) {
            provider = providers.firstOrNull().orEmpty()
        }
    }

    LaunchedEffect(provider) {
        if (provider != "GitHub") {
            createRepo = false
            newRepoName = ""
        }
    }

    LaunchedEffect(provider, providerRepos, createRepo) {
        if (createRepo) return@LaunchedEffect
        if (providerRepos.none { it.url == repoUrl }) {
            repoUrl = providerRepos.firstOrNull()?.url.orEmpty()
            repoQuery = ""
        }
    }

    LaunchedEffect(repoUrl) {
        if (repoUrl.isBlank()) {
            branches = emptyList()
            startingRef = ""
            return@LaunchedEffect
        }
        loadingBranches = true
        val cached = container.catalog.branches(repoUrl)
        if (cached.isNotEmpty()) {
            branches = cached
            if (startingRef.isBlank() || startingRef !in cached) {
                startingRef = selectedRepo?.defaultBranch?.takeIf { it in cached }
                    ?: cached.firstOrNull().orEmpty()
            }
        }
        val next = runCatching {
            container.repo.branches(repoUrl, selectedRepo?.defaultBranch)
        }.getOrDefault(cached)
        branches = next
        if (startingRef.isBlank() || startingRef !in next) {
            startingRef = selectedRepo?.defaultBranch?.takeIf { it in next }
                ?: next.firstOrNull().orEmpty()
        }
        loadingBranches = false
    }

    if (showBack) BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("New agent") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = agentName,
                    onValueChange = { agentName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Agent name") },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                )
                Text("Target", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = envType == "cloud", onClick = { envType = "cloud" }, label = { Text("Cloud") })
                    FilterChip(selected = envType == "machine", onClick = { envType = "machine" }, label = { Text("Machine") })
                    FilterChip(selected = envType == "pool", onClick = { envType = "pool" }, label = { Text("Pool") })
                }
                if (envType == "cloud") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !cloudFromEnv,
                            onClick = { cloudFromEnv = false },
                            label = { Text("From repo") },
                        )
                        FilterChip(
                            selected = cloudFromEnv,
                            onClick = { cloudFromEnv = true },
                            label = { Text("Cloud computer") },
                        )
                    }
                    if (cloudFromEnv) {
                        ExposedDropdownMenuBox(expanded = cloudEnvMenu, onExpandedChange = { cloudEnvMenu = it }) {
                            OutlinedTextField(
                                value = envName,
                                onValueChange = { envName = it },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                                    .fillMaxWidth(),
                                label = { Text("Environment") },
                                placeholder = { Text("Name of the saved environment") },
                                supportingText = {
                                    Text("Saved environment: snapshot, secrets, and repos. Do not also pick a repo.")
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cloudEnvMenu) },
                                singleLine = true,
                            )
                            ExposedDropdownMenu(expanded = cloudEnvMenu, onDismissRequest = { cloudEnvMenu = false }) {
                                if (cloudEnvs.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Type a saved environment name") },
                                        onClick = { cloudEnvMenu = false },
                                        enabled = false,
                                    )
                                }
                                cloudEnvs.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            envName = name
                                            cloudEnvMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                    ExposedDropdownMenuBox(expanded = providerMenu, onExpandedChange = { providerMenu = it }) {
                        OutlinedTextField(
                            value = provider.ifBlank { if (loadingRepos) "Loading…" else "No source connected" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Source") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenu) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            providers.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        provider = name
                                        providerMenu = false
                                    },
                                )
                            }
                        }
                    }
                    if (providerRepos.size > 12) {
                        OutlinedTextField(
                            value = repoQuery,
                            onValueChange = { repoQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Filter repos") },
                            singleLine = true,
                        )
                    }
                    val visibleRepos = if (repoQuery.isBlank()) {
                        providerRepos
                    } else {
                        providerRepos.filter {
                            it.displayName().contains(repoQuery, ignoreCase = true) ||
                                it.url.contains(repoQuery, ignoreCase = true)
                        }
                    }
                    ExposedDropdownMenuBox(expanded = repoMenu, onExpandedChange = { repoMenu = it }) {
                        OutlinedTextField(
                            value = when {
                                createRepo -> "Create new repo"
                                selectedRepo != null -> selectedRepo.displayName()
                                loadingRepos -> "Loading repos…"
                                else -> "Select a repo"
                            },
                            onValueChange = {},
                            readOnly = true,
                            enabled = provider.isNotBlank(),
                            label = { Text("Repository") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repoMenu) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = repoMenu, onDismissRequest = { repoMenu = false }) {
                            if (provider == "GitHub") {
                                DropdownMenuItem(
                                    text = { Text("Create new repo") },
                                    onClick = {
                                        createRepo = true
                                        newRepoName = ""
                                        repoUrl = ""
                                        startingRef = ""
                                        branches = emptyList()
                                        repoMenu = false
                                    },
                                )
                                HorizontalDivider()
                            }
                            if (visibleRepos.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No repos for this source") },
                                    onClick = { repoMenu = false },
                                    enabled = false,
                                )
                            }
                            visibleRepos.forEach { repo ->
                                DropdownMenuItem(
                                    text = { Text(repo.displayName()) },
                                    onClick = {
                                        createRepo = false
                                        newRepoName = ""
                                        repoUrl = repo.url
                                        repoMenu = false
                                    },
                                )
                            }
                        }
                    }
                    if (createRepo && repoUrl.isBlank()) {
                        val sanitized = GithubRepos.sanitizeName(newRepoName)
                        val hasToken = !container.store.githubToken.isNullOrBlank()
                        OutlinedTextField(
                            value = newRepoName,
                            onValueChange = { newRepoName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Name the repo") },
                            placeholder = { Text("name-the-repo") },
                            supportingText = {
                                Text(
                                    when {
                                        !hasToken ->
                                            "Add a GitHub token with repo access in Settings > Connections."
                                        sanitized.isNotBlank() && sanitized != newRepoName.trim() ->
                                            "Will be $sanitized"
                                        else ->
                                            "Creates a private repo under the token account, with a README on main."
                                    },
                                )
                            },
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = newRepoPrivate, onCheckedChange = { newRepoPrivate = it })
                            Text("Private")
                        }
                    }
                    OutlinedTextField(
                        value = prUrl,
                        onValueChange = { prUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("PR URL (optional)") },
                        placeholder = { Text("https://github.com/org/repo/pull/12") },
                        singleLine = true,
                        supportingText = {
                            Text("When set, the agent works on that PR. Branch is ignored.")
                        },
                    )
                    if (!createRepo || repoUrl.isNotBlank()) ExposedDropdownMenuBox(expanded = branchMenu, onExpandedChange = { branchMenu = it }) {
                        OutlinedTextField(
                            value = startingRef.ifBlank {
                                if (loadingBranches) "Loading branches…" else "Select a branch"
                            },
                            onValueChange = {},
                            readOnly = true,
                            enabled = repoUrl.isNotBlank(),
                            label = { Text("Branch") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchMenu) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = branchMenu, onDismissRequest = { branchMenu = false }) {
                            if (branches.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No branches found") },
                                    onClick = { branchMenu = false },
                                    enabled = false,
                                )
                            }
                            branches.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        startingRef = name
                                        branchMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = workOnBranch, onCheckedChange = { workOnBranch = it })
                        Text("Commit on this branch")
                    }
                    Text(
                        "Off creates a new cursor/ branch from the start ref. On pushes to the branch or PR head you picked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoPr, onCheckedChange = { autoPr = it })
                        Text("Open PR when finished")
                    }
                    if (autoPr) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = skipReviewer, onCheckedChange = { skipReviewer = it })
                            Text("Don't add me as reviewer")
                        }
                    }
                    }
                    if (cloudFromEnv) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = autoPr, onCheckedChange = { autoPr = it })
                            Text("Open PR when finished")
                        }
                        if (autoPr) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = skipReviewer, onCheckedChange = { skipReviewer = it })
                                Text("Don't add me as reviewer")
                            }
                        }
                    }
                } else if (envType == "machine") {
                    val online = computers.filter { it.online }
                    ExposedDropdownMenuBox(expanded = computerMenu, onExpandedChange = { computerMenu = it }) {
                        OutlinedTextField(
                            value = envName.ifBlank { "Select a computer" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Remote") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = computerMenu) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = computerMenu, onDismissRequest = { computerMenu = false }) {
                            if (online.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No remotes online") },
                                    onClick = { computerMenu = false },
                                    enabled = false,
                                )
                            }
                            online.forEach { computer ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(computer.name)
                                            val detail = buildString {
                                                append(if (computer.inUse) "Busy" else "Idle")
                                                computer.detail?.let {
                                                    append(" · ")
                                                    append(it)
                                                }
                                            }
                                            Text(detail, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        envName = computer.name
                                        computerMenu = false
                                    },
                                )
                            }
                            computers.filter { !it.online }.forEach { computer ->
                                DropdownMenuItem(
                                    text = { Text("${computer.name} · offline") },
                                    onClick = {
                                        envName = computer.name
                                        computerMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        "Online machines you are signed into. The PC must stay awake with Remote Control or a My Machines worker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ExposedDropdownMenuBox(expanded = poolMenu, onExpandedChange = { poolMenu = it }) {
                        OutlinedTextField(
                            value = envName,
                            onValueChange = { envName = it },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryEditable)
                                .fillMaxWidth(),
                            label = { Text("Pool") },
                            placeholder = { Text("default") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = poolMenu) },
                            singleLine = true,
                        )
                        ExposedDropdownMenu(expanded = poolMenu, onDismissRequest = { poolMenu = false }) {
                            if (pools.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No registered pools. Type a name.") },
                                    onClick = { poolMenu = false },
                                    enabled = false,
                                )
                            }
                            pools.forEach { pool ->
                                DropdownMenuItem(
                                    text = { Text(pool.line()) },
                                    onClick = {
                                        envName = pool.poolName
                                        poolMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        "Self-hosted pool from List Pools. Unknown names fail with 400.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ExposedDropdownMenuBox(expanded = modelMenu, onExpandedChange = { modelMenu = it }) {
                    OutlinedTextField(
                        value = modelId.ifBlank { "Account default" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenu) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Account default") },
                            onClick = {
                                modelId = ""
                                modelParams = emptyList()
                                modelMenu = false
                            },
                        )
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName ?: model.id) },
                                onClick = {
                                    modelId = model.id
                                    modelParams = model.defaultParams()
                                    modelMenu = false
                                },
                            )
                        }
                    }
                }
                ModelParamRow(
                    model = models.firstOrNull { it.id == modelId },
                    params = modelParams,
                    onParams = { modelParams = it },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == "agent", onClick = { mode = "agent" }, label = { Text("Agent") })
                    FilterChip(selected = mode == "plan", onClick = { mode = "plan" }, label = { Text("Plan") })
                }

                AttachChips(items = attaches, onItems = { attaches = it })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    AttachButton(items = attaches, onItems = { attaches = it }, enabled = !loading)
                    if (container.store.showMicrophone) {
                        VoiceButton(enabled = !loading) { spoken ->
                            prompt = if (prompt.isBlank()) spoken else "$prompt $spoken"
                        }
                    }
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Task") },
                        minLines = 5,
                    )
                }
                Text("Subagents (optional)", style = MaterialTheme.typography.labelLarge)
                Text(
                    "The agent can delegate to these. Max 20. Names cannot be explore, debug, shell, or computerUse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                subagents.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                append(item.name)
                                if (item.model.isNotBlank()) {
                                    append(" · ")
                                    append(item.model)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { subagents = subagents.filter { it !== item } }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove")
                        }
                    }
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (subagents.size < 20) {
                    OutlinedTextField(
                        value = subName,
                        onValueChange = { subName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = subDesc,
                        onValueChange = { subDesc = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("When to use") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = subPrompt,
                        onValueChange = { subPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Prompt") },
                        minLines = 2,
                    )
                    Text("Subagent model", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = subModel.isBlank(),
                            onClick = { subModel = "" },
                            label = { Text("Inherit") },
                        )
                        models.take(6).forEach { model ->
                            FilterChip(
                                selected = subModel == model.id,
                                onClick = { subModel = model.id },
                                label = { Text(model.displayName ?: model.id) },
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val next = DraftSubagent(subName, subDesc, subPrompt, subModel)
                            if (next.ready()) {
                                subagents = subagents + next
                                subName = ""
                                subDesc = ""
                                subPrompt = ""
                                subModel = ""
                            }
                        },
                        enabled = DraftSubagent(subName, subDesc, subPrompt).ready(),
                    ) {
                        Text("Add subagent")
                    }
                }
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                val ready = attaches.filter { it.ok }
                                val pending = DraftSubagent(subName, subDesc, subPrompt, subModel)
                                val allSubs = if (pending.ready()) subagents + pending else subagents
                                val subs = allSubs.toApi()
                                val named = agentName.trim().takeIf { it.isNotEmpty() }
                                if (envType == "cloud" && !cloudFromEnv && createRepo && repoUrl.isBlank()) {
                                    val made = container.repo.createGithubRepo(
                                        name = newRepoName,
                                        privateRepo = newRepoPrivate,
                                        description = null,
                                    )
                                    repos = container.catalog.repos()
                                    repoUrl = made.url
                                    startingRef = made.defaultBranch?.takeIf { it.isNotBlank() } ?: "main"
                                    createRepo = false
                                }
                                val repo = repoUrl.trim()
                                val branch = startingRef.trim()
                                val tip = if (envType == "cloud" && !cloudFromEnv && repo.isNotBlank() && branch.isNotBlank()) {
                                    runCatching { container.repo.branchTip(repo, branch) }.getOrNull()
                                } else {
                                    null
                                }
                                val cloudTarget = if (envType == "cloud") {
                                    cloudCreateTarget(
                                        cloudFromEnv,
                                        envName,
                                        repo,
                                        tip?.sha ?: branch.ifBlank { null },
                                        prUrl,
                                    )
                                } else {
                                    null
                                }
                                val picked = models.firstOrNull { it.id == modelId }
                                val body = CreateAgentRequest(
                                    prompt = Attachments.prompt(prompt, ready),
                                    model = picked?.selection(modelParams),
                                    name = named,
                                    env = if (envType == "cloud") {
                                        cloudTarget?.first
                                    } else {
                                        Env(type = envType, name = envName.trim().ifBlank { null })
                                    },
                                    repos = if (envType == "cloud") {
                                        cloudTarget?.second
                                    } else {
                                        null
                                    },
                                    workOnCurrentBranch = if (envType == "cloud" && !cloudFromEnv) workOnBranch else null,
                                    autoCreatePR = if (envType == "cloud") autoPr else null,
                                    skipReviewerRequest = if (envType == "cloud" && autoPr && skipReviewer) true else null,
                                    mode = mode,
                                    mcpServers = container.store.mcpServers(),
                                    customSubagents = subs,
                                )
                                val created = container.repo.createAgent(body)
                                if (named != null) {
                                    container.chats.setTitle(created.agent.id, named)
                                }
                                if (envType == "cloud" && cloudFromEnv) {
                                    container.catalog.rememberCloudEnv(envName)
                                }
                                if (envType == "cloud" && !cloudFromEnv && repo.isNotBlank()) {
                                    container.chats.setRepoBase(
                                        created.agent.id,
                                        repo,
                                        branch.ifBlank { null },
                                        tip?.sha,
                                    )
                                }
                                container.conversations.save(
                                    created.agent.id,
                                    listOf(
                                        TranscriptLine(
                                            id = "user-${created.run.id}",
                                            kind = "user",
                                            text = Attachments.label(prompt, ready),
                                            runId = created.run.id,
                                        ),
                                    ).withStartupNotice(envType),
                                    runId = created.run.id,
                                    runStatus = created.run.status,
                                    immediate = true,
                                )
                                RunWatchScheduler.watch(
                                    context.applicationContext,
                                    created.agent.id,
                                    created.run.id,
                                    created.agent.name,
                                )
                                container.notifier.notifyIfNeeded(
                                    created.agent.id,
                                    created.agent.name,
                                    created.run.id,
                                    created.run.status,
                                    null,
                                )
                                container.drafts.clear(DraftStore.NEW_AGENT)
                                onCreated(created.agent.id)
                            } catch (e: Exception) {
                                error = e.message ?: "Create failed"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = (prompt.isNotBlank() || attaches.any { it.ok }) && !loading && when (envType) {
                        "machine" -> envName.isNotBlank()
                        "cloud" -> {
                            if (cloudFromEnv) {
                                envName.trim().isNotBlank()
                            } else if (createRepo && repoUrl.isBlank()) {
                                GithubRepos.sanitizeName(newRepoName).isNotBlank() &&
                                    !container.store.githubToken.isNullOrBlank()
                            } else {
                                repoUrl.isNotBlank() && (startingRef.isNotBlank() || prUrl.isNotBlank())
                            }
                        }
                        else -> true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (loading) "Starting…" else "Start agent")
                }
            }
        }
    }
}
