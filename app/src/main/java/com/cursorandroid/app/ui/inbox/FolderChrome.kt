package com.cursorandroid.app.ui.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Work
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.repo.FolderMembership
import com.cursorandroid.app.data.repo.LocalFolder
import com.cursorandroid.app.data.repo.LocalFolderStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.cursorandroid.app.data.repo.FolderIconStore
import com.cursorandroid.app.ui.theme.ColorPickerDialog
import com.cursorandroid.app.ui.theme.LocalHomeLayout

internal class FolderListActions(
    val membership: FolderMembership,
    val onCreate: (String) -> LocalFolder?,
    val onRename: (String, String) -> Boolean,
    val onDelete: (String) -> Unit,
    val onToggleCollapsed: (String) -> Unit,
    val onToggleFavorite: (String) -> Unit,
    val onColor: (String, Int) -> Unit,
    val onIcon: (String, String) -> Unit,
    val onMoveChats: (Set<String>, String?) -> Unit,
    val onMoveGroup: (String, String?) -> Unit,
)

internal sealed class FolderMove {
    data class Chats(val ids: Set<String>) : FolderMove()
    data class Group(val key: String) : FolderMove()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun FolderHeader(
    folder: LocalFolder,
    count: Int,
    iconStore: FolderIconStore?,
    depth: Int = 0,
    parentFolderName: String? = null,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onColor: (Int) -> Unit,
    onIcon: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var colorOpen by remember { mutableStateOf(false) }
    var iconOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val expanded = !folder.collapsed
    val layout = LocalHomeLayout.current
    val nameColor = if (folder.color != 0) Color(folder.color) else MaterialTheme.colorScheme.onSurface
    val titleStyle = MaterialTheme.typography.titleSmall.copy(
        fontSize = MaterialTheme.typography.titleSmall.fontSize * layout.folderTitleScale,
    )
    val countStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = MaterialTheme.typography.labelLarge.fontSize * layout.folderCountScale,
    )
    Box {
        FolderTreeRow(
            depth = depth,
            parentFolderName = parentFolderName,
            rowLabel = folder.name,
            verticalPadding = layout.folderRowPadV,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onToggle,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.folderRowGap),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse ${folder.name}" else "Expand ${folder.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FolderIconGlyph(
                    folder = folder,
                    iconStore = iconStore,
                    tint = if (folder.color != 0) nameColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    size = layout.folderIconSize,
                )
                Text(
                    folder.name,
                    style = titleStyle,
                    color = nameColor,
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
                    style = countStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
                text = { Text("Name color") },
                onClick = {
                    menu = false
                    colorOpen = true
                },
            )
            DropdownMenuItem(
                text = { Text("Change icon") },
                onClick = {
                    menu = false
                    iconOpen = true
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
    if (colorOpen) {
        ColorPickerDialog(
            title = "Name color",
            initialColor = folder.color,
            presetColors = LocalFolderStore.NAME_COLORS,
            allowDefault = true,
            defaultPreviewColor = MaterialTheme.colorScheme.onSurface.toArgb(),
            onConfirm = {
                colorOpen = false
                onColor(it)
            },
            onDismiss = { colorOpen = false },
        )
    }
    if (iconOpen) {
        FolderIconDialog(
            folderId = folder.id,
            selected = folder.icon,
            iconStore = iconStore,
            onPick = {
                iconOpen = false
                onIcon(it)
            },
            onDismiss = { iconOpen = false },
        )
    }
}

@Composable
private fun FolderIconGlyph(
    folder: LocalFolder,
    iconStore: FolderIconStore?,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    val custom = folder.icon.takeIf { iconStore?.isCustom(it) == true }
    if (custom != null && iconStore != null) {
        val bitmap = remember(custom) { iconStore.loadBitmap(custom)?.asImageBitmap() }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
            return
        }
    }
    Icon(
        imageVector = folderIcon(folder.icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size),
    )
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

internal fun folderIcon(id: String): ImageVector =
    folderIconChoices().firstOrNull { it.first == id }?.second ?: Icons.Filled.Folder

internal fun folderIconChoices(): List<Pair<String, ImageVector>> = listOf(
    "" to Icons.Filled.Folder,
    "work" to Icons.Filled.Work,
    "code" to Icons.Filled.Code,
    "bug" to Icons.Filled.BugReport,
    "build" to Icons.Filled.Build,
    "cloud" to Icons.Filled.Cloud,
    "home" to Icons.Filled.Home,
    "science" to Icons.Filled.Science,
    "terminal" to Icons.Filled.Terminal,
    "bookmark" to Icons.Filled.Bookmark,
    "inventory" to Icons.Filled.Inventory2,
    "hub" to Icons.Filled.Hub,
    "memory" to Icons.Filled.Memory,
    "public" to Icons.Filled.Public,
    "lock" to Icons.Filled.Lock,
    "label" to Icons.Filled.Label,
    "tree" to Icons.Filled.AccountTree,
    "storage" to Icons.Filled.Storage,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderIconDialog(
    folderId: String,
    selected: String,
    iconStore: FolderIconStore?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || iconStore == null) return@rememberLauncherForActivityResult
        val ref = iconStore.importForFolder(folderId, uri) ?: return@rememberLauncherForActivityResult
        onPick(ref)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change icon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    folderIconChoices().forEach { (id, icon) ->
                        val picked = id == selected
                        val label = if (id.isEmpty()) "Default folder" else id
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (picked) 2.dp else 1.dp,
                                    color = if (picked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { onPick(id) }
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                if (iconStore != null) {
                    TextButton(
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    ) { Text("Upload image") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick("") }) { Text("Reset to default") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
