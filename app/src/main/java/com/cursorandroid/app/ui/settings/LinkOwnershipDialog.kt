package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.cursorandroid.app.data.links.LinkOwnershipDialogKind
import com.cursorandroid.app.data.links.LinkOwnershipPolicy

@Composable
fun LinkOwnershipDialog(
    kind: LinkOwnershipDialogKind,
    onTakeMeThere: () -> Unit,
    onRemindMe: () -> Unit,
    onDontShowAgain: () -> Unit,
) {
    if (kind == LinkOwnershipDialogKind.None) return
    val title = when (kind) {
        LinkOwnershipDialogKind.EnableOwnLinks -> LinkOwnershipPolicy.OWN_TITLE
        LinkOwnershipDialogKind.GrokConflict,
        LinkOwnershipDialogKind.GrokStillConflict,
        -> LinkOwnershipPolicy.GROK_TITLE
        LinkOwnershipDialogKind.None -> return
    }
    val body = when (kind) {
        LinkOwnershipDialogKind.GrokConflict -> LinkOwnershipPolicy.GROK_BODY
        LinkOwnershipDialogKind.GrokStillConflict -> LinkOwnershipPolicy.GROK_STILL_BODY
        LinkOwnershipDialogKind.EnableOwnLinks -> LinkOwnershipPolicy.OWN_BODY
        LinkOwnershipDialogKind.None -> return
    }
    AlertDialog(
        onDismissRequest = onRemindMe,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onTakeMeThere) {
                Text(LinkOwnershipPolicy.TAKE_ME_THERE)
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onRemindMe) { Text(LinkOwnershipPolicy.REMIND_ME) }
                TextButton(onClick = onDontShowAgain) { Text(LinkOwnershipPolicy.DONT_SHOW_AGAIN) }
            }
        },
    )
}
