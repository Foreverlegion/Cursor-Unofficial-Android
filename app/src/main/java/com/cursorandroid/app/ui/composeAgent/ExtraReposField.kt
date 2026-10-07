package com.cursorandroid.app.ui.composeAgent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.api.EnvironmentChoice
import com.cursorandroid.app.data.api.MAX_AGENT_REPOS
import com.cursorandroid.app.data.api.MAX_ENV_REPOS
import com.cursorandroid.app.data.api.RepositoryItem
import com.cursorandroid.app.data.api.gitPath
import com.cursorandroid.app.data.api.menuLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraReposField(
    repos: List<RepositoryItem>,
    selected: List<String>,
    primaryUrl: String,
    onSelected: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    cap: Int = MAX_AGENT_REPOS,
    hint: String = "Cloud starts take up to 20 repos. A named cloud environment is separate from this list.",
) {
    var menu by remember { mutableStateOf(false) }
    val taken = (selected + primaryUrl).map { it.lowercase() }.toSet()
    val choices = repos.filter { it.url.lowercase() !in taken }
    val room = selected.size + if (primaryUrl.isBlank()) 0 else 1
    val limit = cap.coerceAtLeast(0)
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
        ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it && room < limit }) {
            OutlinedTextField(
                value = if (room >= limit) "$limit repo limit" else "Add a repository",
                onValueChange = {},
                readOnly = true,
                enabled = room < limit && choices.isNotEmpty(),
                label = { Text("More repos") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                supportingText = { Text(hint) },
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
    owner: String,
    onOwner: (String) -> Unit,
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
            OwnerChips(owner = owner, onOwner = onOwner)
            OutlinedTextField(
                value = install,
                onValueChange = onInstall,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Install command") },
                placeholder = { Text("optional, runs during a Build") },
                supportingText = {
                    Text("Saves the environment, then starts the agent on that name. Repos stay on the environment.")
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedEnvironmentPanel(
    choices: List<EnvironmentChoice>,
    name: String,
    onName: (String) -> Unit,
    selectedId: String?,
    onPicked: (EnvironmentChoice) -> Unit,
    status: String,
    onDelete: (() -> Unit)?,
    createName: String,
    onCreateName: (String) -> Unit,
    owner: String,
    onOwner: (String) -> Unit,
    catalogRepos: List<RepositoryItem>,
    repoUrls: List<String>,
    onRepoUrls: (List<String>) -> Unit,
    environmentJson: String,
    onEnvironmentJson: (String) -> Unit,
    onCreate: () -> Unit,
    busy: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    var typedUrl by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
                label = { Text("Environment") },
                placeholder = { Text("Saved environment") },
                supportingText = {
                    Text("Saved environments with an id are listed first. A named environment replaces repos on the request.")
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu) },
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (choices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No saved environments yet") },
                        onClick = { menu = false },
                        enabled = false,
                    )
                }
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.menuLabel()) },
                        onClick = {
                            onPicked(choice)
                            menu = false
                        },
                    )
                }
            }
        }
        if (!selectedId.isNullOrBlank()) {
            Text(
                status.ifBlank { "Loading builds…" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onDelete?.invoke() },
                enabled = onDelete != null && !busy,
            ) {
                Text("Delete environment")
            }
        } else if (name.isNotBlank()) {
            Text(
                "No id yet. The public API has no environment list, so this name comes from a past agent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Create environment", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = createName,
            onValueChange = onCreateName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Name") },
        )
        OwnerChips(owner = owner, onOwner = onOwner)
        ExtraReposField(
            repos = catalogRepos,
            selected = repoUrls,
            primaryUrl = "",
            onSelected = onRepoUrls,
            cap = MAX_ENV_REPOS,
            hint = "Up to 100 repos. Empty is allowed.",
        )
        OutlinedTextField(
            value = typedUrl,
            onValueChange = { typedUrl = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Repo URL") },
            placeholder = { Text("https://github.com/org/repo") },
        )
        Button(
            onClick = {
                val url = typedUrl.trim()
                if (url.isEmpty() || repoUrls.any { it.equals(url, ignoreCase = true) }) return@Button
                if (repoUrls.size >= MAX_ENV_REPOS) return@Button
                onRepoUrls(repoUrls + url)
                typedUrl = ""
            },
            enabled = typedUrl.isNotBlank() && repoUrls.size < MAX_ENV_REPOS,
        ) {
            Text("Add repo")
        }
        OutlinedTextField(
            value = environmentJson,
            onValueChange = onEnvironmentJson,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("environment.json") },
            placeholder = { Text("{\"install\":\"pnpm install\"}") },
            minLines = 2,
            supportingText = {
                Text("JSON string. Blank sends {\"install\":\"true\"}.")
            },
        )
        Button(
            onClick = onCreate,
            enabled = createName.isNotBlank() && !busy,
        ) {
            Text(if (busy) "Saving…" else "Save environment")
        }
    }
}

@Composable
private fun OwnerChips(owner: String, onOwner: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !owner.equals("team", ignoreCase = true),
            onClick = { onOwner("personal") },
            label = { Text("Personal") },
        )
        FilterChip(
            selected = owner.equals("team", ignoreCase = true),
            onClick = { onOwner("team") },
            label = { Text("Team") },
        )
    }
}
