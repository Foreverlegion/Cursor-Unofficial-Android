package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.cursorandroid.app.data.repo.ProfileRule

@Composable
fun ProfileRulePrompt(
    onBuild: () -> Unit,
    onNotNow: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(ProfileRule.TITLE) },
        text = {
            Text(
                "This starts one cloud agent with no repository and no environment. It writes an Always Apply user rule so later agents know this phone app.",
            )
        },
        confirmButton = {
            TextButton(onClick = onBuild) { Text("Build") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onNotNow) { Text("Not now") }
                TextButton(onClick = onLater) { Text("Remind later") }
            }
        },
    )
}
