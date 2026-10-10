package com.cursorandroid.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.repo.McpImportItem
import com.cursorandroid.app.data.repo.McpImportParse
import com.cursorandroid.app.data.repo.SafeLinks
import com.cursorandroid.app.data.repo.StoredMcpServer
import com.cursorandroid.app.data.repo.exportMcpJson
import com.cursorandroid.app.data.repo.mcpNameKey
import com.cursorandroid.app.data.repo.mergeMcpImport
import com.cursorandroid.app.data.repo.parseMcpJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_MCP_FILE_BYTES = 1024L * 1024L

private fun readText(context: Context, uri: Uri): String? =
    context.contentResolver.openInputStream(uri)?.use { SafeLinks.readBounded(it, MAX_MCP_FILE_BYTES) }
        ?.toString(Charsets.UTF_8)

@Composable
fun McpImportDialog(
    existing: List<StoredMcpServer>,
    onDismiss: () -> Unit,
    onMerged: (List<StoredMcpServer>, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<McpImportParse?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var confirmReplace by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }

    val have = existing.map { mcpNameKey(it.name) }.toSet()

    fun preview(raw: String) {
        val result = parseMcpJson(raw)
        parsed = result
        picked = result.items
            .filter { it.problem == null && mcpNameKey(it.server.name) !in have }
            .map { mcpNameKey(it.server.name) }
            .toSet()
    }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = withContext(Dispatchers.IO) { runCatching { readText(context, uri) }.getOrNull() }
            if (raw == null) {
                fileError = "Could not read that file"
            } else {
                fileError = null
                text = raw
                preview(raw)
            }
        }
    }

    fun merge(replace: Set<String>) {
        val chosen = parsed?.items.orEmpty().filter { mcpNameKey(it.server.name) in picked }.map { it.server }
        val result = mergeMcpImport(existing, chosen, replace)
        val bits = buildList {
            if (result.added > 0) add("${result.added} added")
            if (result.replaced > 0) add("${result.replaced} replaced")
            if (result.skipped > 0) add("${result.skipped} skipped")
        }
        onMerged(result.items, if (bits.isEmpty()) "Nothing imported" else "MCP import: ${bits.joinToString(", ")}")
    }

    val result = parsed
    if (result == null || result.error != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Import mcp.json") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Same format as ~/.cursor/mcp.json and .cursor/mcp.json on a PC. Paste it or choose a file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 260.dp),
                        placeholder = { Text("{\"mcpServers\": { ... }}") },
                    )
                    (result?.error ?: fileError)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { chooser.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                        Text("Choose file")
                    }
                }
            },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { preview(text) }) { Text("Preview") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        return
    }

    val replacing = result.items.filter { mcpNameKey(it.server.name) in picked && mcpNameKey(it.server.name) in have }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose servers") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                result.items.forEach { item ->
                    ImportRow(
                        item = item,
                        conflict = mcpNameKey(item.server.name) in have,
                        checked = mcpNameKey(item.server.name) in picked,
                        onChecked = { on ->
                            val key = mcpNameKey(item.server.name)
                            picked = if (on) picked + key else picked - key
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked.isNotEmpty(),
                onClick = { if (replacing.isNotEmpty()) confirmReplace = true else merge(emptySet()) },
            ) { Text("Import ${picked.size}") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { parsed = null }) { Text("Back") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace saved servers?") },
            text = {
                Text(
                    "These already exist and will be replaced: ${replacing.joinToString { it.server.name }}. " +
                        "Headers, env, and OAuth secrets the file leaves empty are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmReplace = false; merge(replacing.map { mcpNameKey(it.server.name) }.toSet()) }) {
                    Text("Replace")
                }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ImportRow(item: McpImportItem, conflict: Boolean, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked, enabled = item.problem == null)
        Column(Modifier.weight(1f)) {
            Text(item.server.name.ifBlank { "Untitled" })
            val detail = listOfNotNull(
                item.server.kindLabel(),
                item.problem,
                "Already saved, replaces it".takeIf { conflict && item.problem == null },
            ) + item.notes
            Text(
                detail.joinToString(". "),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun McpExportDialog(items: List<StoredMcpServer>, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var secrets by remember { mutableStateOf(false) }
    val hasSecrets = items.any { it.hasSecrets() }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val body = exportMcpJson(items, secrets)
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
                }.isSuccess
            }
            onDone(if (ok) "mcp.json saved" else "Could not save mcp.json")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export mcp.json") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Same format as Cursor desktop's mcp.json. Header and env values and OAuth client secrets are left empty.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasSecrets) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(checked = secrets, onCheckedChange = { secrets = it })
                        Text("Include secrets")
                    }
                    if (secrets) {
                        Text(
                            "The file is plain text. Anyone who gets it can use those tokens.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { save.launch("mcp.json") }) { Text("Save file") } },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(exportMcpJson(items, secrets)))
                        onDone("mcp.json copied")
                    },
                ) { Text("Copy") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
