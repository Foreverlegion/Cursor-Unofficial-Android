package com.cursorandroid.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.api.AccountOverview
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.notify.BatteryExemption
import com.cursorandroid.app.data.notify.NotifyPermission
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.repo.AppUpdate
import com.cursorandroid.app.data.repo.FeedbackPolicy
import com.cursorandroid.app.data.repo.GithubRepos
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.data.repo.UsageSample
import com.cursorandroid.app.data.repo.displayTotal
import com.cursorandroid.app.ui.AppInsets
import com.cursorandroid.app.ui.inbox.HideFinishedAge
import com.cursorandroid.app.ui.scaffoldBars
import com.cursorandroid.app.ui.theme.ThemeColorPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private enum class SettingsPage(val title: String, val summary: String) {
    Home("Settings", ""),
    Appearance("Appearance", "Theme color"),
    AgentList("Agent list", "Grouping, cards, and filters"),
    RepoDefaults("Repo defaults", "Model, branch, and PR per repo"),
    Chats("Chats & threads", "Tools, thinking, and model"),
    Notifications("Notifications", "Run alerts and battery"),
    Connections("Connections", "Links, MCP, and forges"),
    Forges("Forges", "Git hosts and tokens"),
    Backup("Backup", "Export and import"),
    Feedback("Feedback", "Bugs and feature requests"),
    About("About & account", "Stats, usage, and sign out"),
}

private const val LINK_INFO =
    "https://cursor.com/agents links are not verified, so Android shows a chooser until you allow them under Open by default."
private const val INBOX_TAB_INFO =
    "Cloud always stays. Hide Pool or Remote if you do not use them. Hidden tabs fold back into Cloud."
private const val BATTERY_INFO =
    "WorkManager polls for agent notifications while a run is active, then every 15 minutes. Android Doze stops that work when battery use is optimized."
private const val ALERT_INFO = "Alerts stay on this phone. Cursor has no mobile push, so a finish notice can lag in the background."
private const val MCP_INFO =
    "Saved on this phone. Enabled servers are attached to new agents and follow-ups. The agent calls their tools."
private const val GITHUB_INFO = "Forge tokens stay encrypted on this phone. They list branches and create repos from New agent. A GitHub token already saved on this phone is kept as a GitHub forge."
private const val REMOTE_INFO =
    "On the PC: Cursor 3.9.8 or newer, Agents Window, Settings, Agents, Remote Control, then /remote-control. Local remotes show under Remote. To start new work on a named machine, use New agent, Machine."
private const val BACKUP_INFO =
    "Export includes the API key, forge tokens, repo defaults, theme, inbox tabs, alerts, model, MCP, chat names, favorites, pins, drafts, and cached transcripts. Keep the file private."
private const val KEY_INFO =
    "The key stays on this phone across updates, stored encrypted. Uninstall wipes it unless you import an export."
private const val USAGE_INFO =
    "Cloud Agents token usage only. Desktop and team totals live on the Cursor dashboard."

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    showBack: Boolean,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onSessionChanged: () -> Unit = {},
    onAppearanceChanged: () -> Unit = {},
    openAccountTick: Int = 0,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(SettingsPage.Home) }
    var under by remember { mutableStateOf(SettingsPage.Home) }
    fun closePage() {
        if (page == SettingsPage.Home) {
            onBack()
        } else {
            page = under
            under = SettingsPage.Home
        }
    }
    var info by remember { mutableStateOf<String?>(null) }
    var overview by remember { mutableStateOf<AccountOverview?>(null) }
    var overviewError by remember { mutableStateOf<String?>(null) }
    var overviewLoading by remember { mutableStateOf(true) }
    var overviewAttempt by remember { mutableIntStateOf(0) }
    var usageSample by remember { mutableStateOf<UsageSample?>(null) }
    var usageError by remember { mutableStateOf<String?>(null) }
    var usageLoading by remember { mutableStateOf(true) }
    var usageAttempt by remember { mutableIntStateOf(0) }
    var notify by remember { mutableStateOf(container.store.notifyOnComplete) }
    var notifyApprovals by remember { mutableStateOf(container.store.notifyOnApproval) }
    var showTools by remember { mutableStateOf(container.store.showToolCalls) }
    var showThinking by remember { mutableStateOf(container.store.showThinking) }
    var defaultModel by remember { mutableStateOf(container.store.defaultModel) }
    var showMicrophone by remember { mutableStateOf(container.store.showMicrophone) }
    var modelItems by remember { mutableStateOf<List<ModelItem>>(emptyList()) }
    var modelMenu by remember { mutableStateOf(false) }
    var themeColor by remember { mutableIntStateOf(container.store.themeColor) }
    var showInboxEnvs by remember { mutableStateOf(container.store.showInboxEnvs) }
    var showInboxRemote by remember { mutableStateOf(container.store.showInboxRemote) }
    var groupByRepo by remember { mutableStateOf(container.chats.groupByRepo) }
    var compactCards by remember { mutableStateOf(container.chats.compactCards) }
    var hideFinishedDays by remember { mutableIntStateOf(container.chats.hideFinishedDays) }
    var hideFinishedMenu by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(container.chats.inboxShowArchived) }
    var showHidden by remember { mutableStateOf(container.chats.inboxShowHidden) }
    var githubLogin by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val lifeState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var unrestrictedBattery by remember { mutableStateOf(BatteryExemption.isExempt(context)) }
    LaunchedEffect(lifeState) {
        unrestrictedBattery = BatteryExemption.isExempt(context)
    }
    val fmt = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    val notifyPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notify = granted
        container.store.notifyOnComplete = granted
        if (granted) RunWatchScheduler.resume(context.applicationContext)
    }
    val installed = remember { AppUpdate.installed(context) }

    fun reloadLocal() {
        notify = container.store.notifyOnComplete
        notifyApprovals = container.store.notifyOnApproval
        showTools = container.store.showToolCalls
        showThinking = container.store.showThinking
        defaultModel = container.store.defaultModel
        showMicrophone = container.store.showMicrophone
        themeColor = container.store.themeColor
        showInboxEnvs = container.store.showInboxEnvs
        showInboxRemote = container.store.showInboxRemote
        groupByRepo = container.chats.groupByRepo
        compactCards = container.chats.compactCards
        hideFinishedDays = container.chats.hideFinishedDays
        showArchived = container.chats.inboxShowArchived
        showHidden = container.chats.inboxShowHidden
        onAppearanceChanged()
    }

    LaunchedEffect(Unit) {
        modelItems = runCatching { container.repo.models() }.getOrDefault(emptyList())
        githubLogin = withContext(Dispatchers.IO) {
            runCatching { GithubRepos.authenticatedLogin(container.store.githubToken) }.getOrNull()
        }
    }
    LaunchedEffect(overviewAttempt) {
        overviewLoading = true
        overviewError = null
        val result = runCatching { container.repo.accountOverview() }
        if (result.isSuccess) {
            overview = result.getOrNull()
            overviewError = null
        } else {
            overviewError = "Couldn't load your stats. Tap to retry."
        }
        overviewLoading = false
    }
    LaunchedEffect(usageAttempt) {
        usageLoading = true
        usageError = null
        val result = runCatching { container.repo.recentUsage() }
        if (result.isSuccess) {
            usageSample = result.getOrNull()
            usageError = null
        } else {
            usageError = "Couldn't load token usage. Tap to retry."
        }
        usageLoading = false
    }
    LaunchedEffect(openAccountTick) {
        if (openAccountTick > 0) page = SettingsPage.Feedback
    }

    BackHandler(enabled = page != SettingsPage.Home || showBack) {
        closePage()
    }

    val shownInfo = info
    if (shownInfo != null) {
        ModalBottomSheet(onDismissRequest = { info = null }) {
            Text(
                shownInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    if (modelMenu) {
        ChoiceDialog(
            title = "Default model",
            selected = defaultModel,
            options = listOf("" to "Account default") + modelItems.map { it.id to (it.displayName ?: it.id) },
            onDismiss = { modelMenu = false },
            onPick = { id ->
                defaultModel = id
                container.store.defaultModel = id
                modelMenu = false
            },
        )
    }
    if (hideFinishedMenu) {
        ChoiceDialog(
            title = "Hide finished older than",
            selected = hideFinishedDays.toString(),
            options = HideFinishedAge.entries.map { it.days.toString() to it.label },
            onDismiss = { hideFinishedMenu = false },
            onPick = { id ->
                val days = id.toIntOrNull() ?: 0
                hideFinishedDays = days
                container.chats.hideFinishedDays = days
                hideFinishedMenu = false
                onAppearanceChanged()
            },
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = AppInsets.bars,
        topBar = {
            TopAppBar(
                title = { Text(page.title) },
                navigationIcon = {
                    if (page != SettingsPage.Home || showBack) {
                        IconButton(onClick = { closePage() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scaffoldBars(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (page) {
                    SettingsPage.Home -> {
                        SettingsPage.entries.filter { it != SettingsPage.Home && it != SettingsPage.Forges }.forEach { item ->
                            SettingsLinkRow(item.title, item.summary) { page = item }
                        }
                    }
                    SettingsPage.Appearance -> {
                        Text(
                            "Theme color",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ThemeColorPresets.forEach { color ->
                                val selected = themeColor == color
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onBackground
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape,
                                        )
                                        .clickable {
                                            themeColor = color
                                            container.store.themeColor = color
                                            onAppearanceChanged()
                                        },
                                )
                            }
                        }
                    }
                    SettingsPage.AgentList -> {
                        SettingsSwitchRow(
                            title = "Group by repo",
                            summary = "Collapsible sections, newest activity first",
                            checked = groupByRepo,
                            onCheckedChange = {
                                groupByRepo = it
                                container.chats.groupByRepo = it
                                onAppearanceChanged()
                            },
                        )
                        SettingsSwitchRow(
                            title = "Compact cards",
                            summary = "Title, status, and time only",
                            checked = compactCards,
                            onCheckedChange = {
                                compactCards = it
                                container.chats.compactCards = it
                                onAppearanceChanged()
                            },
                        )
                        SettingsChoiceRow(
                            title = "Hide finished older than",
                            summary = HideFinishedAge.fromDays(hideFinishedDays).label,
                            onClick = { hideFinishedMenu = true },
                        )
                        SettingsSwitchRow(
                            title = "Show archived",
                            summary = "Keep archived chats in the list",
                            checked = showArchived,
                            onCheckedChange = {
                                showArchived = it
                                container.chats.inboxShowArchived = it
                                onAppearanceChanged()
                            },
                        )
                        SettingsSwitchRow(
                            title = "Show hidden",
                            summary = "Chats you hid from the list",
                            checked = showHidden,
                            onCheckedChange = {
                                showHidden = it
                                container.chats.inboxShowHidden = it
                                onAppearanceChanged()
                            },
                        )
                        SettingsSwitchRow(
                            title = "Pool tab",
                            summary = "Pool chats",
                            checked = showInboxEnvs,
                            info = INBOX_TAB_INFO,
                            onInfo = { info = it },
                            onCheckedChange = {
                                showInboxEnvs = it
                                container.store.showInboxEnvs = it
                                onAppearanceChanged()
                            },
                        )
                        SettingsSwitchRow(
                            title = "Remote tab",
                            summary = "Remote Control machines",
                            checked = showInboxRemote,
                            info = INBOX_TAB_INFO,
                            onInfo = { info = it },
                            onCheckedChange = {
                                showInboxRemote = it
                                container.store.showInboxRemote = it
                                onAppearanceChanged()
                            },
                        )
                    }
                    SettingsPage.Chats -> {
                        SettingsSwitchRow(
                            title = "Show tool calls",
                            summary = "Collapsed in the thread. Off hides them",
                            checked = showTools,
                            onCheckedChange = {
                                showTools = it
                                container.store.showToolCalls = it
                            },
                        )
                        SettingsSwitchRow(
                            title = "Show thinking",
                            summary = "Reasoning stream from the agent",
                            checked = showThinking,
                            onCheckedChange = {
                                showThinking = it
                                container.store.showThinking = it
                            },
                        )
                        SettingsSwitchRow(
                            title = "Show microphone",
                            summary = "Voice input on the compose row",
                            checked = showMicrophone,
                            onCheckedChange = {
                                showMicrophone = it
                                container.store.showMicrophone = it
                            },
                        )
                        val modelLabel = modelItems.firstOrNull { it.id == defaultModel }?.displayName
                            ?: defaultModel.ifBlank { "Account default" }
                        SettingsChoiceRow(
                            title = "Default model",
                            summary = modelLabel,
                            info = "Used for new agents in this app. Does not change PC or web.",
                            onInfo = { info = it },
                            onClick = { modelMenu = true },
                        )
                    }
                    SettingsPage.Notifications -> {
                        SettingsSwitchRow(
                            title = "Notify when a run finishes",
                            summary = "Can lag if the app is in the background",
                            checked = notify,
                            info = ALERT_INFO,
                            onInfo = { info = it },
                            onCheckedChange = { on ->
                                if (on && Build.VERSION.SDK_INT >= 33 && !NotifyPermission.granted(context)) {
                                    notifyPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notify = on
                                    container.store.notifyOnComplete = on
                                    if (on) RunWatchScheduler.resume(context.applicationContext)
                                }
                            },
                        )
                        SettingsSwitchRow(
                            title = "Notify when approval is needed",
                            summary = "Shade alert when a tool is waiting",
                            checked = notifyApprovals,
                            info = ALERT_INFO,
                            onInfo = { info = it },
                            onCheckedChange = { on ->
                                if (on && Build.VERSION.SDK_INT >= 33 && !NotifyPermission.granted(context)) {
                                    notifyPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                notifyApprovals = on
                                container.store.notifyOnApproval = on
                                if (on) RunWatchScheduler.resume(context.applicationContext)
                            },
                        )
                        SettingsSwitchRow(
                            title = "Unrestricted battery",
                            summary = "Lets background checks keep running",
                            checked = unrestrictedBattery,
                            info = BATTERY_INFO,
                            onInfo = { info = it },
                            onCheckedChange = {
                                BatteryExemption.openSettings(context)
                                unrestrictedBattery = BatteryExemption.isExempt(context)
                            },
                        )
                    }
                    SettingsPage.Connections -> {
                        SettingsLinkRow(
                            title = "Open Cursor agent links",
                            summary = "Allow this app under Open by default",
                            info = LINK_INFO,
                            onInfo = { info = it },
                            onClick = { SafeLinks.openSupportedLinks(context) },
                        )
                        SettingsLinkRow(
                            title = "Remote Control",
                            summary = "How to connect a PC",
                            info = REMOTE_INFO,
                            onInfo = { info = it },
                        ) { info = REMOTE_INFO }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("MCP", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Servers attached to new agents",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { info = MCP_INFO }) {
                                    Icon(Icons.Outlined.Info, contentDescription = "About MCP")
                                }
                            }
                            McpListSection(container.store)
                        }
                        val forgeCount = container.store.forges().size
                        SettingsLinkRow(
                            title = "Forges",
                            summary = if (forgeCount == 0) "Add GitHub or another host" else "$forgeCount connected",
                            info = GITHUB_INFO,
                            onInfo = { info = it },
                            onClick = {
                                under = SettingsPage.Connections
                                page = SettingsPage.Forges
                            },
                        )
                    }
                    SettingsPage.Forges -> ForgesPage(container)
                    SettingsPage.RepoDefaults -> RepoDefaultsPage(container)
                    SettingsPage.Backup -> {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Move settings to another phone",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(onClick = { info = BACKUP_INFO }) {
                                    Icon(Icons.Outlined.Info, contentDescription = "About backup")
                                }
                            }
                            SettingsTransfer(container = container, onImported = { reloadLocal() })
                        }
                    }
                    SettingsPage.Feedback -> {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Replies show up here",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(onClick = { info = FeedbackPolicy.ANONYMOUS }) {
                                    Icon(Icons.Outlined.Info, contentDescription = "About feedback")
                                }
                            }
                            FeedbackSection(
                                container = container,
                                operator = FeedbackPolicy.isOperator(githubLogin),
                            )
                        }
                    }
                    SettingsPage.About -> {
                        val me = overview?.me
                        val who = listOfNotNull(
                            me?.userEmail,
                            listOfNotNull(me?.userFirstName, me?.userLastName).joinToString(" ").ifBlank { null },
                        ).joinToString(" · ").ifBlank { me?.apiKeyName ?: "Signed in" }
                        SettingsLinkRow(
                            title = who,
                            summary = "Signed in on this phone",
                            info = KEY_INFO,
                            onInfo = { info = it },
                            onClick = { info = KEY_INFO },
                        )
                        OverviewSection(
                            loading = overviewLoading,
                            error = overviewError,
                            overview = overview,
                            onRetry = { overviewAttempt++ },
                        )
                        val sample = usageSample
                        val used = sample?.usage
                        val usageSummary = when {
                            usageLoading -> "Loading token totals…"
                            usageError != null -> usageError
                            sample == null -> "Couldn't load token usage. Tap to retry."
                            sample.sampledAgents == 0 -> "No recent chats"
                            else -> "${fmt.format(used?.displayTotal() ?: 0)} tokens across ${sample.sampledAgents} recent chats"
                        }
                        val usageDetail = buildString {
                            append(USAGE_INFO)
                            if (usageError != null) {
                                append("\n\n")
                                append(usageError)
                            }
                            if (used != null && (sample?.sampledAgents ?: 0) > 0) {
                                append("\n\n")
                                append("in ${fmt.format(used.inputTokens ?: 0)}")
                                append(" · out ${fmt.format(used.outputTokens ?: 0)}")
                                append(" · cache write ${fmt.format(used.cacheWriteTokens ?: 0)}")
                                append(" · cache read ${fmt.format(used.cacheReadTokens ?: 0)}")
                                sample?.top.orEmpty().forEach { row ->
                                    append("\n")
                                    append(row.name)
                                    append(" · ")
                                    append(fmt.format(row.tokens))
                                }
                            }
                        }
                        SettingsLinkRow(
                            title = "Usage",
                            summary = usageSummary.orEmpty(),
                            info = usageDetail,
                            onInfo = { info = it },
                            onClick = {
                                if (usageError != null || (!usageLoading && sample == null)) {
                                    usageAttempt++
                                } else if (!usageLoading && sample != null) {
                                    info = usageDetail
                                }
                            },
                        )
                        SettingsLinkRow(
                            title = "Open usage dashboard",
                            summary = "Cursor dashboard",
                            onClick = { SafeLinks.open(context, "https://cursor.com/dashboard/usage") },
                        )
                        SettingsStaticRow("Free to use", "No charge for this app")
                        SettingsStaticRow("Made by ForeverLegion", "Unofficial Cursor for Android")
                        SettingsStaticRow("Version", installed.versionName)
                        Text(
                            "Sign out",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    RunWatchScheduler.stop(context.applicationContext)
                                    if (container.store.demoMode) {
                                        container.store.demoMode = false
                                        if (container.store.hasKey()) onSessionChanged() else onSignedOut()
                                    } else {
                                        container.store.clear()
                                        onSignedOut()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(
    loading: Boolean,
    error: String?,
    overview: AccountOverview?,
    onRetry: () -> Unit,
) {
    Text(
        "Overview",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    if (overview == null && !loading) {
        SettingsLinkRow(
            title = "Overview",
            summary = error ?: "Couldn't load your stats. Tap to retry.",
            onClick = onRetry,
        )
        return
    }
    val fmt = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    fun value(text: String) = if (loading && overview == null) "Loading…" else text
    val stats = overview
    SettingsStaticRow("Cloud agents", value(fmt.format(stats?.agentCount ?: 0)))
    SettingsStaticRow("Running", value(fmt.format(stats?.runningCount ?: 0)))
    SettingsStaticRow(
        "Remote machines online",
        value("${fmt.format(stats?.computersOnline ?: 0)} of ${fmt.format(stats?.computerCount ?: 0)}"),
    )
    SettingsStaticRow("Pools", value(fmt.format(stats?.poolCount ?: 0)))
    SettingsStaticRow("Cached repos", value(fmt.format(stats?.repoCount ?: 0)))
    if (error != null && !loading) {
        SettingsLinkRow(
            title = "Refresh stats",
            summary = error,
            onClick = onRetry,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    info: String? = null,
    onInfo: (String) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (info != null) {
            IconButton(onClick = { onInfo(info) }) {
                Icon(Icons.Outlined.Info, contentDescription = "About $title")
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider()
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    info: String? = null,
    onInfo: (String) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (info != null) {
            IconButton(onClick = { onInfo(info) }) {
                Icon(Icons.Outlined.Info, contentDescription = "About $title")
            }
        }
    }
    HorizontalDivider()
}

@Composable
internal fun SettingsLinkRow(
    title: String,
    summary: String,
    info: String? = null,
    onInfo: (String) -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (info != null) {
            IconButton(onClick = { onInfo(info) }) {
                Icon(Icons.Outlined.Info, contentDescription = "About $title")
            }
        } else {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
}

@Composable
internal fun SettingsStaticRow(title: String, summary: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    HorizontalDivider()
}

@Composable
private fun ChoiceDialog(
    title: String,
    selected: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { (id, label) ->
                    Text(
                        if (id == selected) "$label  ✓" else label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(id) }
                            .padding(vertical = 12.dp),
                        color = if (id == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
