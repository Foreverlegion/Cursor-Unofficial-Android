package com.cursorandroid.app.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.notify.Notice
import com.cursorandroid.app.data.notify.badgeText

@Composable
internal fun NoticeBell(
    unread: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (unread > 0) "Notifications, $unread unread" else "Notifications"
    IconButton(
        onClick = onClick,
        modifier = modifier
            .testTag("notice-bell")
            .semantics { contentDescription = label },
    ) {
        BadgedBox(
            badge = {
                if (unread > 0) {
                    Badge(modifier = Modifier.testTag("notice-badge")) { Text(badgeText(unread)) }
                }
            },
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = null)
        }
    }
}

@Composable
internal fun NoticePopup(
    notices: List<Notice>,
    onOpen: (Notice) -> Unit,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("notice-popup"),
        title = { Text("Notifications") },
        text = {
            if (notices.isEmpty()) {
                Text(
                    "No notifications",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(notices, key = { it.id }) { notice ->
                        NoticeRow(notice, onOpen = { onOpen(notice) }, onDismiss = { onDismiss(notice.id) })
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onClearAll, enabled = notices.isNotEmpty()) { Text("Clear all") }
        },
    )
}

@Composable
private fun NoticeRow(
    notice: Notice,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp)
            .testTag("notice-${notice.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(notice.title, style = MaterialTheme.typography.titleSmall)
            Text(
                notice.body,
                style = MaterialTheme.typography.bodySmall,
                color = when (notice.kind) {
                    "working", "approval" -> MaterialTheme.colorScheme.primary
                    "error" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
        }
    }
}
