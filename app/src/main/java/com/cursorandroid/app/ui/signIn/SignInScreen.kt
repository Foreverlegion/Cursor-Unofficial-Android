package com.cursorandroid.app.ui.signIn

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import com.cursorandroid.app.data.notify.RunWatchScheduler
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.ui.settings.SettingsTransfer
import kotlinx.coroutines.launch

private const val API_KEYS_URL = "https://cursor.com/dashboard/api"

@Composable
fun SignInScreen(
    container: AppContainer,
    onSignedIn: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
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
            .imePadding()
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
                "Paste a Cursor user API key from cursor.com/dashboard/api or Cursor Settings > API Keys. The key stays on this phone. This app does not create keys, and it does not run an agent on the phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    if (!SafeLinks.open(context, API_KEYS_URL)) {
                        error = "Could not open the API keys page"
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open API keys")
            }
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
                enabled = key.isNotBlank() && !loading,
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
