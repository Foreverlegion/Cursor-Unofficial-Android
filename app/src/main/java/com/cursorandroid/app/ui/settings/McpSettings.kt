package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.auth.ApiKeyStore
import com.cursorandroid.app.data.repo.SafeLinks
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

@Composable
fun McpListSection(store: ApiKeyStore) {
    var items by remember { mutableStateOf(store.storedMcps()) }
    var edit by remember { mutableStateOf<StoredMcpServer?>(null) }
    val ready = storedMcpsToApi(items).orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (ready.isEmpty()) {
                "None enabled. New runs get no extra MCP tools."
            } else {
                "${ready.size} enabled on new agents and follow-ups."
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
                TextButton(onClick = { edit = item }) { Text("Edit") }
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
        TextButton(onClick = { edit = StoredMcpServer() }) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("Add MCP")
        }
    }

    edit?.let { current ->
        McpEditDialog(
            initial = current,
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
private fun McpEditDialog(
    initial: StoredMcpServer,
    onDismiss: () -> Unit,
    onSave: (StoredMcpServer) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var stdio by remember { mutableStateOf(initial.isStdio()) }
    var url by remember { mutableStateOf(initial.url.orEmpty()) }
    var headers by remember { mutableStateOf(headerLines(initial.headers)) }
    var command by remember { mutableStateOf(initial.command.orEmpty()) }
    var args by remember { mutableStateOf(argLines(initial.args)) }
    var env by remember { mutableStateOf(envLines(initial.env)) }
    val urlOk = url.isBlank() || SafeLinks.isHttps(url)
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
                    FilterChip(selected = !stdio, onClick = { stdio = false }, label = { Text("HTTP") })
                    FilterChip(selected = stdio, onClick = { stdio = true }, label = { Text("Stdio") })
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
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            type = if (stdio) TYPE_STDIO else TYPE_HTTP,
                            url = url.trim().ifBlank { null },
                            headers = parseHeaderLines(headers),
                            command = command.trim().ifBlank { null },
                            args = parseArgLines(args),
                            env = parseEnvLines(env),
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
