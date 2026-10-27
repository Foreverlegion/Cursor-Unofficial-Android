package com.cursorandroid.app.ui.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.repo.FolderMembership
import com.cursorandroid.app.data.repo.LocalFolder

internal class FolderListActions(
    val membership: FolderMembership,
    val onCreate: (String) -> LocalFolder?,
    val onRename: (String, String) -> Boolean,
    val onDelete: (String) -> Unit,
    val onToggleCollapsed: (String) -> Unit,
    val onToggleFavorite: (String) -> Unit,
    val onMoveChats: (Set<String>, String?) -> Unit,
    val onMoveGroup: (String, String?) -> Unit,
)

internal sealed class FolderMove {
    data class Chats(val ids: Set<String>) : FolderMove()
    data class Group(val key: String) : FolderMove()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderHeader(
    folder: LocalFolder,
    count: Int,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val expanded = !folder.collapsed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onToggle,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse ${folder.name}" else "Expand ${folder.name}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            folder.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (folder.favorite) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Favorite folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menu = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(if (folder.favorite) "Remove star" else "Favorite") },
                onClick = {
                    menu = false
                    onToggleFavorite()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete folder") },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
internal fun MoveToFolderDialog(
    folders: List<LocalFolder>,
    onMove: (String?) -> Unit,
    onCreate: (String) -> LocalFolder?,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onMove(null) }) { Text("No folder") }
                folders.forEach { folder ->
                    TextButton(onClick = { onMove(folder.id) }) { Text(folder.name) }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it.take(60)
                        error = null
                    },
                    singleLine = true,
                    label = { Text("New folder") },
                    supportingText = error?.let { message -> { Text(message) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val created = onCreate(draft)
                    if (created == null) {
                        if (draft.isNotBlank()) error = "That folder already exists."
                        return@TextButton
                    }
                    onMove(created.id)
                },
                enabled = draft.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun RenameFolderDialog(
    folder: LocalFolder,
    onRename: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(folder.name) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename folder") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it.take(60)
                    error = null
                },
                singleLine = true,
                label = { Text("Name") },
                supportingText = error?.let { message -> { Text(message) } },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (!onRename(draft)) {
                    error = "That folder already exists."
                    return@TextButton
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun DeleteFolderDialog(
    name: String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete folder") },
        text = { Text("\"$name\" goes away. Chats and repo groups stay in the list.") },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
