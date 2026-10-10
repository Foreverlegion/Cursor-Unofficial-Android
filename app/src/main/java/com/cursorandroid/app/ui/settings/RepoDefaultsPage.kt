package com.cursorandroid.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.api.CloudEnvironment
import com.cursorandroid.app.data.api.ModelItem
import com.cursorandroid.app.data.api.defaultParams
import com.cursorandroid.app.data.api.gitPath
import com.cursorandroid.app.data.api.repoKey
import com.cursorandroid.app.data.repo.RepoDefault
import com.cursorandroid.app.data.repo.knownRepoUrls
import com.cursorandroid.app.ui.chat.ModelParamRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDefaultsPage(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    var saved by remember { mutableStateOf(container.store.repoDefaults()) }
    var editing by remember { mutableStateOf<RepoDefault?>(null) }
    var picking by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var known by remember { mutableStateOf<List<String>>(emptyList()) }
    var models by remember { mutableStateOf<List<ModelItem>>(emptyList()) }
    var envs by remember { mutableStateOf<List<CloudEnvironment>>(emptyList()) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var branchQuery by remember { mutableStateOf("") }
    var branchNote by remember { mutableStateOf("") }
    var modelMenu by remember { mutableStateOf(false) }
    var envMenu by remember { mutableStateOf(false) }
    val draft = editing
    BackHandler(enabled = draft != null || picking) {
        editing = null
        picking = false
    }

    LaunchedEffect(Unit) {
        envs = container.catalog.savedEnvironments()
        val listed = runCatching { container.repo.repositories() }.getOrDefault(container.catalog.repos())
        val snaps = container.catalog.gitSnaps().values.mapNotNull { it.repoUrl }
        known = knownRepoUrls(listed.map { it.url }, snaps, emptyList())
        models = runCatching { container.repo.models() }.getOrDefault(emptyList())
    }
    LaunchedEffect(draft?.repoUrl) {
        val url = draft?.repoUrl.orEmpty()
        if (url.isBlank()) {
            branches = emptyList()
            branchNote = ""
            return@LaunchedEffect
        }
        branchNote = "Loading branches…"
        val names = runCatching { container.repo.branches(url) }.getOrDefault(emptyList())
        branches = names
        val forge = container.store.forgeForRepo(url)
        branchNote = when {
            names.isNotEmpty() && forge != null -> "Branches from ${forge.displayName()}."
            names.isNotEmpty() -> "Branches from Cursor. Type a name if yours is missing."
            forge == null -> "No forge is connected for this host. Type the branch name."
            else -> "The forge returned no branches. Type the branch name."
        }
    }

    Column(modifier) {
        Text(
            "Used when you start a new agent on that repo. You can still change them for one chat. The account default model is used when a repo has no model.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (draft == null && !picking) {
            if (saved.isEmpty()) {
                SettingsStaticRow("No repo defaults", "Add one to prefill new agents")
            }
            saved.forEach { item ->
                val model = models.firstOrNull { it.id == item.modelId }?.displayName ?: item.modelId.ifBlank { "Account model" }
                val branch = item.branch.ifBlank { "Default branch" }
                SettingsLinkRow(
                    title = gitPath(item.repoUrl).ifBlank { item.repoUrl },
                    summary = "$model · $branch",
                    onClick = {
                        branchQuery = item.branch
                        editing = item
                    },
                )
            }
            SettingsLinkRow(
                title = "Add repo default",
                summary = "Repos from Cursor, forges, and recent agents",
                onClick = {
                    query = ""
                    picking = true
                },
            )
            return@Column
        }
        if (picking && draft == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Find a repo") },
                placeholder = { Text("Search or paste a URL") },
                singleLine = true,
            )
            val taken = saved.map { repoKey(it.repoUrl) }.toSet()
            val shown = known.filter { repoKey(it) !in taken && (query.isBlank() || it.contains(query, ignoreCase = true) || gitPath(it).contains(query, ignoreCase = true)) }
            if (shown.isEmpty()) {
                SettingsStaticRow("No matching repos", "Paste a repository URL below")
            }
            shown.take(30).forEach { url ->
                SettingsLinkRow(
                    title = gitPath(url).ifBlank { url },
                    summary = url,
                    onClick = {
                        picking = false
                        branchQuery = ""
                        editing = RepoDefault(repoUrl = url, autoCreatePr = true)
                    },
                )
            }
            val pasted = query.trim()
            if (pasted.startsWith("https://") && repoKey(pasted) !in taken) {
                SettingsLinkRow(
                    title = "Use this URL",
                    summary = pasted,
                    onClick = {
                        picking = false
                        branchQuery = ""
                        editing = RepoDefault(repoUrl = pasted, autoCreatePr = true)
                    },
                )
            }
            TextButton(onClick = { picking = false }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Back")
            }
            return@Column
        }
        val item = draft ?: return@Column
        SettingsStaticRow("Repository", gitPath(item.repoUrl).ifBlank { item.repoUrl })
        val modelLabel = models.firstOrNull { it.id == item.modelId }?.displayName
            ?: item.modelId.ifBlank { "Account default" }
        ExposedDropdownMenuBox(expanded = modelMenu, onExpandedChange = { modelMenu = it }) {
            OutlinedTextField(
                value = modelLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenu) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ExposedDropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Account default") },
                    onClick = {
                        editing = item.copy(modelId = "", modelParams = emptyList())
                        modelMenu = false
                    },
                )
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName ?: model.id) },
                        onClick = {
                            editing = item.copy(modelId = model.id, modelParams = model.defaultParams())
                            modelMenu = false
                        },
                    )
                }
            }
        }
        ModelParamRow(
            model = models.firstOrNull { it.id == item.modelId },
            params = item.modelParams,
            onParams = { editing = item.copy(modelParams = it) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = branchQuery,
            onValueChange = {
                branchQuery = it
                editing = item.copy(branch = it.trim())
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Default branch") },
            placeholder = { Text("Search or type a branch") },
            supportingText = { Text(branchNote) },
            singleLine = true,
        )
        val matches = branches.filter { branchQuery.isBlank() || it.contains(branchQuery, ignoreCase = true) }.take(12)
        matches.forEach { name ->
            Text(
                name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        branchQuery = name
                        editing = item.copy(branch = name)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DefaultSwitch("Open PR when finished", "Sends autoCreatePR for this repo", item.autoCreatePr != false) {
            editing = item.copy(autoCreatePr = it)
        }
        if (item.autoCreatePr != false) {
            DefaultSwitch("Don't add me as reviewer", "Sends skipReviewerRequest", item.skipReviewer == true) {
                editing = item.copy(skipReviewer = it)
            }
        }
        DefaultSwitch(
            "Open the PR link on this phone",
            "Once, when the finished run includes a pull request URL",
            item.openFinishedPr,
        ) { editing = item.copy(openFinishedPr = it) }
        val allMcp = item.mcpIds == null
        DefaultSwitch("Attach every enabled MCP server", "Turn off to choose servers", allMcp) { checked ->
            editing = item.copy(mcpIds = if (checked) null else container.store.storedMcps().filter { it.enabled }.map { it.id })
        }
        if (!allMcp) {
            val selected = item.mcpIds.orEmpty().toSet()
            container.store.storedMcps().forEach { server ->
                DefaultSwitch(server.name.ifBlank { "MCP server" }, server.kindLabel(), server.id in selected) { checked ->
                    val next = if (checked) selected + server.id else selected - server.id
                    editing = item.copy(mcpIds = next.toList())
                }
            }
        }
        OutlinedTextField(
            value = item.promptPrefix,
            onValueChange = { editing = item.copy(promptPrefix = it.take(2000)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Prompt prefix") },
            placeholder = { Text("Instructions added in front of the first prompt") },
            minLines = 2,
        )
        val envLabel = item.environmentName.ifBlank { "None" }
        ExposedDropdownMenuBox(expanded = envMenu, onExpandedChange = { envMenu = it }) {
            OutlinedTextField(
                value = envLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default environment") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = envMenu) },
                supportingText = { Text("Saved on this phone. Cursor has no list-all environments API.") },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ExposedDropdownMenu(expanded = envMenu, onDismissRequest = { envMenu = false }) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = {
                        editing = item.copy(environmentId = "", environmentName = "")
                        envMenu = false
                    },
                )
                envs.filter { it.name.isNotBlank() }.forEach { env ->
                    DropdownMenuItem(
                        text = { Text(env.name) },
                        onClick = {
                            editing = item.copy(environmentId = env.id, environmentName = env.name)
                            envMenu = false
                        },
                    )
                }
            }
        }
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(
                onClick = {
                    container.store.saveRepoDefault(item)
                    saved = container.store.repoDefaults()
                    editing = null
                    picking = false
                },
            ) { Text("Save") }
            TextButton(
                onClick = {
                    container.store.deleteRepoDefault(item.repoUrl)
                    saved = container.store.repoDefaults()
                    editing = null
                },
            ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { editing = null }) { Text("Back") }
        }
    }
}

@Composable
private fun DefaultSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
