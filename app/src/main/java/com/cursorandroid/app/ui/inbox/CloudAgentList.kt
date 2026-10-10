package com.cursorandroid.app.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

internal fun LazyListScope.cloudAgentBlocks(
    arrangement: CloudArrangement,
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
        arrangement.groups.forEach { group ->
            item(key = "repo-${group.key}") {
                RepoGroupHeader(
                    label = group.label,
                    count = group.agents.size,
                    running = group.running,
                    expanded = group.key !in collapsed,
                    onClick = { onToggleGroup(group.key) },
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

@Composable
internal fun RepoGroupHeader(
    label: String,
    count: Int,
    running: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse $label" else "Expand $label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
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
