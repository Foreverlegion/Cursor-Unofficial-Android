package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.cursorandroid.app.data.repo.UserRuleSync

@Composable
fun UserRuleDialog(
    url: String,
    onUrl: (String) -> Unit,
    error: String?,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit,
) {
    val clone = UserRuleSync.originCloneUrl(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("User rule") },
        text = {
            Column {
                Text(
                    "This phone cannot write Customize → Rules. Paste the clone URL of a new Origin repo. " +
                        "Start launches one agent on that repo and asks it to update the Cursor Unofficial Android user rule in place.",
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user-rule-origin-url"),
                    label = { Text("Origin clone URL") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(error ?: "https://origin.cursor.com/git/owner/repo.git")
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { clone?.let(onStart) },
                enabled = clone != null,
            ) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
