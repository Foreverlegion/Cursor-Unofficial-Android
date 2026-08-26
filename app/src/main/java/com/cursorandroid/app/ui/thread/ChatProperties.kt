package com.cursorandroid.app.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ChatPropertiesDialog(
    title: String,
    status: String?,
    env: String?,
    branch: String?,
    repoUrl: String?,
    prUrl: String?,
    tokens: Long?,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat properties") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PropertyLine("Name", title)
                PropertyLine("Status", status?.takeIf { it.isNotBlank() })
                PropertyLine("Environment", env?.takeIf { it.isNotBlank() })
                PropertyLine("Branch", branch?.takeIf { it.isNotBlank() })
                PropertyLine(
                    label = "Repo",
                    value = repoUrl?.takeIf { it.isNotBlank() },
                    onClick = repoUrl?.let { { onOpenUrl(it) } },
                )
                PropertyLine(
                    label = "Pull request",
                    value = prUrl?.takeIf { it.isNotBlank() },
                    onClick = prUrl?.let { { onOpenUrl(it) } },
                )
                PropertyLine(
                    "Tokens",
                    tokens?.let { NumberFormat.getIntegerInstance(Locale.US).format(it) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun PropertyLine(
    label: String,
    value: String?,
    onClick: (() -> Unit)? = null,
) {
    val shown = value?.takeIf { it.isNotBlank() } ?: "—"
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            shown,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null && value != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = if (onClick != null && value != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        )
    }
}
