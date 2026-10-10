package com.cursorandroid.app.ui.composeAgent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.cursorandroid.app.ui.AppInsets
import com.cursorandroid.app.ui.scaffoldBars
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.Env
import com.cursorandroid.app.data.api.ModelParam
import com.cursorandroid.app.data.api.cloudCreateTarget
import com.cursorandroid.app.data.api.gitHost
import com.cursorandroid.app.data.api.gitPath
import com.cursorandroid.app.data.api.machineCreateTarget
import com.cursorandroid.app.data.api.matchRepo
import com.cursorandroid.app.data.api.prettyProvider
import com.cursorandroid.app.data.api.defaultParams
import com.cursorandroid.app.data.api.namedCloudEnvironments
import com.cursorandroid.app.data.api.environmentChoices
import com.cursorandroid.app.data.api.environmentConfigJson
import com.cursorandroid.app.data.api.environmentOwner
import com.cursorandroid.app.data.api.resolvedEnvironmentJson
import com.cursorandroid.app.data.api.activeBuildSummary
import com.cursorandroid.app.data.api.poolAgentRepos
import com.cursorandroid.app.data.api.acceptsManyRepos
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
import com.cursorandroid.app.data.repo.mcpServersFor
import com.cursorandroid.app.data.repo.prefixPrompt
import com.cursorandroid.app.data.repo.toApi
import com.cursorandroid.app.data.repo.toDraft
import com.cursorandroid.app.ui.chat.AttachButton
import com.cursorandroid.app.ui.chat.AttachChips
import com.cursorandroid.app.ui.chat.ModelParamRow
import com.cursorandroid.app.ui.chat.VoiceButton
import com.cursorandroid.app.data.repo.ForgeClient
import com.cursorandroid.app.data.repo.machineKey
import com.cursorandroid.app.data.repo.visibleMachines
import com.cursorandroid.app.ui.MachineActionsDialog
import com.cursorandroid.app.ui.MachineMenuRow
import com.cursorandroid.app.data.repo.filterRepos
import com.cursorandroid.app.data.repo.forgeForLabel
import com.cursorandroid.app.data.repo.forgeSupportsRepoList
import com.cursorandroid.app.data.repo.mergeRepos
import com.cursorandroid.app.data.repo.sourceLabels
import kotlinx.coroutines.delay
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
    var forges by remember { mutableStateOf(container.store.forges()) }
    var forgeRepos by remember { mutableStateOf<List<RepositoryItem>>(emptyList()) }
    var forgePage by remember { mutableStateOf(1) }
    var forgeMore by remember { mutableStateOf(false) }
    var forgeLoading by remember { mutableStateOf(false) }
    var repoUrl by remember { mutableStateOf("") }
    var startingRef by remember { mutableStateOf("") }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingRepos by remember { mutableStateOf(repos.isEmpty()) }
    var loadingBranches by remember { mutableStateOf(false) }
    var autoPr by remember { mutableStateOf(true) }
    var workOnBranch by remember { mutableStateOf(false) }
    var skipReviewer by remember { mutableStateOf(false) }
    var prUrl by remember { mutableStateOf("") }
    var promptPrefix by remember { mutableStateOf("") }
    var repoMcpIds by remember { mutableStateOf<List<String>?>(null) }
    var openFinishedPr by remember { mutableStateOf(false) }
    var seededRepo by remember { mutableStateOf<String?>(null) }
    var heldBranchRepo by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("agent") }
    var models by remember { mutableStateOf(container.repo.cachedModels()) }
    var modelId by remember { mutableStateOf("") }
    var modelParams by remember { mutableStateOf<List<ModelParam>>(emptyList()) }
    var modelMenu by remember { mutableStateOf(false) }
    var computers by remember { mutableStateOf(container.catalog.computers()) }
    var computerMenu by remember { mutableStateOf(false) }
    var machinePrefs by remember { mutableStateOf(container.machines.prefs()) }
    var machineAction by remember { mutableStateOf<Computer?>(null) }
    var selectedWorkerId by remember { mutableStateOf<String?>(null) }
    var pools by remember { mutableStateOf(container.catalog.pools()) }
    var poolMenu by remember { mutableStateOf(false) }
    var repoMenu by remember { mutableStateOf(false) }
    var branchMenu by remember { mutableStateOf(false) }
    var repoQuery by remember { mutableStateOf("") }
    var extraRepos by remember { mutableStateOf(listOf<String>()) }
    var saveEnvironment by remember { mutableStateOf(false) }
    var environmentName by remember { mutableStateOf("") }
    var envInstall by remember { mutableStateOf("") }
    var savedEnvs by remember { mutableStateOf(container.catalog.savedEnvironments()) }
    var selectedEnvId by remember { mutableStateOf<String?>(null) }
    var envStatus by remember { mutableStateOf("") }
    var envOwner by remember { mutableStateOf("personal") }
    var envJson by remember { mutableStateOf("") }
    var createEnvRepos by remember { mutableStateOf(listOf<String>()) }
    var envBusy by remember { mutableStateOf(false) }
    var createRepo by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("") }
    var newRepoPrivate by remember { mutableStateOf(true) }
    var cloudFromEnv by remember {
        mutableStateOf(!initialEnvName.isNullOrBlank() && initialEnvType == "cloud")
    }
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
    val shownComputers = remember(computers, machinePrefs) {
        visibleMachines(computers, machinePrefs, container.machines.now())
    }
    val allRepos = remember(repos, forgeRepos) { mergeRepos(repos, forgeRepos) }
    val providers = remember(repos, forges) { sourceLabels(repos, forges) }
    val createForge = remember(forges, provider) { forgeForLabel(forges, provider) }
    val listsRepos = createForge != null && forgeSupportsRepoList(createForge)
    val providerRepos = remember(allRepos, provider) {
        allRepos.filter { provider.isBlank() || it.providerLabel() == provider }
    }
    val selectedRepo = remember(allRepos, repoUrl) { matchRepo(allRepos, repoUrl) }
    fun adoptRepo(url: String) {
        repoUrl = url
        val item = matchRepo(allRepos, url)
        val label = item?.providerLabel() ?: prettyProvider(gitHost(url))
        if (label.isNotBlank()) provider = label
    }
    fun loadMoreForgeRepos() {
        val forge = createForge ?: return
        scope.launch {
            forgeLoading = true
            val next = forgePage + 1
            val page = ForgeClient.listRepos(forge, repoQuery, next)
            forgePage = next
            forgeRepos = mergeRepos(forgeRepos, page.repos)
            forgeMore = page.hasMore
            forgeLoading = false
        }
    }
    val cloudChoices = environmentChoices(
        savedEnvs,
        container.catalog.agents().namedCloudEnvironments(container.catalog.cloudEnvs() + listOf(envName)),
    )

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
        if (saved.extraRepos.isNotEmpty()) extraRepos = saved.extraRepos
        saveEnvironment = saved.saveEnvironment
        if (saved.environmentName.isNotBlank()) environmentName = saved.environmentName
        if (saved.envInstall.isNotBlank()) envInstall = saved.envInstall
        if (saved.envOwner.isNotBlank()) envOwner = environmentOwner(saved.envOwner)
        if (saved.environmentJson.isNotBlank()) envJson = saved.environmentJson
        if (saved.createEnvRepos.isNotEmpty()) createEnvRepos = saved.createEnvRepos
        savedEnvs = container.catalog.savedEnvironments()
        selectedEnvId = if (!initialEnvName.isNullOrBlank() && initialEnvType == "cloud") {
            savedEnvs.firstOrNull { it.id.isNotBlank() && it.name.equals(envName, ignoreCase = true) }?.id
        } else {
            saved.selectedEnvId.takeIf { it.isNotBlank() }
                ?: savedEnvs.firstOrNull { it.id.isNotBlank() && it.name.equals(envName, ignoreCase = true) }?.id
        }
        if (saved.modelParams.isNotEmpty()) modelParams = saved.modelParams
        subagents = saved.resolvedSubagents()
        seededRepo = repoUrl
        draftReady = true
    }

    LaunchedEffect(repoUrl, draftReady) {
        if (!draftReady) return@LaunchedEffect
        val seed = seededRepo ?: return@LaunchedEffect
        if (repoUrl == seed) return@LaunchedEffect
        val defaults = container.store.repoDefault(repoUrl)
        if (defaults == null) {
            promptPrefix = ""
            repoMcpIds = null
            openFinishedPr = false
            heldBranchRepo = ""
            val fallback = container.store.defaultModel
            if (fallback.isNotBlank()) {
                modelId = fallback
                modelParams = models.firstOrNull { it.id == fallback }?.defaultParams().orEmpty()
            }
            return@LaunchedEffect
        }
        if (defaults.modelId.isNotBlank()) {
            modelId = defaults.modelId
            modelParams = defaults.modelParams
        }
        if (defaults.branch.isNotBlank()) {
            startingRef = defaults.branch
            heldBranchRepo = repoUrl
        } else {
            heldBranchRepo = ""
        }
        defaults.autoCreatePr?.let { autoPr = it }
        defaults.skipReviewer?.let { skipReviewer = it }
        promptPrefix = defaults.promptPrefix
        repoMcpIds = defaults.mcpIds
        openFinishedPr = defaults.openFinishedPr
        if (defaults.environmentName.isNotBlank()) {
            envType = "cloud"
            cloudFromEnv = true
            envName = defaults.environmentName
            selectedEnvId = defaults.environmentId.takeIf { it.isNotBlank() }
                ?: savedEnvs.firstOrNull { it.name.equals(defaults.environmentName, ignoreCase = true) }?.id
        }
    }

    LaunchedEffect(
        agentName, prompt, modelId, modelParams, mode, attaches, envType, envName, provider, repoUrl,
        startingRef, autoPr, workOnBranch, skipReviewer, prUrl, subagents, extraRepos,
        saveEnvironment, environmentName, envInstall, envOwner, selectedEnvId, envJson,
        createEnvRepos, draftReady,
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
                extraRepos = extraRepos,
                saveEnvironment = saveEnvironment,
                environmentName = environmentName,
                envInstall = envInstall,
                envOwner = envOwner,
                selectedEnvId = selectedEnvId.orEmpty(),
                environmentJson = envJson,
                createEnvRepos = createEnvRepos,
            ),
        )
    }

    LaunchedEffect(selectedEnvId) {
        val id = selectedEnvId?.takeIf { it.isNotBlank() }
        if (id == null) {
            envStatus = ""
            return@LaunchedEffect
        }
        envStatus = "Loading builds…"
        val detail = runCatching { container.repo.getCloudEnvironment(id) }.getOrNull()
        if (selectedEnvId != id) return@LaunchedEffect
        if (detail != null) {
            savedEnvs = container.catalog.savedEnvironments()
            if (detail.name.isNotBlank()) envName = detail.name
        }
        val active = runCatching { container.repo.activeEnvironmentBuild(id) }.getOrNull()
        val listed = runCatching { container.repo.listEnvironmentBuilds(id) }.getOrNull()
        if (selectedEnvId != id) return@LaunchedEffect
        var latest = listed?.items?.firstOrNull()
        val activeId = active?.buildId
        if (!activeId.isNullOrBlank() && latest?.id != activeId) {
            latest = runCatching { container.repo.getEnvironmentBuild(id, activeId) }.getOrNull() ?: latest
        }
        if (selectedEnvId != id) return@LaunchedEffect
        envStatus = when {
            active == null && listed == null -> "Build status unavailable"
            else -> activeBuildSummary(active, latest).ifBlank { "No build yet" }
        }
    }

    LaunchedEffect(Unit) {
        models = runCatching { container.repo.models() }.getOrDefault(models)
        scope.launch {
            computers = runCatching { container.repo.listComputers(container.catalog.agents()) }.getOrDefault(computers)
            machinePrefs = container.machines.prefs()
            pools = runCatching { container.repo.listPools() }.getOrDefault(pools)
            if (envType == "machine") {
                val usable = visibleMachines(computers, machinePrefs, container.machines.now())
                val picked = usable.firstOrNull { it.name.equals(envName, ignoreCase = true) && it.online }
                    ?: usable.firstOrNull { it.name.equals(envName, ignoreCase = true) }
                    ?: usable.firstOrNull { it.online }
                if (picked != null) {
                    envName = picked.name
                    selectedWorkerId = picked.workerId
                    picked.boundRepo()?.let { adoptRepo(it) }
                }
                workOnBranch = true
            }
            if (envType == "pool" && envName.isBlank()) {
                pools.firstOrNull()?.let { envName = it.poolName }
            }
        }
        loadingRepos = repos.isEmpty()
        repos = runCatching { container.repo.repositories() }.getOrDefault(repos)
        loadingRepos = false
        if (runCatching { container.repo.environments() }.isSuccess) {
            savedEnvs = container.catalog.savedEnvironments()
        }
    }

    LaunchedEffect(providers, envType) {
        if (providers.isEmpty()) return@LaunchedEffect
        if (provider.isBlank()) {
            provider = providers.first()
            return@LaunchedEffect
        }
        if (envType != "machine" && provider !in providers) {
            provider = providers.first()
        }
    }

    LaunchedEffect(provider, forges, repoQuery) {
        val forge = createForge
        forgePage = 1
        if (forge == null || !listsRepos) {
            forgeMore = false
            forgeLoading = false
            return@LaunchedEffect
        }
        if (repoQuery.isNotBlank()) delay(350)
        forgeLoading = true
        val page = ForgeClient.listRepos(forge, repoQuery, 1)
        forgeRepos = mergeRepos(forgeRepos, page.repos)
        forgeMore = page.hasMore
        forgeLoading = false
    }

    LaunchedEffect(resetTick, envType) {
        forges = container.store.forges()
    }

    LaunchedEffect(provider) {
        if (createForge == null) {
            createRepo = false
            newRepoName = ""
        }
    }

    LaunchedEffect(provider, providerRepos, createRepo, envType) {
        if (createRepo) return@LaunchedEffect
        if (envType == "machine") return@LaunchedEffect
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
        fun fillBranch(names: List<String>) {
            val hold = heldBranchRepo == repoUrl && startingRef.isNotBlank()
            if (hold) return
            if (startingRef.isBlank() || (names.isNotEmpty() && startingRef !in names)) {
                startingRef = selectedRepo?.defaultBranch?.takeIf { it in names }
                    ?: names.firstOrNull().orEmpty()
            }
        }
        val cached = container.catalog.branches(repoUrl)
        if (cached.isNotEmpty()) {
            branches = cached
            fillBranch(cached)
        }
        val next = runCatching {
            container.repo.branches(repoUrl, selectedRepo?.defaultBranch)
        }.getOrDefault(cached)
        branches = next
        fillBranch(next)
        loadingBranches = false
    }

    if (showBack) BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        contentWindowInsets = AppInsets.bars,
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
                .scaffoldBars(padding)
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
                    FilterChip(
                        selected = envType == "machine",
                        onClick = {
                            envType = "machine"
                            workOnBranch = true
                        },
                        label = { Text("Machine") },
                    )
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
                        SavedEnvironmentPanel(
                            choices = cloudChoices,
                            name = envName,
                            onName = { typed ->
                                envName = typed
                                selectedEnvId = cloudChoices.firstOrNull {
                                    it.fromApi && it.name.equals(typed.trim(), ignoreCase = true)
                                }?.id
                            },
                            selectedId = selectedEnvId,
                            onPicked = { choice ->
                                envName = choice.name
                                selectedEnvId = choice.id
                            },
                            status = envStatus,
                            onDelete = selectedEnvId?.let { id ->
                                {
                                    scope.launch {
                                        envBusy = true
                                        error = null
                                        try {
                                            container.repo.deleteCloudEnvironment(id)
                                            savedEnvs = container.catalog.savedEnvironments()
                                            if (selectedEnvId == id) {
                                                selectedEnvId = null
                                                envName = ""
                                                envStatus = ""
                                            }
                                        } catch (e: Exception) {
                                            error = e.message ?: "Delete failed"
                                        } finally {
                                            envBusy = false
                                        }
                                    }
                                }
                            },
                            createName = environmentName,
                            onCreateName = { environmentName = it },
                            owner = envOwner,
                            onOwner = { envOwner = it },
                            catalogRepos = repos,
                            repoUrls = createEnvRepos,
                            onRepoUrls = { createEnvRepos = it },
                            environmentJson = envJson,
                            onEnvironmentJson = { envJson = it },
                            busy = envBusy || loading,
                            onCreate = {
                                scope.launch {
                                    val savedName = environmentName.trim()
                                    if (savedName.isBlank()) {
                                        error = "Name the environment."
                                        return@launch
                                    }
                                    envBusy = true
                                    error = null
                                    try {
                                        val made = container.repo.createCloudEnvironment(
                                            savedName,
                                            createEnvRepos,
                                            resolvedEnvironmentJson(envJson),
                                            envOwner,
                                        )
                                        savedEnvs = container.catalog.savedEnvironments()
                                        envName = made.name.ifBlank { savedName }
                                        selectedEnvId = made.id.takeIf { it.isNotBlank() }
                                        environmentName = ""
                                    } catch (e: Exception) {
                                        error = e.message ?: "Create failed"
                                    } finally {
                                        envBusy = false
                                    }
                                }
                            },
                        )
                    } else {
                    SourcePicker(
                        sources = providers,
                        selected = provider,
                        loading = loadingRepos,
                        emptyLabel = "No source connected",
                        onPick = { provider = it },
                    )
                    if (listsRepos) {
                        ForgeRepoSearch(
                            query = repoQuery,
                            onQuery = { repoQuery = it },
                            loading = forgeLoading,
                            hasMore = forgeMore,
                            onMore = { loadMoreForgeRepos() },
                        )
                    } else if (providerRepos.size > 12) {
                        OutlinedTextField(
                            value = repoQuery,
                            onValueChange = { repoQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Filter repos") },
                            singleLine = true,
                        )
                    }
                    val visibleRepos = filterRepos(providerRepos, repoQuery)
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
                            if (createForge != null) {
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
                                        extraRepos = extraRepos.filterNot { it.equals(repo.url, ignoreCase = true) }
                                        repoMenu = false
                                    },
                                )
                            }
                        }
                    }
                    ExtraReposField(
                        repos = providerRepos,
                        selected = extraRepos,
                        primaryUrl = repoUrl,
                        onSelected = { extraRepos = it },
                    )
                    SaveEnvironmentField(
                        save = saveEnvironment,
                        onSave = { saveEnvironment = it },
                        name = environmentName,
                        onName = { environmentName = it },
                        install = envInstall,
                        onInstall = { envInstall = it },
                        owner = envOwner,
                        onOwner = { envOwner = it },
                    )
                    if (createRepo && repoUrl.isBlank()) {
                        val sanitized = GithubRepos.sanitizeName(newRepoName)
                        val hasToken = createForge != null
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
                                            "Add a forge with a token in Settings > Connections."
                                        sanitized.isNotBlank() && sanitized != newRepoName.trim() ->
                                            "Will be $sanitized"
                                        else ->
                                            "Creates a private repo on ${createForge?.displayName() ?: "this forge"}."
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
                    val online = shownComputers.filter { it.online }
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
                                MachineMenuRow(
                                    onClick = {
                                        envName = computer.name
                                        selectedWorkerId = computer.workerId
                                        computer.boundRepo()?.let { adoptRepo(it) }
                                        computerMenu = false
                                    },
                                    onLongClick = { machineAction = computer },
                                    onMore = { machineAction = computer },
                                ) {
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
                            }
                            shownComputers.filter { !it.online }.forEach { computer ->
                                MachineMenuRow(
                                    onClick = {
                                        envName = computer.name
                                        selectedWorkerId = computer.workerId
                                        computer.boundRepo()?.let { adoptRepo(it) }
                                        computerMenu = false
                                    },
                                    onLongClick = { machineAction = computer },
                                    onMore = { machineAction = computer },
                                ) {
                                    Text("${computer.name} · offline")
                                }
                            }
                        }
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
                    val hiddenCount = computers.size - shownComputers.size
                    if (hiddenCount > 0) {
                        Text(
                            "$hiddenCount hidden. Unhide them under Settings, Connections, Machines. Long-press a machine to hide or delete it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Online machines you are signed into. The PC must stay awake with Remote Control or a My Machines worker. The public API needs a repo on the request, even when the checkout is already on the PC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SourcePicker(
                        sources = providers,
                        selected = provider,
                        loading = loadingRepos,
                        emptyLabel = "Any source",
                        onPick = { provider = it },
                    )
                    if (listsRepos) {
                        ForgeRepoSearch(
                            query = repoQuery,
                            onQuery = { repoQuery = it },
                            loading = forgeLoading,
                            hasMore = forgeMore,
                            onMore = { loadMoreForgeRepos() },
                        )
                    }
                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Repository URL") },
                        placeholder = { Text("https://github.com/org/repo") },
                        singleLine = true,
                        supportingText = {
                            Text("Any HTTPS git URL the Cloud Agents API accepts, including GitLab, Bitbucket, Azure, or Origin.")
                        },
                    )
                    ExposedDropdownMenuBox(expanded = repoMenu, onExpandedChange = { repoMenu = it }) {
                        OutlinedTextField(
                            value = when {
                                selectedRepo != null -> selectedRepo.displayName()
                                repoUrl.isNotBlank() -> gitPath(repoUrl).ifBlank { repoUrl }
                                loadingRepos -> "Loading repos…"
                                else -> "Select a repo"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Repository") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repoMenu) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = repoMenu, onDismissRequest = { repoMenu = false }) {
                            val shownRepos = filterRepos(providerRepos, repoQuery)
                            if (shownRepos.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No repos for this source") },
                                    onClick = { repoMenu = false },
                                    enabled = false,
                                )
                            }
                            shownRepos.forEach { repo ->
                                DropdownMenuItem(
                                    text = { Text(repo.displayName()) },
                                    onClick = {
                                        adoptRepo(repo.url)
                                        repoMenu = false
                                    },
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(expanded = branchMenu, onExpandedChange = { branchMenu = it }) {
                        OutlinedTextField(
                            value = startingRef,
                            onValueChange = { startingRef = it },
                            enabled = repoUrl.isNotBlank(),
                            label = { Text("Branch") },
                            placeholder = {
                                Text(if (loadingBranches) "Loading branches…" else "main")
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchMenu) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryEditable)
                                .fillMaxWidth(),
                            singleLine = true,
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
                        "Self-hosted pool from List Pools. Unknown names fail with 400. Any-repo pools can take several repos. The default pool and repo-backed pools take one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val selectedPool = pools.firstOrNull { it.poolName.equals(envName.trim(), ignoreCase = true) }
                    if (selectedPool?.acceptsManyRepos() == true) {
                        SourcePicker(
                            sources = providers,
                            selected = provider,
                            loading = loadingRepos,
                            emptyLabel = "Any source",
                            onPick = { provider = it },
                        )
                        if (listsRepos) {
                            ForgeRepoSearch(
                                query = repoQuery,
                                onQuery = { repoQuery = it },
                                loading = forgeLoading,
                                hasMore = forgeMore,
                                onMore = { loadMoreForgeRepos() },
                            )
                        }
                        ExtraReposField(
                            repos = filterRepos(providerRepos, repoQuery),
                            selected = extraRepos,
                            primaryUrl = "",
                            onSelected = { extraRepos = it },
                        )
                    }
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
                                    val made = container.repo.createOnForge(
                                        providerLabel = provider,
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
                                val selectedPool = pools.firstOrNull { it.poolName.equals(envName.trim(), ignoreCase = true) }
                                if (envType == "pool" && extraRepos.size > 1 && selectedPool?.acceptsManyRepos() != true) {
                                    error = "This pool accepts one repo. Name an any-repo pool to send several."
                                    return@launch
                                }
                                var launchFromEnv = cloudFromEnv
                                var launchEnvName = envName
                                if (envType == "cloud" && !cloudFromEnv && saveEnvironment) {
                                    val savedName = environmentName.trim()
                                    if (savedName.isBlank()) {
                                        error = "Name the environment."
                                        return@launch
                                    }
                                    val made = container.repo.createCloudEnvironment(
                                        savedName,
                                        listOf(repo) + extraRepos,
                                        environmentConfigJson(envInstall),
                                        envOwner,
                                    )
                                    savedEnvs = container.catalog.savedEnvironments()
                                    selectedEnvId = made.id.takeIf { it.isNotBlank() }
                                    container.catalog.rememberCloudEnv(made.name.ifBlank { savedName })
                                    launchFromEnv = true
                                    launchEnvName = made.name.ifBlank { savedName }
                                }
                                val tip = if (envType == "cloud" && !cloudFromEnv && repo.isNotBlank() && branch.isNotBlank()) {
                                    runCatching { container.repo.branchTip(repo, branch) }.getOrNull()
                                } else {
                                    null
                                }
                                val cloudTarget = if (envType == "cloud") {
                                    cloudCreateTarget(
                                        launchFromEnv,
                                        launchEnvName,
                                        repo,
                                        tip?.sha ?: branch.ifBlank { null },
                                        prUrl,
                                        extraRepoUrls = if (launchFromEnv) emptyList() else extraRepos,
                                    )
                                } else {
                                    null
                                }
                                val pooledRepos = if (envType == "pool") poolAgentRepos(selectedPool, extraRepos) else null
                                val computer = computers.firstOrNull {
                                    !selectedWorkerId.isNullOrBlank() && it.workerId == selectedWorkerId
                                } ?: computers.firstOrNull { it.name.equals(envName.trim(), ignoreCase = true) }
                                val machineRepo = repo.ifBlank { computer?.boundRepo().orEmpty() }
                                val machineTarget = if (envType == "machine") {
                                    if (machineRepo.isBlank()) {
                                        error = "Pick a repository the machine has checked out. A machine start with no repo is rejected."
                                        return@launch
                                    }
                                    machineCreateTarget(envName, machineRepo, branch.ifBlank { null })
                                } else {
                                    null
                                }
                                val picked = models.firstOrNull { it.id == modelId }
                                val body = CreateAgentRequest(
                                    prompt = Attachments.prompt(prefixPrompt(promptPrefix, prompt), ready),
                                    model = picked?.selection(modelParams),
                                    name = named,
                                    env = when (envType) {
                                        "cloud" -> cloudTarget?.first
                                        "machine" -> machineTarget?.first
                                        else -> Env(type = envType, name = envName.trim().ifBlank { null })
                                    },
                                    repos = when (envType) {
                                        "cloud" -> cloudTarget?.second
                                        "machine" -> machineTarget?.second
                                        else -> pooledRepos
                                    },
                                    workOnCurrentBranch = when {
                                        envType == "cloud" && !launchFromEnv -> workOnBranch
                                        envType == "machine" -> workOnBranch
                                        else -> null
                                    },
                                    autoCreatePR = if (envType == "cloud") autoPr else null,
                                    skipReviewerRequest = if (envType == "cloud" && autoPr && skipReviewer) true else null,
                                    mode = mode,
                                    mcpServers = mcpServersFor(container.store.storedMcps(), repoMcpIds),
                                    customSubagents = subs,
                                )
                                val created = container.repo.createAgent(body)
                                container.runModels.recordRun(created.agent.id, created.run.id, body.model, explicit = true)
                                if (openFinishedPr) {
                                    container.chats.setOpenFinishedPr(created.agent.id, true)
                                }
                                if (named != null) {
                                    container.chats.setTitle(created.agent.id, named)
                                }
                                if (envType == "cloud" && launchFromEnv) {
                                    container.catalog.rememberCloudEnv(launchEnvName)
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
                                    created.run.status,
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
                        "machine" -> envName.isNotBlank() && repoUrl.isNotBlank() && startingRef.isNotBlank()
                        "cloud" -> {
                            if (cloudFromEnv) {
                                envName.trim().isNotBlank()
                            } else if (createRepo && repoUrl.isBlank()) {
                                GithubRepos.sanitizeName(newRepoName).isNotBlank() &&
                                    createForge != null
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
