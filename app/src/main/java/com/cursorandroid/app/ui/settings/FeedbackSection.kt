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
import com.cursorandroid.app.data.repo.AppUpdate
import com.cursorandroid.app.data.repo.BugReport
import com.cursorandroid.app.data.repo.InstallPulse
import com.cursorandroid.app.data.repo.SafeLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@Composable
fun FeedbackSection(
    container: AppContainer,
    showInstallCounts: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fmt = remember { NumberFormat.getIntegerInstance(Locale.US) }
    var counts by remember { mutableStateOf<InstallPulse.Counts?>(null) }
    var countError by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var reportStatus by remember { mutableStateOf<String?>(null) }
    var reportError by remember { mutableStateOf(false) }

    LaunchedEffect(showInstallCounts) {
        if (!showInstallCounts) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching { InstallPulse.fetchCounts() }
        }
        counts = result.getOrNull()
        countError = result.isFailure
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Feedback", style = MaterialTheme.typography.titleMedium)
        Text(
            "Opens a GitHub issue and assigns it to Foreverlegion.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { reportOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Report Bug")
        }
        if (reportStatus != null) {
            Text(
                reportStatus!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (reportError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }

    if (showInstallCounts) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Installs", style = MaterialTheme.typography.titleMedium)
            Text(
                "Visible only on this Cursor account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when {
                    counts != null ->
                        "${fmt.format(counts!!.current)} currently installed · ${fmt.format(counts!!.total)} total installs"
                    countError -> "Install count unavailable"
                    else -> "Loading install count…"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Total counts each phone once. Updates do not add another install. Current drops off after ${InstallPulse.WINDOW_DAYS} days without a ping.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (reportOpen) {
        ReportBugDialog(
            onDismiss = { reportOpen = false },
            onSubmit = { title, detail ->
                reportOpen = false
                reportError = false
                reportStatus = "Opening GitHub…"
                scope.launch {
                    val here = AppUpdate.installed(context)
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            BugReport.submit(
                                title = title,
                                userText = detail,
                                device = BugReport.Device(
                                    versionName = here.versionName,
                                    versionCode = here.versionCode,
                                    sdk = Build.VERSION.SDK_INT,
                                    release = Build.VERSION.RELEASE.orEmpty(),
                                    manufacturer = Build.MANUFACTURER.orEmpty(),
                                    model = Build.MODEL.orEmpty(),
                                ),
                                userToken = container.store.githubToken,
                            )
                        }
                    }
                    result.onSuccess { url ->
                        val opened = SafeLinks.open(context, url)
                        reportError = !opened
                        reportStatus = if (opened) "Opened GitHub issue" else "Could not open GitHub"
                    }.onFailure { fail ->
                        reportError = true
                        reportStatus = fail.message ?: "Could not open GitHub"
                    }
                }
            },
        )
    }
}

@Composable
private fun ReportBugDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Bug") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
