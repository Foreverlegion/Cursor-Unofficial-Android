package com.cursorandroid.app.ui.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.repo.RepoBehind

@Composable
fun BehindBranchDialog(
    stale: RepoBehind,
    onKeep: () -> Unit,
    onPull: () -> Unit,
) {
    val count = if (stale.behindBy > 0) {
        "${stale.behindBy} commits behind"
    } else {
        "newer commits on the branch"
    }
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Behind ${stale.branch}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("This chat is $count.")
                Text(
                    "Chat: ${stale.chatSha.take(7)} ${stale.chatTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${stale.branch}: ${stale.remoteSha.take(7)} ${stale.remoteTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Pull the newest copy before making changes?")
            }
        },
        confirmButton = {
            TextButton(onClick = onPull) { Text("Pull newest") }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text("Keep this copy") }
        },
    )
}
