package com.cursorandroid.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.notify.FeedbackReplyScheduler
import com.cursorandroid.app.data.repo.AppUpdate
import com.cursorandroid.app.data.repo.BugReport
import com.cursorandroid.app.data.repo.FeedbackPolicy
import com.cursorandroid.app.data.repo.FeedbackSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FeedbackSection(
    container: AppContainer,
    operator: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reportOpen by remember { mutableStateOf(false) }
    var reportStatus by remember { mutableStateOf<String?>(null) }
    var reportError by remember { mutableStateOf(false) }
    var banned by remember { mutableStateOf(container.feedback.banned()) }
    var threads by remember { mutableStateOf<List<FeedbackSync.Thread>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<FeedbackSync.Incoming>>(emptyList()) }
    var banTarget by remember { mutableStateOf<FeedbackSync.Incoming?>(null) }
    var banError by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val inbox = withContext(Dispatchers.IO) { FeedbackSync.load(container.feedback) }
        banned = inbox.banned
        threads = inbox.threads
        if (operator) {
            incoming = withContext(Dispatchers.IO) { FeedbackSync.incoming() }
        }
    }

    LaunchedEffect(operator) {
        reload()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Feedback", style = MaterialTheme.typography.titleMedium)
        Text(
            FeedbackPolicy.ANONYMOUS + " Replies show up here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { if (!banned) reportOpen = true },
            enabled = !banned,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(FeedbackPolicy.BUTTON)
        }
        if (banned) {
            Text(
                FeedbackPolicy.BLOCKED,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val status = reportStatus
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (reportError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (threads.isNotEmpty()) {
            Text("Your reports", style = MaterialTheme.typography.titleSmall)
            threads.forEach { thread ->
                Text(thread.title, style = MaterialTheme.typography.bodyMedium)
                if (!thread.reachable) {
                    Text(
                        "Replies will show when this report can be reached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (thread.replies.isEmpty()) {
                    Text(
                        "No replies yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    thread.replies.forEach { reply ->
                        Text(reply, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (operator) {
            Text("Incoming reports", style = MaterialTheme.typography.titleSmall)
            Text(
                "Ban stops that install from sending another report. The report does not name who sent it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val banMessage = banError
            if (banMessage != null) {
                Text(
                    banMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (incoming.isEmpty()) {
                Text(
                    "No incoming reports.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            incoming.forEach { item ->
                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                if (item.body.isNotBlank()) {
                    Text(
                        item.body.take(400),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.banned) {
                    Text(
                        "Banned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    TextButton(onClick = { banTarget = item }) {
                        Text("Ban")
                    }
                }
            }
        }
    }

    if (reportOpen && !banned) {
        ReportDialog(
            onDismiss = { reportOpen = false },
            onSubmit = { title, detail ->
                reportOpen = false
                reportError = false
                reportStatus = "Sending…"
                scope.launch {
                    val here = AppUpdate.installed(context)
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            BugReport.submit(
                                title = title,
                                userText = detail,
                                versionName = here.versionName,
                                versionCode = here.versionCode,
                                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                                sdk = Build.VERSION.SDK_INT,
                                ledger = container.feedback,
                            )
                        }
                    }
                    result.onSuccess {
                        reportError = false
                        reportStatus = "Sent. Replies show up here."
                        FeedbackReplyScheduler.sync(context.applicationContext)
                    }.onFailure { fail ->
                        reportError = true
                        reportStatus = fail.message ?: "Could not send the report."
                    }
                    reload()
                }
            },
        )
    }

    val pendingBan = banTarget
    if (pendingBan != null) {
        AlertDialog(
            onDismissRequest = { banTarget = null },
            title = { Text("Ban this install?") },
            text = {
                Text("They will not be able to send new reports from the install that filed \"${pendingBan.title}\".")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val number = pendingBan.number
                        banTarget = null
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { FeedbackSync.ban(number) }
                            if (!ok) {
                                banError = "Could not ban that report."
                            } else {
                                banError = null
                            }
                            reload()
                        }
                    },
                ) { Text("Ban") }
            },
            dismissButton = {
                TextButton(onClick = { banTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(FeedbackPolicy.BUTTON) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    FeedbackPolicy.ANONYMOUS,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(BugReport.MAX_TITLE) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Title") },
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it.take(BugReport.MAX_BODY) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text("What happened") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(title, detail) },
                enabled = title.isNotBlank() || detail.isNotBlank(),
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
