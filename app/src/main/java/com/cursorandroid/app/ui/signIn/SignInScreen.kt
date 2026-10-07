package com.cursorandroid.app.ui.signIn

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.auth.DemoAccess
import com.cursorandroid.app.ui.screenInsets
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.repo.ClientOrigin
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.ui.settings.SettingsTransfer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    container: AppContainer,
    onSignedIn: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var demoUser by remember { mutableStateOf("") }
    var demoPass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var waitingBrowser by remember { mutableStateOf(false) }
    var browserJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notifyPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifyPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .screenInsets()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Cursor", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Sign in on cursor.com. The first time, this saves one API key named ${ClientOrigin.ID}. Later sign-ins reuse that key and do not create another. Same Cloud Agents access as pasting a key. This app does not run an agent on the phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    val saved = container.store.apiKey?.trim()?.takeIf { it.isNotEmpty() }
                    if (saved != null) {
                        error = null
                        loading = true
                        scope.launch {
                            try {
                                container.repo.me()
                                RunWatchScheduler.ensureSweep(context.applicationContext)
                                onSignedIn()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "Saved API key was rejected. Paste a key."
                            } finally {
                                loading = false
                            }
                        }
                        return@Button
                    }
                    val handshake = container.login.handshake()
                    error = null
                    waitingBrowser = true
                    loading = true
                    if (!SafeLinks.open(context, handshake.loginUrl)) {
                        error = "Could not open Cursor login"
                        waitingBrowser = false
                        loading = false
                        return@Button
                    }
                    browserJob?.cancel()
                    browserJob = scope.launch {
                        try {
                            container.login.complete(handshake, container.store)
                            container.repo.me()
                            RunWatchScheduler.ensureSweep(context.applicationContext)
                            onSignedIn()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Sign-in failed"
                        } finally {
                            waitingBrowser = false
                            loading = false
                            browserJob = null
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (waitingBrowser) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Sign in with Cursor")
                }
            }
            if (waitingBrowser) {
                Text(
                    "Finish login in the browser, then return here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        browserJob?.cancel()
                        browserJob = null
                        waitingBrowser = false
                        loading = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancel") }
            }
            Text("Review / Demo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Play review sign-in. Username demo, password demo. Sample chats only. This does not connect to Cursor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = demoUser,
                onValueChange = { demoUser = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username") },
                singleLine = true,
            )
            OutlinedTextField(
                value = demoPass,
                onValueChange = { demoPass = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = {
                    if (!DemoAccess.accepts(demoUser, demoPass)) {
                        error = "Review sign-in was rejected."
                        return@Button
                    }
                    error = null
                    container.store.demoMode = true
                    onSignedIn()
                },
                enabled = demoUser.isNotBlank() && demoPass.isNotBlank() && !loading && !waitingBrowser,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enter demo")
            }
            Text(
                "Or paste a key from cursor.com/dashboard/api",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        container.store.apiKey = key
                        try {
                            container.repo.me()
                            RunWatchScheduler.ensureSweep(context.applicationContext)
                            onSignedIn()
                        } catch (e: Exception) {
                            container.store.clear()
                            error = e.message ?: "Sign-in failed"
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = key.isNotBlank() && !loading && !waitingBrowser,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Sign in")
                }
            }
            SettingsTransfer(
                container = container,
                allowExport = false,
                onImported = {
                    if (!container.store.hasKey()) return@SettingsTransfer
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            container.repo.me()
                            RunWatchScheduler.ensureSweep(context.applicationContext)
                            onSignedIn()
                        } catch (e: Exception) {
                            container.store.clear()
                            error = e.message ?: "Imported key failed"
                        } finally {
                            loading = false
                        }
                    }
                },
            )
        }
    }
}
