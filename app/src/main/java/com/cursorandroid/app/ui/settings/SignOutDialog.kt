package com.cursorandroid.app.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val SIGN_OUT_TITLE = "Sign out?"
const val SIGN_OUT_CONFIRM = "Sign out"

/** What signing out keeps and clears, in words. Demo mode only leaves the demo. */
fun signOutNote(demo: Boolean): String =
    if (demo) {
        "This leaves demo mode. Nothing else changes."
    } else {
        "Cleared: your Cursor sign-in key and saved forge tokens are removed from this phone. " +
            "Kept: settings, chats and drafts. After signing in again you can restore settings from a backup."
    }

@Composable
fun SignOutDialog(demo: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SIGN_OUT_TITLE) },
        text = { Text(signOutNote(demo), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("sign-out-confirm")) {
                Text(SIGN_OUT_CONFIRM, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("sign-out-cancel")) { Text("Cancel") }
        },
    )
}
