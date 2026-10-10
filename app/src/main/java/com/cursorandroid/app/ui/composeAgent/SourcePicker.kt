package com.cursorandroid.app.ui.composeAgent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePicker(
    sources: List<String>,
    selected: String,
    loading: Boolean,
    emptyLabel: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.ifBlank { if (loading) "Loading…" else emptyLabel },
            onValueChange = {},
            readOnly = true,
            label = { Text("Source") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (sources.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No connected sources yet") },
                    onClick = { menu = false },
                    enabled = false,
                )
            }
            sources.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onPick(name)
                        menu = false
                    },
                )
            }
        }
    }
}

@Composable
fun ForgeRepoSearch(
    query: String,
    onQuery: (String) -> Unit,
    loading: Boolean,
    hasMore: Boolean,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search repos") },
            singleLine = true,
            supportingText = { if (loading) Text("Loading repos…") },
        )
        if (hasMore && !loading) {
            OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) {
                Text("Load more repos")
            }
        }
    }
}
