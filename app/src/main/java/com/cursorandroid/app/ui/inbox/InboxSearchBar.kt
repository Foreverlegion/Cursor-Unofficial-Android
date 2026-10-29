package com.cursorandroid.app.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.ui.status.PlayColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InboxSearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    cloud: Boolean,
    cloudFilter: CloudFilter,
    onCloudFilter: (CloudFilter) -> Unit,
    workingOnly: Boolean,
    onWorkingOnly: (Boolean) -> Unit,
    showArchived: Boolean,
    onShowArchived: (Boolean) -> Unit,
    showHidden: Boolean,
    onShowHidden: (Boolean) -> Unit,
    tabs: List<InboxTab>,
    selectedTab: InboxTab,
    onSelectTab: (InboxTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(query) { parseInboxSearchQuery(query) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(expanded) {
        if (expanded) focusRequester.requestFocus()
    }
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                tabs.forEach { item ->
                    val active = item == selectedTab
                    Column(
                        modifier = Modifier
                            .clickable { onSelectTab(item) }
                            .padding(top = 4.dp, bottom = 8.dp),
                    ) {
                        Text(
                            item.title,
                            color = if (active) PlayColors.Teal else PlayColors.Muted,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(if (active) 28.dp else 0.dp)
                                .background(PlayColors.Teal),
                        )
                    }
                }
            }
            Box {
                IconButton(
                    onClick = {
                        if (expanded && query.isBlank()) {
                            onExpandedChange(false)
                            focusManager.clearFocus()
                        } else {
                            onExpandedChange(true)
                        }
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (parsed.hasActiveFilter) {
                            "Search active"
                        } else {
                            "Search chats"
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                }
                if (parsed.hasActiveFilter && !expanded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("Search chats") },
                    placeholder = { Text("Title, repo, or PR URL") },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                )
                if (cloud) {
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CloudFilter.entries.forEach { item ->
                            FilterChip(
                                selected = cloudFilter == item,
                                onClick = { onCloudFilter(item) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = workingOnly,
                            onClick = { onWorkingOnly(!workingOnly) },
                            label = { Text("Working") },
                        )
                        FilterChip(
                            selected = showArchived,
                            onClick = { onShowArchived(!showArchived) },
                            label = { Text("Archived") },
                        )
                        FilterChip(
                            selected = showHidden,
                            onClick = { onShowHidden(!showHidden) },
                            label = { Text("Hidden") },
                        )
                    }
                }
            }
        }
    }
}

fun shouldCollapseInboxSearch(expanded: Boolean, query: String): Boolean =
    expanded && query.isBlank()
