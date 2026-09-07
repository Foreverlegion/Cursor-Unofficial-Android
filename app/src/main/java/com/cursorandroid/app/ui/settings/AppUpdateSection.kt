package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.repo.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppUpdateSection(
    container: AppContainer,
    modifier: Modifier = Modifier,
    showToken: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed = remember { AppUpdate.installed(context) }
    var latest by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<AppUpdate.Remote?>(null) }
    var offerBusy by remember { mutableStateOf(false) }
    var offerError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Installed ${installed.versionName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (latest != null) {
            Text(
                "Latest $latest",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = {
                scope.launch {
                    error = false
                    status = "Checking GitHub…"
                    runCatching {
                        val token = container.store.githubToken
                        val remote = withContext(Dispatchers.IO) { AppUpdate.findRemote(token) }
                        val here = AppUpdate.installed(context)
                        latest = remote.versionName
                        when {
                            remote.versionCode < here.versionCode ->
                                "Phone is newer than GitHub (${remote.versionName})"
                            remote.versionCode == here.versionCode ->
                                "Already on ${here.versionName}"
                            remote.apkUrl == null -> {
                                error = true
                                "Latest is ${remote.versionName}. No release APK yet."
                            }
                            else -> {
                                pending = remote
                                offerError = null
                                "Release notes for ${remote.versionName}"
                            }
                        }
                    }.onSuccess { message ->
                        status = message
                    }.onFailure { fail ->
                        error = true
                        status = fail.message ?: "Update failed"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Update") }
        if (showToken) {
            GithubTokenField(container)
        }
        Text(
            "Update checks GitHub for the newest published version.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status != null) {
            Text(
                status!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val remote = pending
    if (remote != null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                ReleaseNotesPrompt(
                    remote = remote,
                    busy = offerBusy,
                    error = offerError,
                    onSkip = {
                        container.store.skippedUpdateCode = remote.versionCode
                        pending = null
                        offerError = null
                        status = "Skipped ${remote.versionName}"
                    },
                    onUpdate = {
                        scope.launch {
                            offerBusy = true
                            offerError = null
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    AppUpdate.installRemote(context, remote, container.store.githubToken)
                                }
                                status = "Installer opened for ${remote.versionName}"
                            }.onFailure { fail ->
                                offerError = fail.message ?: "Update failed"
                                error = true
                                status = offerError
                            }
                            offerBusy = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun GithubTokenField(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    var githubToken by remember { mutableStateOf(container.store.githubToken.orEmpty()) }
    OutlinedTextField(
        value = githubToken,
        onValueChange = {
            githubToken = it
            container.store.githubToken = it
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text("GitHub token") },
        placeholder = { Text("Needed to create repos or read a private GitHub repo") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
}
