package com.cursorandroid.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.repo.ModelPickerStore
import com.cursorandroid.app.ui.theme.CursorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    open: Boolean,
    title: String,
    models: List<ModelItem>,
    selectedModelId: String,
    store: ModelPickerStore,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    if (!open) return
    val recent = remember(models) { store.recentInCatalog(models) }
    val tabs = remember(models, recent) { modelPickerTabs(models, recent) }
    var tabIndex by remember(open, selectedModelId, models) {
        val tabId = store.resolveOpenTab(selectedModelId, models)
        mutableIntStateOf(tabs.indexOfFirst { it.id == tabId }.coerceAtLeast(0))
    }
    var search by remember(open) { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(open, selectedModelId, models) {
        if (!open) return@LaunchedEffect
        val tabId = store.resolveOpenTab(selectedModelId, models)
        tabIndex = tabs.indexOfFirst { it.id == tabId }.coerceAtLeast(0)
        search = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                label = { Text("Search models") },
                singleLine = true,
            )
            val searching = search.isNotBlank()
            if (!searching && tabs.size > 1) {
                ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 16.dp) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = tabIndex == index,
                            onClick = {
                                tabIndex = index
                                store.lastTabId = tab.id
                            },
                            text = {
                                Text(
                                    tab.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
            if (searching) {
                val groups = remember(search, models) { groupedSearchResults(search, models) }
                LazyColumn(modifier = Modifier.padding(bottom = 32.dp)) {
                    groups.forEach { (vendor, items) ->
                        item(key = "header-${vendor.id}") {
                            Text(
                                vendor.label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(items, key = { it.id }) { model ->
                            ModelPickerRowView(
                                label = model.displayName ?: model.id,
                                description = model.description,
                                selected = model.id == selectedModelId,
                                onClick = {
                                    val tabId = modelVendorForId(model.id).id
                                    store.recordModelPick(model.id, tabId)
                                    onPick(model.id)
                                },
                            )
                        }
                    }
                }
            } else {
                val activeTab = tabs.getOrElse(tabIndex) { ModelVendorTab.All }
                val rows = remember(activeTab, models, recent) {
                    modelsForPickerTab(activeTab, models, recent)
                }
                LazyColumn(modifier = Modifier.padding(bottom = 32.dp)) {
                    items(rows, key = {
                        when (it) {
                            ModelPickerRow.AccountDefault -> "account-default"
                            is ModelPickerRow.Model -> it.item.id
                        }
                    }) { row ->
                        when (row) {
                            ModelPickerRow.AccountDefault -> ModelPickerRowView(
                                label = "Account default",
                                description = null,
                                selected = selectedModelId.isBlank(),
                                onClick = {
                                    store.recordModelPick("", activeTab.id)
                                    onPick("")
                                },
                            )
                            is ModelPickerRow.Model -> ModelPickerRowView(
                                label = row.item.displayName ?: row.item.id,
                                description = row.item.description,
                                selected = row.item.id == selectedModelId,
                                onClick = {
                                    store.recordModelPick(row.item.id, activeTab.id)
                                    onPick(row.item.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPickerRowView(
    label: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            if (selected) "$label  ✓" else label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider()
}

@Preview(showBackground = true)
@Composable
private fun ModelPickerTabsPreview() {
    val sample = listOf(
        ModelItem(id = "composer-2.5", displayName = "Composer 2.5"),
        ModelItem(id = "claude-opus-5-5", displayName = "Claude Opus 5.5"),
        ModelItem(id = "gpt-5.6-sol", displayName = "GPT 5.6"),
        ModelItem(id = "grok-4.7", displayName = "Grok 4.7"),
    )
    CursorTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text("Tabs: ${modelPickerTabs(sample, listOf("composer-2.5")).joinToString { it.label }}")
        }
    }
}
