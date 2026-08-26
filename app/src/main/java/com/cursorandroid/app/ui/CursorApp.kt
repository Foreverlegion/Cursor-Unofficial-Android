package com.cursorandroid.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.window.core.layout.WindowSizeClass
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.LaunchRequest
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.repo.AppUpdate
import com.cursorandroid.app.data.repo.Attachments
import com.cursorandroid.app.data.repo.AutoUpdateScheduler
import com.cursorandroid.app.data.repo.ChatDraft
import com.cursorandroid.app.data.repo.DraftStore
import com.cursorandroid.app.data.repo.toDraft
import com.cursorandroid.app.ui.composeAgent.NewAgentScreen
import com.cursorandroid.app.ui.inbox.InboxScreen
import com.cursorandroid.app.ui.settings.AutoUpdatePrompt
import com.cursorandroid.app.ui.settings.SettingsScreen
import com.cursorandroid.app.ui.signIn.SignInScreen
import com.cursorandroid.app.ui.theme.CursorTheme
import com.cursorandroid.app.ui.thread.ThreadScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Pane { Inbox, Compose, Settings }

@Composable
fun CursorApp(
    container: AppContainer,
    launch: LaunchRequest,
) {
    var themeColor by remember { mutableIntStateOf(container.store.themeColor) }
    var showInboxEnvs by remember { mutableStateOf(container.store.showInboxEnvs) }
    var showInboxRemote by remember { mutableStateOf(container.store.showInboxRemote) }
    fun refreshAppearance() {
        themeColor = container.store.themeColor
        showInboxEnvs = container.store.showInboxEnvs
        showInboxRemote = container.store.showInboxRemote
    }

    CursorTheme(accentArgb = themeColor) {
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
    var signedIn by rememberSaveable { mutableStateOf(container.store.hasKey()) }
    var askedAutoUpdate by rememberSaveable { mutableStateOf(container.store.autoUpdateAsked) }
    val context = LocalContext.current
    if (!askedAutoUpdate) {
        AutoUpdatePrompt { enabled ->
            container.store.autoUpdate = enabled
            container.store.autoUpdateAsked = true
            askedAutoUpdate = true
            if (enabled) {
                if (!AppUpdate.canInstall(context)) {
                    AppUpdate.requestInstallPermission(context)
                }
                AutoUpdateScheduler.sync(context.applicationContext, true)
            }
        }
        return
    }
    if (!signedIn) {
        SignInScreen(
            container = container,
            onSignedIn = {
                onAppearanceChanged()
                signedIn = true
            },
        )
        return
    }

    val windowSize = currentWindowAdaptiveInfo().windowSizeClass
    val twoPane = windowSize.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    var pane by rememberSaveable { mutableStateOf(Pane.Inbox) }
    var selectedId by rememberSaveable { mutableStateOf(launch.agentId) }
    var composeEnvType by rememberSaveable { mutableStateOf("cloud") }
    var composeEnvName by rememberSaveable { mutableStateOf<String?>(null) }
    var composeTick by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(signedIn) {
        if (signedIn) RunWatchScheduler.resume(context.applicationContext)
        AutoUpdateScheduler.sync(context.applicationContext, container.store.autoUpdate)
    }

    LaunchedEffect(launch.nonce) {
        if (launch.nonce == 0L) return@LaunchedEffect
        if (launch.agentId != null) {
            selectedId = launch.agentId
            pane = Pane.Inbox
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                InboxScreen(
                    container = container,
                    selectedId = selectedId,
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
                            onAppearanceChanged = onAppearanceChanged,
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
                    onAppearanceChanged = onAppearanceChanged,
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
                )
            }
        }
    }
}

@Composable
private fun EmptyDetail() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Select an agent, or start a new one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
