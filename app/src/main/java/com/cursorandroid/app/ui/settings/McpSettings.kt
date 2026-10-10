package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.repo.CURSOR_MCP_SETTINGS_URL
import com.cursorandroid.app.data.repo.MCP_PRESETS
import com.cursorandroid.app.data.repo.McpPreset
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.data.repo.StoredMcpAuth
import com.cursorandroid.app.data.repo.TYPE_SSE
import com.cursorandroid.app.data.repo.isBareScheme
import com.cursorandroid.app.data.repo.isOAuthOnlyHost
import com.cursorandroid.app.data.repo.StoredMcpServer
import com.cursorandroid.app.data.repo.TYPE_HTTP
import com.cursorandroid.app.data.repo.TYPE_STDIO
import com.cursorandroid.app.data.repo.argLines
import com.cursorandroid.app.data.repo.envLines
import com.cursorandroid.app.data.repo.headerLines
import com.cursorandroid.app.data.repo.parseArgLines
import com.cursorandroid.app.data.repo.parseEnvLines
import com.cursorandroid.app.data.repo.parseHeaderLines
import com.cursorandroid.app.data.repo.storedMcpsToApi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun McpListSection(store: ApiKeyStore) {
    var items by remember { mutableStateOf(store.storedMcps()) }
    var edit by remember { mutableStateOf<StoredMcpServer?>(null) }
    var editOauthHint by remember { mutableStateOf(false) }
    var presets by remember { mutableStateOf(false) }
    var importMenu by remember { mutableStateOf(false) }
    var importer by remember { mutableStateOf(false) }
    var exporter by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val ready = storedMcpsToApi(items).orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (ready.isEmpty()) {
                "None enabled. New agents get no extra MCP tools."
            } else {
                "${ready.size} enabled on new agents. Follow-ups keep the set the agent started with."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name.ifBlank { "Untitled" })
                    Text(
                        item.kindLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { edit = item; editOauthHint = false }) { Text("Edit") }
                IconButton(
                    onClick = {
                        items = items.filter { it.id != item.id }
                        store.saveStoredMcps(items)
                    },
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Remove")
                }
                Switch(
                    checked = item.enabled,
                    onCheckedChange = { on ->
                        items = items.map { if (it.id == item.id) it.copy(enabled = on) else it }
                        store.saveStoredMcps(items)
                    },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { edit = StoredMcpServer(); editOauthHint = false }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add MCP")
            }
            TextButton(onClick = { presets = true }) { Text("Presets") }
            Column {
                TextButton(onClick = { importMenu = true }) { Text("Import") }
                DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("From mcp.json") },
                        onClick = { importMenu = false; importer = true },
                    )
                    DropdownMenuItem(
                        text = { Text("From Cursor") },
                        enabled = false,
                        onClick = {},
                        trailingIcon = { Text("Unavailable", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            TextButton(onClick = { exporter = true }, enabled = items.isNotEmpty()) { Text("Export") }
        }
        Text(
            "Cursor has no API that lists the MCP servers saved on your account, so they cannot be pulled in. " +
                "Import a mcp.json instead, such as ~/.cursor/mcp.json on a PC.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (presets) {
        McpPresetDialog(
            onDismiss = { presets = false },
            onPick = { preset ->
                presets = false
                edit = preset.toServer()
                editOauthHint = preset.oauthOnly
            },
        )
    }

    if (importer) {
        McpImportDialog(
            existing = items,
            onDismiss = { importer = false },
            onMerged = { next, message ->
                items = next
                store.saveStoredMcps(next)
                status = message
                importer = false
            },
        )
    }

    if (exporter) {
        McpExportDialog(items = items, onDismiss = { exporter = false }, onDone = { status = it; exporter = false })
    }

    edit?.let { current ->
        McpEditDialog(
            initial = current,
            oauthHint = editOauthHint,
            onDismiss = { edit = null },
            onSave = { next ->
                items = if (items.any { it.id == next.id }) {
                    items.map { if (it.id == next.id) next else it }
                } else {
                    items + next
                }
                store.saveStoredMcps(items)
                edit = null
            },
        )
    }
}

@Composable
private fun McpPresetDialog(onDismiss: () -> Unit, onPick: (McpPreset) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Presets") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Fills the name and URL. Add your own token in the next step.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MCP_PRESETS.forEach { preset ->
                    TextButton(onClick = { onPick(preset) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(preset.label)
                            Text(
                                if (preset.oauthOnly) "${preset.url}  (OAuth)" else preset.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun McpEditDialog(
    initial: StoredMcpServer,
    oauthHint: Boolean,
    onDismiss: () -> Unit,
    onSave: (StoredMcpServer) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(initial.type.lowercase().takeIf { it == TYPE_STDIO || it == TYPE_SSE } ?: TYPE_HTTP) }
    var url by remember { mutableStateOf(initial.url.orEmpty()) }
    var headers by remember { mutableStateOf(headerLines(initial.headers)) }
    var command by remember { mutableStateOf(initial.command.orEmpty()) }
    var args by remember { mutableStateOf(argLines(initial.args)) }
    var env by remember { mutableStateOf(envLines(initial.env)) }
    var clientId by remember { mutableStateOf(initial.auth?.clientId.orEmpty()) }
    var clientSecret by remember { mutableStateOf(initial.auth?.clientSecret.orEmpty()) }
    var scopes by remember { mutableStateOf(initial.auth?.scopes.orEmpty().joinToString(" ")) }
    val stdio = kind == TYPE_STDIO
    val urlOk = url.isBlank() || SafeLinks.isHttps(url)
    val parsedHeaders = parseHeaderLines(headers)
    val bareHeader = parsedHeaders.entries.firstOrNull { isBareScheme(it.value) }
    val knownOauth = oauthHint || (!stdio && isOAuthOnlyHost(url))
    val canSave = name.trim().isNotBlank() && if (stdio) {
        command.trim().isNotBlank()
    } else {
        SafeLinks.isHttps(url)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isBlank()) "Add MCP" else "Edit MCP") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == TYPE_HTTP, onClick = { kind = TYPE_HTTP }, label = { Text("HTTP") })
                    FilterChip(selected = kind == TYPE_SSE, onClick = { kind = TYPE_SSE }, label = { Text("SSE") })
                    FilterChip(selected = stdio, onClick = { kind = TYPE_STDIO }, label = { Text("Stdio") })
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                )
                if (stdio) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Command") },
                        placeholder = { Text("npx") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = args,
                        onValueChange = { args = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Args") },
                        placeholder = { Text("one argument per line") },
                        minLines = 2,
                    )
                    OutlinedTextField(
                        value = env,
                        onValueChange = { env = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Env") },
                        placeholder = { Text("KEY=value") },
                        minLines = 2,
                    )
                    Text(
                        "Runs inside the cloud VM.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (kind == TYPE_SSE) {
                        Text(
                            "Cursor says SSE is not supported for cloud agents. Use HTTP if the server offers it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("HTTPS URL") },
                        singleLine = true,
                        isError = !urlOk,
                        supportingText = {
                            if (!urlOk) Text("HTTPS URL required")
                        },
                    )
                    OutlinedTextField(
                        value = headers,
                        onValueChange = { headers = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Headers") },
                        placeholder = { Text("Authorization: Bearer …") },
                        minLines = 2,
                        isError = bareHeader != null,
                        supportingText = {
                            bareHeader?.let { Text("${it.key} needs the token after ${it.value}. Until then it is not sent.") }
                        },
                    )
                    Text("OAuth client (optional)", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Client ID") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Client secret (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = scopes,
                        onValueChange = { scopes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Scopes (optional)") },
                        placeholder = { Text("space separated") },
                        singleLine = true,
                    )
                    Text(
                        if (knownOauth) {
                            "This server signs in with OAuth. Finish the login at cursor.com/agents, MCP Servers. It cannot be completed in this app."
                        } else {
                            "Servers that sign in with OAuth (GitLab, Slack, Sentry) finish the login at cursor.com/agents, MCP Servers."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { SafeLinks.open(context, CURSOR_MCP_SETTINGS_URL) }) {
                        Text("Open cursor.com/agents")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val auth = StoredMcpAuth(
                        clientId = clientId.trim(),
                        clientSecret = clientSecret.trim(),
                        scopes = scopes.split(' ', ',', '\n').map { it.trim() }.filter { it.isNotEmpty() },
                    ).takeIf { !stdio && it.clientId.isNotEmpty() }
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            type = kind,
                            url = url.trim().ifBlank { null },
                            headers = parsedHeaders,
                            command = command.trim().ifBlank { null },
                            args = parseArgLines(args),
                            env = parseEnvLines(env),
                            auth = auth,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
