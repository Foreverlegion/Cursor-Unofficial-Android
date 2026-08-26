package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
                            !AppUpdate.canInstall(context) -> {
                                AppUpdate.requestInstallPermission(context)
                                error = true
                                "Allow installs from this app, then try Update again"
                            }
                            else -> {
                                status = "Installing ${remote.versionName}…"
                                val apk = withContext(Dispatchers.IO) {
                                    AppUpdate.download(context, remote.apkUrl, token)
                                }
                                AppUpdate.checkReady(context, apk)?.let { throw IllegalStateException(it) }
                                AppUpdate.install(context, apk)
                                "Installer opened for ${remote.versionName}"
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
