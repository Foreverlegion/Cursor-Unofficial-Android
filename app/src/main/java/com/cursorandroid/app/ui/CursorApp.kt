package com.cursorandroid.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.LaunchRequest
import com.cursorandroid.app.data.notify.BatteryExemption
import com.cursorandroid.app.data.notify.BatteryPromptPolicy
import com.cursorandroid.app.data.notify.FeedbackReplyScheduler
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.repo.Attachments
import com.cursorandroid.app.data.repo.ChatDraft
import com.cursorandroid.app.data.repo.DraftStore
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.data.repo.toDraft
import com.cursorandroid.app.ui.composeAgent.NewAgentScreen
import com.cursorandroid.app.ui.inbox.InboxScreen
import com.cursorandroid.app.ui.settings.BatteryPrompt
import com.cursorandroid.app.ui.settings.FeedbackNoticePrompt
import com.cursorandroid.app.ui.settings.SettingsScreen
import com.cursorandroid.app.ui.signIn.SignInScreen
import com.cursorandroid.app.ui.theme.CursorTheme
import com.cursorandroid.app.ui.theme.Appearance
import com.cursorandroid.app.ui.theme.ChatDensity
import com.cursorandroid.app.ui.theme.CodeFont
import com.cursorandroid.app.ui.theme.UiFont
import com.cursorandroid.app.ui.theme.clampTextScale
import com.cursorandroid.app.ui.thread.ThreadScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Pane { Inbox, Compose, Settings }

private fun readAppearance(container: AppContainer) = Appearance(
    uiFont = UiFont.fromId(container.store.uiFont),
    codeFont = CodeFont.fromId(container.store.codeFont),
    textScalePct = clampTextScale(container.store.textScalePct),
    density = ChatDensity.fromId(container.store.chatDensity),
)

@Composable
fun CursorApp(
    container: AppContainer,
    launch: LaunchRequest,
) {
    var themeColor by remember { mutableIntStateOf(container.store.themeColor) }
    var appearance by remember { mutableStateOf(readAppearance(container)) }
    var showInboxEnvs by remember { mutableStateOf(container.store.showInboxEnvs) }
    var showInboxRemote by remember { mutableStateOf(container.store.showInboxRemote) }
    fun refreshAppearance() {
        themeColor = container.store.themeColor
        appearance = readAppearance(container)
        showInboxEnvs = container.store.showInboxEnvs
        showInboxRemote = container.store.showInboxRemote
    }

    CursorTheme(accentArgb = themeColor, appearance = appearance) {
        CursorAppContent(
            container = container,
            launch = launch,
            showInboxEnvs = showInboxEnvs,
            showInboxRemote = showInboxRemote,
            onAppearanceChanged = { refreshAppearance() },
        )
    }
}

@Composable
private fun CursorAppContent(
    container: AppContainer,
    launch: LaunchRequest,
    showInboxEnvs: Boolean,
    showInboxRemote: Boolean,
    onAppearanceChanged: () -> Unit,
) {
    var settingsEpoch by remember { mutableIntStateOf(0) }
    val refreshSettings = {
        onAppearanceChanged()
        settingsEpoch += 1
    }
    var signedIn by rememberSaveable { mutableStateOf(container.store.hasSession()) }
    var demo by rememberSaveable { mutableStateOf(container.store.demoMode) }
    var askedFeedback by rememberSaveable { mutableStateOf(container.store.feedbackNoticeSeen) }
    val context = LocalContext.current
    var batteryExempt by remember { mutableStateOf(BatteryExemption.isExempt(context)) }
    var batteryAsked by remember { mutableStateOf(container.store.batteryAsked) }
    var batteryKnownExempt by remember { mutableStateOf(container.store.batteryKnownExempt) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryExempt = BatteryExemption.isExempt(context)
    }
    val batteryDecision = BatteryPromptPolicy.decide(batteryExempt, batteryAsked, batteryKnownExempt)
    LaunchedEffect(batteryDecision) {
        if (batteryAsked == batteryDecision.asked && batteryKnownExempt == batteryDecision.knownExempt) return@LaunchedEffect
        batteryAsked = batteryDecision.asked
        batteryKnownExempt = batteryDecision.knownExempt
        container.store.batteryAsked = batteryDecision.asked
        container.store.batteryKnownExempt = batteryDecision.knownExempt
    }
    val windowSize = currentWindowAdaptiveInfo().windowSizeClass
    val twoPane = windowSize.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    var pane by rememberSaveable { mutableStateOf(Pane.Inbox) }
    var selectedId by rememberSaveable { mutableStateOf(launch.agentId) }
    var linkNote by rememberSaveable { mutableStateOf<String?>(null) }
    var composeEnvType by rememberSaveable { mutableStateOf("cloud") }
    var composeEnvName by rememberSaveable { mutableStateOf<String?>(null) }
    var composeTick by rememberSaveable { mutableStateOf(0) }
    var accountTick by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(signedIn, demo) {
        if (signedIn && !demo) RunWatchScheduler.resume(context.applicationContext)
        FeedbackReplyScheduler.sync(context.applicationContext)
    }
    LaunchedEffect(launch.nonce) {
        if (launch.nonce == 0L) return@LaunchedEffect
        if (launch.openSettings) {
            pane = Pane.Settings
            accountTick += 1
        }
        if (launch.agentId != null) {
            selectedId = launch.agentId
            pane = Pane.Inbox
            linkNote = null
        } else if (launch.invalidAgentLink) {
            selectedId = null
            pane = Pane.Inbox
            linkNote = LaunchRequest.INVALID_AGENT_LINK
        }
        val sharedPr = SafeLinks.pullRequestUrl(launch.shareText)
        if (sharedPr != null && launch.shareUris.isEmpty() && signedIn) {
            val found = runCatching { container.repo.agentForPr(sharedPr) }.getOrNull()
            if (found != null) {
                selectedId = found.id
                pane = Pane.Inbox
                linkNote = null
                return@LaunchedEffect
            }
        }
        val hasShare = !launch.shareText.isNullOrBlank() || launch.shareUris.isNotEmpty()
        if (hasShare) {
            val items = withContext(Dispatchers.IO) {
                Attachments.read(context.applicationContext, launch.shareUris)
            }
            val target = launch.agentId ?: selectedId
            val draftId = if (launch.compose || target == null) DraftStore.NEW_AGENT else target
            val current = container.drafts.load(draftId)
            container.drafts.save(
                draftId,
                current.copy(
                    text = listOfNotNull(current.text.takeIf { it.isNotBlank() }, launch.shareText)
                        .joinToString("\n"),
                    attaches = current.attaches + items.toDraft(),
                ),
            )
            if (launch.compose || target == null) {
                pane = Pane.Compose
                composeTick += 1
            }
        } else if (launch.compose) {
            pane = Pane.Compose
            composeTick += 1
        }
    }
    if (!signedIn) {
        SignInScreen(
            container = container,
            onSignedIn = {
                onAppearanceChanged()
                demo = container.store.demoMode
                signedIn = true
            },
        )
        return
    }
    if (batteryDecision.show) {
        BatteryPrompt(
            onAllow = {
                batteryAsked = true
                container.store.batteryAsked = true
                BatteryExemption.requestExempt(context)
            },
            onSkip = {
                batteryAsked = true
                container.store.batteryAsked = true
            },
        )
        return
    }
    if (!askedFeedback) {
        FeedbackNoticePrompt {
            container.store.feedbackNoticeSeen = true
            askedFeedback = true
        }
        return
    }
    val topBars = WindowInsets.statusBars.union(
        WindowInsets.displayCutout.only(WindowInsetsSides.Top),
    )
    Column(Modifier.fillMaxSize()) {
        if (demo) {
            DemoBanner(Modifier.windowInsetsPadding(topBars))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (demo) Modifier.consumeWindowInsets(topBars) else Modifier),
        ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                InboxScreen(
                    container = container,
                    selectedId = selectedId,
                    linkNote = linkNote,
                    onDismissLinkNote = { linkNote = null },
                    onSelect = {
                        selectedId = it
                        pane = Pane.Inbox
                    },
                    onCompose = { type, name ->
                        composeEnvType = type
                        composeEnvName = name
                        composeTick += 1
                        pane = Pane.Compose
                    },
                    onSettings = { pane = Pane.Settings },
                    showEnvs = showInboxEnvs,
                    showRemote = showInboxRemote,
                    settingsEpoch = settingsEpoch,
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight(),
                )
                Box(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight(),
                ) {
                    when {
                        pane == Pane.Settings -> SettingsScreen(
                            container = container,
                            showBack = false,
                            onBack = { pane = Pane.Inbox },
                            onSignedOut = { signedIn = false },
                            onSessionChanged = { demo = container.store.demoMode },
                            onAppearanceChanged = refreshSettings,
                            openAccountTick = accountTick,
                            modifier = Modifier.fillMaxSize(),
                        )
                        pane == Pane.Compose -> NewAgentScreen(
                            container = container,
                            showBack = false,
                            onBack = { pane = Pane.Inbox },
                            onCreated = { id ->
                                selectedId = id
                                pane = Pane.Inbox
                            },
                            modifier = Modifier.fillMaxSize(),
                            initialEnvType = composeEnvType,
                            initialEnvName = composeEnvName,
                            resetTick = composeTick,
                        )
                        selectedId != null -> ThreadScreen(
                            container = container,
                            agentId = selectedId!!,
                            showBack = false,
                            onBack = { selectedId = null },
                            onRemoved = { selectedId = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> EmptyDetail()
                    }
                }
            }
        } else {
            when {
                pane == Pane.Settings -> SettingsScreen(
                    container = container,
                    showBack = true,
                    onBack = { pane = Pane.Inbox },
                    onSignedOut = { signedIn = false },
                    onSessionChanged = { demo = container.store.demoMode },
                    onAppearanceChanged = refreshSettings,
                    openAccountTick = accountTick,
                )
                pane == Pane.Compose -> NewAgentScreen(
                    container = container,
                    showBack = true,
                    onBack = { pane = Pane.Inbox },
                    onCreated = { id ->
                        selectedId = id
                        pane = Pane.Inbox
                    },
                    initialEnvType = composeEnvType,
                    initialEnvName = composeEnvName,
                    resetTick = composeTick,
                )
                selectedId != null -> ThreadScreen(
                    container = container,
                    agentId = selectedId!!,
                    showBack = true,
                    onBack = { selectedId = null },
                    onRemoved = { selectedId = null },
                )
                else -> InboxScreen(
                    container = container,
                    selectedId = selectedId,
                    linkNote = linkNote,
                    onDismissLinkNote = { linkNote = null },
                    onSelect = { selectedId = it },
                    onCompose = { type, name ->
                        composeEnvType = type
                        composeEnvName = name
                        composeTick += 1
                        pane = Pane.Compose
                    },
                    onSettings = { pane = Pane.Settings },
                    showEnvs = showInboxEnvs,
                    showRemote = showInboxRemote,
                    settingsEpoch = settingsEpoch,
                )
            }
        }
    }
        }
    }
}

@Composable
private fun DemoBanner(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "Demo — not connected to Cursor",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyDetail() {
    Box(Modifier.fillMaxSize().screenInsets(), contentAlignment = Alignment.Center) {
        Text(
            "Select an agent, or start a new one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
