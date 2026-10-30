package com.cursorandroid.app.ui.composeAgent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.ui.AppInsets
import com.cursorandroid.app.ui.scaffoldBars
import com.cursorandroid.app.data.api.ApiException
import com.cursorandroid.app.data.api.Computer
import com.cursorandroid.app.data.api.CreateAgentRequest
import com.cursorandroid.app.data.api.Env
import com.cursorandroid.app.data.api.EnvironmentHistoryEvent
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
import com.cursorandroid.app.data.api.defaultOpenAsCursorGithubApp
import com.cursorandroid.app.data.api.envVarMap
import com.cursorandroid.app.data.api.envVarsError
import com.cursorandroid.app.data.api.resolveStartingRef
import com.cursorandroid.app.data.api.startingRefError
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
import com.cursorandroid.app.data.repo.agentIdEnvVarsError
import com.cursorandroid.app.data.repo.ChatDraft
import com.cursorandroid.app.data.repo.DraftStore
import com.cursorandroid.app.data.repo.DraftSubagent
import com.cursorandroid.app.data.repo.GithubRepos
import com.cursorandroid.app.data.repo.mcpServersFor
import com.cursorandroid.app.data.repo.prefixPrompt
import com.cursorandroid.app.data.repo.visibleUserText
import com.cursorandroid.app.data.repo.toApi
import com.cursorandroid.app.data.repo.toDraft
import com.cursorandroid.app.ui.chat.AttachButton
import com.cursorandroid.app.ui.chat.AttachChips
import com.cursorandroid.app.ui.chat.ModelParamRow
import com.cursorandroid.app.ui.chat.ModelPickerAnchorField
import com.cursorandroid.app.ui.chat.ModelPickerSheet
import com.cursorandroid.app.ui.chat.VoiceButton
import com.cursorandroid.app.data.repo.ForgeClient
import com.cursorandroid.app.data.repo.machineKey
import com.cursorandroid.app.data.repo.visibleMachines
import com.cursorandroid.app.ui.MachineActionsDialog
import com.cursorandroid.app.ui.MachineMenuRow
import com.cursorandroid.app.data.repo.filterRepos
import com.cursorandroid.app.data.repo.forgeForLabel
import com.cursorandroid.app.data.repo.groupReposByForge
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
    val sync by container.forgeSync.state.collectAsState()
    var refreshingSources by remember { mutableStateOf(false) }
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
    var models by remember { mutableStateOf<List<ModelItem>>(emptyList()) }
    var modelId by remember { mutableStateOf("") }
    var modelParams by remember { mutableStateOf<List<ModelParam>>(emptyList()) }
    var modelPickerOpen by remember { mutableStateOf(false) }
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
    var envHistory by remember { mutableStateOf<List<EnvironmentHistoryEvent>>(emptyList()) }
    var envBusy by remember { mutableStateOf(false) }
    var clientAgentId by remember { mutableStateOf("") }
    var sessionEnv by remember { mutableStateOf(listOf("" to "")) }
    var runEnv by remember { mutableStateOf(listOf("" to "")) }
    var metaRows by remember { mutableStateOf(listOf("" to "")) }
    var openAsExplicit by remember { mutableStateOf<Boolean?>(null) }
    var accountMe by remember { mutableStateOf(container.catalog.me()) }
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
    LaunchedEffect(Unit) {
        if (accountMe != null) return@LaunchedEffect
        accountMe = runCatching { container.repo.refreshMe() }.getOrNull() ?: container.catalog.me()
    }
    var draftReady by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shownComputers = remember(computers, machinePrefs) {
        visibleMachines(computers, machinePrefs, container.machines.now())
    }
    val forgeRepos = remember(sync.reposByForge) { sync.repos() }
    val allRepos = remember(repos, forgeRepos) { mergeRepos(repos, forgeRepos) }
    val providers = remember(repos, forges) { sourceLabels(repos, forges) }
    val createForge = remember(forges, provider) { forgeForLabel(forges, provider) }
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
        if (saved.clientAgentId.isNotBlank()) clientAgentId = saved.clientAgentId
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
                modelParams = container.modelPicker.paramsFor(
                    fallback,
                    models.firstOrNull { it.id == fallback },
                )
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
        createEnvRepos, clientAgentId, draftReady,
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
                clientAgentId = clientAgentId,
            ),
        )
    }

    LaunchedEffect(selectedEnvId) {
        val id = selectedEnvId?.takeIf { it.isNotBlank() }
        if (id == null) {
            envStatus = ""
            envHistory = emptyList()
            return@LaunchedEffect
        }
        envStatus = "Loading builds…"
        val detail = runCatching { container.repo.getCloudEnvironment(id) }.getOrNull()
        if (selectedEnvId != id) return@LaunchedEffect
        if (detail != null) {
            savedEnvs = container.catalog.savedEnvironments()
            if (detail.name.isNotBlank()) envName = detail.name
            detail.environmentJson?.let { envJson = it }
        }
        envHistory = runCatching { container.repo.environmentHistory(id) }.getOrDefault(emptyList())
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
        models = runCatching { container.repo.models() }.getOrDefault(emptyList())
        scope.launch {
            val listed = runCatching { container.repo.listCloudEnvironments() }.getOrNull() ?: return@launch
            savedEnvs = listed
        }
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
        PullToRefreshBox(
            isRefreshing = refreshingSources,
            onRefresh = {
                scope.launch {
                    refreshingSources = true
                    repos = runCatching { container.repo.repositories(force = true) }.getOrDefault(repos)
                    runCatching { container.forgeSync.syncAll() }
                    refreshingSources = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .scaffoldBars(padding),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                ChoiceRow {
                    ChoiceChip(envType == "cloud", "Cloud") { envType = "cloud" }
                    ChoiceChip(envType == "machine", "Machine") {
                        envType = "machine"
                        workOnBranch = true
                    }
                    ChoiceChip(envType == "pool", "Pool") { envType = "pool" }
                    ChoiceChip(envType == "none", "No repo") { envType = "none" }
                }
                if (envType == "cloud") {
                    ChoiceRow {
                        ChoiceChip(!cloudFromEnv, "From repo") { cloudFromEnv = false }
                        ChoiceChip(cloudFromEnv, "Cloud computer") { cloudFromEnv = true }
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
                            history = envHistory,
                            onUpdate = selectedEnvId?.let { id ->
                                {
                                    scope.launch {
                                        envBusy = true
                                        error = null
                                        try {
                                            val updated = container.repo.updateCloudEnvironment(id, envName, envJson)
                                            savedEnvs = container.catalog.savedEnvironments()
                                            envName = updated.name.ifBlank { envName }
                                            updated.environmentJson?.let { envJson = it }
                                            envHistory = runCatching { container.repo.environmentHistory(id) }.getOrDefault(envHistory)
                                        } catch (e: Exception) {
                                            error = (e as? ApiException)?.displayMessage() ?: e.message ?: "Update failed"
                                        } finally {
                                            envBusy = false
                                        }
                                    }
                                }
                            },
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
                    ForgeSyncNotes(forges = forges, provider = provider, sync = sync)
                    if (providerRepos.size > 12) {
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
                            val groups = groupReposByForge(visibleRepos, forges, sync.reposByForge)
                            groups.forEach { group ->
                                if (groups.size > 1) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                group.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                        onClick = {},
                                        enabled = false,
                                    )
                                }
                                group.repos.forEach { repo ->
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
                    ForgeSyncNotes(forges = forges, provider = provider, sync = sync)
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
                } else if (envType == "pool") {
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
                        ForgeSyncNotes(forges = forges, provider = provider, sync = sync)
                        ExtraReposField(
                            repos = filterRepos(providerRepos, repoQuery),
                            selected = extraRepos,
                            primaryUrl = "",
                            onSelected = { extraRepos = it },
                        )
                    }
                } else {
                    Text(
                        "No repository and no environment. The agent starts from the prompt only.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                ModelPickerAnchorField(
                    value = modelId.ifBlank { "Account default" },
                    label = "Model",
                    expanded = modelPickerOpen,
                    onExpandedChange = { modelPickerOpen = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                ModelPickerSheet(
                    open = modelPickerOpen,
                    title = "Model",
                    models = models,
                    selectedModelId = modelId,
                    store = container.modelPicker,
                    onDismiss = { modelPickerOpen = false },
                    onPick = { id ->
                        modelId = id
                        modelParams = container.modelPicker.paramsFor(
                            id,
                            models.firstOrNull { it.id == id },
                        )
                        modelPickerOpen = false
                    },
                )
                ModelParamRow(
                    model = models.firstOrNull { it.id == modelId },
                    params = modelParams,
                    onParams = {
                        modelParams = it
                        if (modelId.isNotBlank()) container.modelPicker.saveParams(modelId, it)
                    },
                )

                ChoiceRow {
                    ChoiceChip(mode == "agent", "Agent") { mode = "agent" }
                    ChoiceChip(mode == "plan", "Plan") { mode = "plan" }
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
                    ChoiceRow {
                        ChoiceChip(subModel.isBlank() || subModel == "inherit", "Inherit") { subModel = "inherit" }
                        models.take(6).forEach { model ->
                            ChoiceChip(subModel == model.id, model.displayName ?: model.id) { subModel = model.id }
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
                if (envType != "none") {
                    val openAsShown = openAsExplicit ?: defaultOpenAsCursorGithubApp(accountMe)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = openAsShown,
                            onCheckedChange = { openAsExplicit = it },
                        )
                        Text("Open PR as Cursor GitHub App")
                    }
                    Text(
                        "User keys default off. Service account keys default on. The value is sent only after you change this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EnvVarFields(
                    title = "Session env vars",
                    hint = "Max 50. Names cannot start with CURSOR_. Cannot be combined with an agent id. A beta account may ignore them.",
                    rows = sessionEnv,
                    onRows = { sessionEnv = it },
                    enabled = clientAgentId.isBlank(),
                )
                EnvVarFields(
                    title = "Run env vars",
                    hint = "For the first run only. Sent when set.",
                    rows = runEnv,
                    onRows = { runEnv = it },
                )
                EnvVarFields(
                    title = "Metadata",
                    hint = "Optional create metadata. Sent when set.",
                    rows = metaRows,
                    onRows = { metaRows = it },
                    maskValues = false,
                )
                OutlinedTextField(
                    value = clientAgentId,
                    onValueChange = { clientAgentId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Agent id (optional)") },
                    placeholder = { Text("bc-…") },
                    singleLine = true,
                    supportingText = {
                        Text("Idempotent create. Leave blank and Cursor assigns one. Cannot be combined with session env vars.")
                    },
                )
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
                                val sessionVars = envVarMap(sessionEnv)
                                val runVars = envVarMap(runEnv)
                                val meta = envVarMap(metaRows)
                                val idError = agentIdEnvVarsError(clientAgentId, sessionVars)
                                    ?: envVarsError(sessionVars)
                                    ?: envVarsError(runVars)
                                    ?: startingRefError(startingRef, workOnBranch, prUrl)
                                if (idError != null) {
                                    error = idError
                                    return@launch
                                }
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
                                val startRef = resolveStartingRef(
                                    branch = branch.ifBlank { null },
                                    commitSha = tip?.sha,
                                    workOnCurrentBranch = workOnBranch,
                                    prUrl = prUrl,
                                    defaultBranch = selectedRepo?.defaultBranch,
                                )
                                val cloudTarget = if (envType == "cloud") {
                                    cloudCreateTarget(
                                        launchFromEnv,
                                        launchEnvName,
                                        repo,
                                        startRef,
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
                                    machineCreateTarget(envName, machineRepo, startRef)
                                } else {
                                    null
                                }
                                val picked = models.firstOrNull { it.id == modelId }
                                val noRepo = envType == "none"
                                val body = CreateAgentRequest(
                                    prompt = Attachments.prompt(prefixPrompt(promptPrefix, prompt), ready),
                                    model = picked?.selection(modelParams),
                                    name = named,
                                    env = when {
                                        noRepo -> null
                                        envType == "cloud" -> cloudTarget?.first
                                        envType == "machine" -> machineTarget?.first
                                        else -> Env(type = envType, name = envName.trim().ifBlank { null })
                                    },
                                    repos = when {
                                        noRepo -> null
                                        envType == "cloud" -> cloudTarget?.second
                                        envType == "machine" -> machineTarget?.second
                                        else -> pooledRepos
                                    },
                                    workOnCurrentBranch = when {
                                        noRepo -> null
                                        envType == "cloud" && !launchFromEnv -> workOnBranch
                                        envType == "machine" -> workOnBranch
                                        else -> null
                                    },
                                    autoCreatePR = if (envType == "cloud" && !noRepo) autoPr else null,
                                    skipReviewerRequest = if (envType == "cloud" && !noRepo && autoPr && skipReviewer) true else null,
                                    agentId = clientAgentId.trim().ifBlank { null },
                                    envVars = sessionVars.takeIf { it.isNotEmpty() },
                                    runEnvVars = runVars.takeIf { it.isNotEmpty() },
                                    metadata = meta.takeIf { it.isNotEmpty() },
                                    openAsCursorGithubApp = if (envType == "none") null else openAsExplicit,
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
                                            text = visibleUserText(body.prompt.text).ifBlank {
                                                Attachments.label(prompt, ready)
                                            },
                                            runId = created.run.id,
                                        ),
                                    ).withStartupNotice(if (noRepo) "none" else envType),
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
                                error = (e as? ApiException)?.displayMessage() ?: e.message ?: "Create failed"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = (prompt.isNotBlank() || attaches.any { it.ok }) && !loading && when (envType) {
                        "none" -> true
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
}

@Composable
private fun ChoiceRow(chips: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips()
    }
}

@Composable
private fun ChoiceChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.wrapContentWidth(unbounded = true),
        label = {
            Text(
                label,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
