package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.cursorandroid.app.data.api.CreateAgentResponse
import com.cursorandroid.app.data.repo.ProfileRule
import kotlinx.coroutines.launch

@Composable
fun ProfileRulePrompt(
    create: suspend () -> CreateAgentResponse,
    onOpen: (CreateAgentResponse) -> Unit,
    onNotNow: () -> Unit,
    onLater: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onLater() },
        title = { Text(ProfileRule.TITLE) },
        text = {
            Column {
                Text(
                    "This starts one cloud agent with no repository and no environment. It writes an Always Apply user rule so later agents know this phone app.",
                )
                if (error != null) {
                    Text(error!!)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (busy) return@TextButton
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            onOpen(create())
                        } catch (e: Exception) {
                            error = e.message ?: "Could not start the agent"
                            busy = false
                        }
                    }
                },
                enabled = !busy,
            ) { Text(if (busy) "Starting…" else "Build") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onNotNow, enabled = !busy) { Text("Not now") }
                TextButton(onClick = onLater, enabled = !busy) { Text("Remind later") }
            }
        },
    )
}
