package com.cursorandroid.app.ui.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import com.cursorandroid.app.data.repo.GroupMove
import com.cursorandroid.app.data.repo.moveGroupKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.api.AgentSummary

internal class RepoGroupActions(
    val manualOrder: Boolean,
    val onRename: (key: String, name: String?) -> Unit,
    val onFavorite: (key: String) -> Unit,
    val onColor: (key: String, color: Int) -> Unit,
    val onOrder: (keys: List<String>) -> Unit,
    val onResetOrder: () -> Unit,
)

internal fun LazyListScope.cloudAgentBlocks(
    arrangement: CloudArrangement,
    groupActions: RepoGroupActions,
    groupByRepo: Boolean,
    collapsed: Set<String>,
    onToggleGroup: (String) -> Unit,
    revealFinished: Boolean,
    onToggleReveal: () -> Unit,
    agentRow: @Composable (AgentSummary) -> Unit,
) {
    if (arrangement.pinned.isNotEmpty()) {
        item(key = "hdr-pin") { SectionHeading("Pinned") }
        itemsKeyed(arrangement.pinned, "pin") { agentRow(it) }
    }
    if (groupByRepo) {
        val displayKeys = arrangement.groups.map { it.key }
        val favoriteKeys = arrangement.groups.filter { it.favorite }.map { it.key }.toSet()
        arrangement.groups.forEach { group ->
            item(key = "repo-${group.key}") {
                val peers = displayKeys.filter { (it in favoriteKeys) == group.favorite }
                val at = peers.indexOf(group.key)
                RepoGroupHeader(
                    label = group.title,
                    subtitle = group.path.takeIf { group.customName != null && it.isNotBlank() },
                    realName = group.path.ifBlank { group.label },
                    count = group.agents.size,
                    running = group.running,
                    expanded = group.key !in collapsed,
                    favorite = group.favorite,
                    color = group.color,
                    renamed = group.customName != null,
                    canMoveUp = at > 0,
                    canMoveDown = at in 0 until peers.lastIndex,
                    manualOrder = groupActions.manualOrder,
                    onClick = { onToggleGroup(group.key) },
                    onRename = { groupActions.onRename(group.key, it) },
                    onFavorite = { groupActions.onFavorite(group.key) },
                    onColor = { groupActions.onColor(group.key, it) },
                    onMove = { move ->
                        groupActions.onOrder(moveGroupKey(displayKeys, favoriteKeys, group.key, move))
                    },
                    onResetOrder = groupActions.onResetOrder,
                )
            }
            if (group.key !in collapsed) {
                itemsKeyed(group.agents, "g-${group.key}") { agentRow(it) }
            }
        }
    } else {
        if (arrangement.favorites.isNotEmpty()) {
            item(key = "hdr-fav") { SectionHeading("Favorites") }
            itemsKeyed(arrangement.favorites, "fav") { agentRow(it) }
            item(key = "hdr-all") { SectionHeading("All") }
        }
        itemsKeyed(arrangement.rest, "row") { agentRow(it) }
    }
    if (arrangement.hiddenFinished > 0) {
        item(key = "aged") {
            val count = arrangement.hiddenFinished
            val label = if (revealFinished) "Hide $count older" else "Show $count hidden"
            Text(
                label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleReveal)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

private fun LazyListScope.itemsKeyed(
    agents: List<AgentSummary>,
    prefix: String,
    row: @Composable (AgentSummary) -> Unit,
) {
    items(
        count = agents.size,
        key = { index -> "$prefix-${agents[index].id}" },
    ) { index ->
        row(agents[index])
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

internal val GroupHeaderColors = listOf(
    0xFFEF4444.toInt(),
    0xFFFB923C.toInt(),
    0xFFEAB308.toInt(),
    0xFF22C55E.toInt(),
    0xFF14B8A6.toInt(),
    0xFF60A5FA.toInt(),
    0xFFA78BFA.toInt(),
    0xFFF472B6.toInt(),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun RepoGroupHeader(
    label: String,
    subtitle: String?,
    realName: String,
    count: Int,
    running: Int,
    expanded: Boolean,
    favorite: Boolean,
    color: Int,
    renamed: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    manualOrder: Boolean,
    onClick: () -> Unit,
    onRename: (String?) -> Unit,
    onFavorite: () -> Unit,
    onColor: (Int) -> Unit,
    onMove: (GroupMove) -> Unit,
    onResetOrder: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var colorOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val nameColor = if (color != 0) Color(color) else MaterialTheme.colorScheme.onSurface
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
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
                contentDescription = if (expanded) "Collapse $label" else "Expand $label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (favorite) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Favorite group",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (running > 0) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .semantics { contentDescription = "Running" },
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            Text(
                realName,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menu = false
                    renameOpen = true
                },
            )
            if (renamed) {
                DropdownMenuItem(
                    text = { Text("Reset name") },
                    onClick = {
                        menu = false
                        onRename(null)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(if (favorite) "Remove star" else "Favorite") },
                onClick = {
                    menu = false
                    onFavorite()
                },
            )
            DropdownMenuItem(
                text = { Text("Move to top") },
                enabled = canMoveUp,
                onClick = {
                    menu = false
                    onMove(GroupMove.Top)
                },
            )
            DropdownMenuItem(
                text = { Text("Move up") },
                enabled = canMoveUp,
                onClick = {
                    menu = false
                    onMove(GroupMove.Up)
                },
            )
            DropdownMenuItem(
                text = { Text("Move down") },
                enabled = canMoveDown,
                onClick = {
                    menu = false
                    onMove(GroupMove.Down)
                },
            )
            DropdownMenuItem(
                text = { Text("Header color") },
                onClick = {
                    menu = false
                    colorOpen = true
                },
            )
            if (manualOrder) {
                DropdownMenuItem(
                    text = { Text("Reset order to automatic") },
                    onClick = {
                        menu = false
                        onResetOrder()
                    },
                )
            }
        }
    }
    if (renameOpen) {
        var draft by remember { mutableStateOf(if (renamed) label else "") }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename group") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(60) },
                    singleLine = true,
                    label = { Text("Display name") },
                    placeholder = { Text(realName) },
                    supportingText = { Text("Repo: $realName") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameOpen = false
                    onRename(draft.trim().takeIf { it.isNotEmpty() })
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (renamed) {
                        TextButton(onClick = {
                            renameOpen = false
                            onRename(null)
                        }) { Text("Reset") }
                    }
                    TextButton(onClick = { renameOpen = false }) { Text("Cancel") }
                }
            },
        )
    }
    if (colorOpen) {
        AlertDialog(
            onDismissRequest = { colorOpen = false },
            title = { Text("Header color") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GroupHeaderColors.forEach { swatch ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(swatch))
                                    .border(
                                        width = if (swatch == color) 3.dp else 1.dp,
                                        color = if (swatch == color) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        colorOpen = false
                                        onColor(swatch)
                                    },
                            )
                        }
                    }
                    TextButton(onClick = {
                        colorOpen = false
                        onColor(0)
                    }) { Text(if (color == 0) "Default (selected)" else "Default") }
                }
            },
            confirmButton = {
                TextButton(onClick = { colorOpen = false }) { Text("Close") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeArchiveRow(
    enabled: Boolean,
    onArchive: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    var fired by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && !fired) {
                fired = true
                onArchive()
            }
            false
        },
    )
    LaunchedEffect(fired) {
        if (!fired) return@LaunchedEffect
        delay(1_200)
        fired = false
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        "Archive",
                        modifier = Modifier.padding(end = 20.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
        content = { content() },
    )
}
