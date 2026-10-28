package com.cursorandroid.app.ui.inbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.ui.status.PlayColors

@Composable
internal fun InboxTabStripWithSearch(
    tabs: List<InboxTab>,
    selected: InboxTab,
    onSelect: (InboxTab) -> Unit,
    searchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    searchActive: Boolean,
) {
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
                val active = item == selected
                Column(
                    modifier = Modifier
                        .clickable { onSelect(item) }
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
            IconButton(onClick = onToggleSearch) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = if (searchExpanded) "Hide search" else "Search chats",
                    tint = if (searchExpanded) PlayColors.Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (searchActive && !searchExpanded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PlayColors.Teal),
                )
            }
        }
    }
}

@Composable
internal fun InboxSearchField(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    requestFocus: Boolean,
    onFocusConsumed: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .focusRequester(focusRequester),
            label = { Text("Search chats") },
            placeholder = { Text("Title, repo, or PR URL") },
            singleLine = true,
        )
    }
    LaunchedEffect(expanded, requestFocus) {
        if (expanded && requestFocus) {
            focusRequester.requestFocus()
            onFocusConsumed()
        }
    }
}
