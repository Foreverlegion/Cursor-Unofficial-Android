package com.cursorandroid.app.ui.composeAgent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.api.MAX_AGENT_REPOS
import com.cursorandroid.app.data.api.RepositoryItem
import com.cursorandroid.app.data.api.gitPath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraReposField(
    repos: List<RepositoryItem>,
    selected: List<String>,
    primaryUrl: String,
    onSelected: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val taken = (selected + primaryUrl).map { it.lowercase() }.toSet()
    val choices = repos.filter { it.url.lowercase() !in taken }
    val room = selected.size + if (primaryUrl.isBlank()) 0 else 1
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Also include",
            style = MaterialTheme.typography.labelLarge,
        )
        if (selected.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { url ->
                    FilterChip(
                        selected = true,
                        onClick = { onSelected(selected.filterNot { it.equals(url, ignoreCase = true) }) },
                        label = { Text(gitPath(url).ifBlank { url }) },
                    )
                }
            }
        }
        ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it && room < MAX_AGENT_REPOS }) {
            OutlinedTextField(
                value = if (room >= MAX_AGENT_REPOS) "20 repo limit" else "Add a repository",
                onValueChange = {},
                readOnly = true,
                enabled = room < MAX_AGENT_REPOS && choices.isNotEmpty(),
                label = { Text("More repos") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                supportingText = {
                    Text("Cloud starts take up to 20 repos. A named cloud environment is separate from this list.")
                },
            )
            ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (choices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No more repos") },
                        onClick = { menu = false },
                        enabled = false,
                    )
                }
                choices.forEach { repo ->
                    DropdownMenuItem(
                        text = { Text(repo.displayName()) },
                        onClick = {
                            onSelected(selected + repo.url)
                            menu = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SaveEnvironmentField(
    save: Boolean,
    onSave: (Boolean) -> Unit,
    name: String,
    onName: (String) -> Unit,
    install: String,
    onInstall: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = save, onCheckedChange = onSave)
            Text("Save as environment")
        }
        if (save) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Environment name") },
            )
            OutlinedTextField(
                value = install,
                onValueChange = onInstall,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Install command") },
                placeholder = { Text("optional, runs during a Build") },
                supportingText = {
                    Text("Creates a personal environment, then starts the agent on that name. Repos stay on the environment.")
                },
            )
        }
    }
}
