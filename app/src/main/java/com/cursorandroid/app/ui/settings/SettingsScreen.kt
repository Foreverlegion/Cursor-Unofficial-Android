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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import com.cursorandroid.app.data.repo.AutoUpdateScheduler
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.ui.theme.ThemeColorPresets
import java.text.NumberFormat
import java.util.Locale

private enum class SettingsTab(val title: String) {
    Profile("Profile"),
    Chats("Chats"),
    Connections("Connections"),
    Account("Account"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    showBack: Boolean,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onAppearanceChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var overview by remember { mutableStateOf<AccountOverview?>(null) }
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
    var autoUpdate by remember { mutableStateOf(container.store.autoUpdate) }
    var tab by remember { mutableStateOf(SettingsTab.Profile) }
    val context = LocalContext.current
    val lifeState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var unrestrictedBattery by remember { mutableStateOf(BatteryExemption.isExempt(context)) }
    LaunchedEffect(lifeState) {
        unrestrictedBattery = BatteryExemption.isExempt(context)
    }
    val fmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val notifyPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notify = granted
        container.store.notifyOnComplete = granted
        if (granted) RunWatchScheduler.resume(context.applicationContext)
    }

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
        autoUpdate = container.store.autoUpdate
        onAppearanceChanged()
    }

    LaunchedEffect(Unit) {
        overview = runCatching { container.repo.accountOverview() }.getOrNull()
        modelItems = runCatching { container.repo.models() }.getOrDefault(emptyList())
    }

    if (showBack) BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(padding),
        ) {
            val tabs = SettingsTab.entries
            val selected = tabs.indexOf(tab).coerceAtLeast(0)
            PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 16.dp) {
                tabs.forEach { item ->
                    Tab(
                        selected = tab == item,
                        onClick = { tab = item },
                        text = { Text(item.title) },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (tab) {
                        SettingsTab.Profile -> ProfileTab(
                            overview = overview,
                            fmt = fmt,
                            themeColor = themeColor,
                            onThemeColor = { color ->
                                themeColor = color
                                container.store.themeColor = color
                                onAppearanceChanged()
                            },
                            showInboxEnvs = showInboxEnvs,
                            onShowInboxEnvs = { on ->
                                showInboxEnvs = on
                                container.store.showInboxEnvs = on
                                onAppearanceChanged()
                            },
                            showInboxRemote = showInboxRemote,
                            onShowInboxRemote = { on ->
                                showInboxRemote = on
                                container.store.showInboxRemote = on
                                onAppearanceChanged()
                            },
                        )
                        SettingsTab.Chats -> ChatsTab(
                            showTools = showTools,
                            onShowTools = {
                                showTools = it
                                container.store.showToolCalls = it
                            },
                            showThinking = showThinking,
                            onShowThinking = {
                                showThinking = it
                                container.store.showThinking = it
                            },
                            notify = notify,
                            onNotify = { on ->
                                if (on && Build.VERSION.SDK_INT >= 33 && !NotifyPermission.granted(context)) {
                                    notifyPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notify = on
                                    container.store.notifyOnComplete = on
                                    if (on) RunWatchScheduler.resume(context.applicationContext)
                                }
                            },
                            notifyApprovals = notifyApprovals,
                            onNotifyApprovals = { on ->
                                if (on && Build.VERSION.SDK_INT >= 33 && !NotifyPermission.granted(context)) {
                                    notifyPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                notifyApprovals = on
                                container.store.notifyOnApproval = on
                                if (on) RunWatchScheduler.resume(context.applicationContext)
                            },
                            unrestrictedBattery = unrestrictedBattery,
                            onUnrestrictedBattery = {
                                BatteryExemption.openSettings(context)
                                unrestrictedBattery = BatteryExemption.isExempt(context)
                            },
                            showMicrophone = showMicrophone,
                            onShowMicrophone = {
                                showMicrophone = it
                                container.store.showMicrophone = it
                            },
                            modelLabel = modelItems.firstOrNull { it.id == defaultModel }?.displayName
                                ?: defaultModel.ifBlank { "Account default" },
                            modelMenu = modelMenu,
                            onModelMenu = { modelMenu = it },
                            modelItems = modelItems,
                            onPickModel = { id ->
                                defaultModel = id
                                container.store.defaultModel = id
                                modelMenu = false
                            },
                        )
                        SettingsTab.Connections -> ConnectionsTab(
                            container = container,
                        )
                        SettingsTab.Account -> AccountTab(
                            container = container,
                            autoUpdate = autoUpdate,
                            onAutoUpdate = { on ->
                                autoUpdate = on
                                container.store.autoUpdate = on
                                container.store.autoUpdateAsked = true
                                if (on) {
                                    AppUpdate.requestInstallPermission(context)
                                }
                                AutoUpdateScheduler.sync(context.applicationContext, on)
                            },
                            onImported = {
                                reloadLocal()
                                AutoUpdateScheduler.sync(
                                    context.applicationContext,
                                    container.store.autoUpdate,
                                )
                            },
                            onSignedOut = {
                                RunWatchScheduler.stop(context.applicationContext)
                                container.store.clear()
                                onSignedOut()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTab(
    overview: AccountOverview?,
    fmt: NumberFormat,
    themeColor: Int,
    onThemeColor: (Int) -> Unit,
    showInboxEnvs: Boolean,
    onShowInboxEnvs: (Boolean) -> Unit,
    showInboxRemote: Boolean,
    onShowInboxRemote: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val me = overview?.me
    Section(
        title = me?.apiKeyName ?: "Signed in",
        detail = "Key stays on this phone across updates, stored encrypted. Uninstall wipes it unless you import an export.",
    ) {
        val who = listOfNotNull(
            me?.userEmail,
            listOfNotNull(me?.userFirstName, me?.userLastName).joinToString(" ").ifBlank { null },
            me?.createdAt,
        ).joinToString(" · ")
        if (who.isNotBlank()) {
            Text(who, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Section(title = "Theme color", detail = "Accent for tabs, switches, and highlights.") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
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
                        .clickable { onThemeColor(color) },
                )
            }
        }
    }
    Section(
        title = "Inbox tabs",
        detail = "Agents always stays. Hide the others if you only use chats.",
    ) {
        PrefSwitch(
            title = "ENVs",
            detail = "Active cloud and pool environments.",
            checked = showInboxEnvs,
            onCheckedChange = onShowInboxEnvs,
        )
        PrefSwitch(
            title = "Remote",
            detail = "Remote Control machines and their chats.",
            checked = showInboxRemote,
            onCheckedChange = onShowInboxRemote,
        )
    }
    Section(title = "Usage") {
        val used = overview?.usage
        if (used == null) {
            Text(
                "Loading token totals from recent chats…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "${fmt.format(used.totalTokens ?: 0)} tokens across ${overview?.sampledAgents ?: 0} recent chats",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "in ${fmt.format(used.inputTokens ?: 0)} · out ${fmt.format(used.outputTokens ?: 0)} · cache write ${fmt.format(used.cacheWriteTokens ?: 0)} · cache read ${fmt.format(used.cacheReadTokens ?: 0)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            overview?.top.orEmpty().forEach { row ->
                Text(
                    "${row.name} · ${fmt.format(row.tokens)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Cloud Agents token usage only. Desktop and team totals live on the Cursor dashboard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { SafeLinks.open(context, "https://cursor.com/dashboard/usage") }) {
            Text("Open usage dashboard")
        }
    }
    Section(title = "Catalog") {
        Text(
            "${overview?.agentCount ?: 0} chats · ${overview?.computersOnline ?: 0}/${overview?.computerCount ?: 0} remote online · ${overview?.repoCount ?: 0} cached repos",
            style = MaterialTheme.typography.bodyMedium,
        )
        val models = overview?.modelNames.orEmpty()
        if (models.isNotEmpty()) {
            Text(
                models.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsTab(
    showTools: Boolean,
    onShowTools: (Boolean) -> Unit,
    showThinking: Boolean,
    onShowThinking: (Boolean) -> Unit,
    notify: Boolean,
    onNotify: (Boolean) -> Unit,
    notifyApprovals: Boolean,
    onNotifyApprovals: (Boolean) -> Unit,
    unrestrictedBattery: Boolean,
    onUnrestrictedBattery: (Boolean) -> Unit,
    showMicrophone: Boolean,
    onShowMicrophone: (Boolean) -> Unit,
    modelLabel: String,
    modelMenu: Boolean,
    onModelMenu: (Boolean) -> Unit,
    modelItems: List<ModelItem>,
    onPickModel: (String) -> Unit,
) {
    Section(title = "Thread", detail = "What this phone shows in a chat.") {
        PrefSwitch(
            title = "Show tool calls",
            detail = "Collapsed in the thread. Off hides them.",
            checked = showTools,
            onCheckedChange = onShowTools,
        )
        PrefSwitch(
            title = "Show thinking",
            detail = "Reasoning stream from the agent.",
            checked = showThinking,
            onCheckedChange = onShowThinking,
        )
        PrefSwitch(
            title = "Show microphone",
            detail = "Voice input on the compose row.",
            checked = showMicrophone,
            onCheckedChange = onShowMicrophone,
        )
    }
    Section(title = "Alerts", detail = "Local only. Cursor has no mobile push.") {
        PrefSwitch(
            title = "Notify when a run finishes",
            detail = "Can lag if the app is in the background.",
            checked = notify,
            onCheckedChange = onNotify,
        )
        PrefSwitch(
            title = "Notify when approval is needed",
            detail = "Shade alert when a tool is waiting. Approve it on your PC.",
            checked = notifyApprovals,
            onCheckedChange = onNotifyApprovals,
        )
        PrefSwitch(
            title = "Unrestricted battery",
            detail = "Opens Android power management so notifications are not blocked.",
            checked = unrestrictedBattery,
            onCheckedChange = onUnrestrictedBattery,
        )
    }
    Section(title = "New chats") {
        ExposedDropdownMenuBox(expanded = modelMenu, onExpandedChange = onModelMenu) {
            OutlinedTextField(
                value = modelLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default model") },
                supportingText = {
                    Text("Used for new agents in this app. Does not change PC or web.")
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenu) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = modelMenu, onDismissRequest = { onModelMenu(false) }) {
                DropdownMenuItem(
                    text = { Text("Account default") },
                    onClick = { onPickModel("") },
                )
                modelItems.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName ?: model.id) },
                        onClick = { onPickModel(model.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionsTab(
    container: AppContainer,
) {
    Section(
        title = "MCP",
        detail = "Saved on this phone. Enabled servers are attached to new agents and follow-ups. The agent calls their tools.",
    ) {
        McpListSection(container.store)
    }
    Section(
        title = "GitHub",
        detail = "Needed to create a GitHub repo from New agent, or to check updates against a private repo.",
    ) {
        GithubTokenField(container)
    }
    Section(
        title = "Remote Control",
        detail = "On the PC: Cursor 3.9.8+, Agents Window, Settings > Agents > Remote Control, then /remote-control. Local remotes show under Remote, not ENVs. To start new work on a named machine, use New agent > Machine.",
    ) {}
}

@Composable
private fun AccountTab(
    container: AppContainer,
    autoUpdate: Boolean,
    onAutoUpdate: (Boolean) -> Unit,
    onImported: () -> Unit,
    onSignedOut: () -> Unit,
) {
    Section(
        title = "Updates",
        detail = "When on, the app checks GitHub and installs newer releases.",
    ) {
        PrefSwitch(
            title = "Auto update",
            detail = "Stay on the latest published APK.",
            checked = autoUpdate,
            onCheckedChange = onAutoUpdate,
        )
    }
    Section(
        title = "Backup",
        detail = "Move settings to another phone or keep a copy before uninstalling.",
    ) {
        SettingsTransfer(container = container, onImported = onImported)
    }
    Section(title = "Session") {
        Button(onClick = onSignedOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Free to use",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Made by: ForeverLegion",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppUpdateSection(container, showToken = false)
    }
}

@Composable
private fun Section(
    title: String,
    detail: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (!detail.isNullOrBlank()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun PrefSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
