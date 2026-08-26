package com.cursorandroid.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.repo.ClientOrigin
import com.cursorandroid.app.data.repo.SettingsBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsTransfer(
    container: AppContainer,
    onImported: () -> Unit = {},
    allowExport: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { SettingsBackup.export(context, container, uri) }.isSuccess
            }
            error = !ok
            status = if (ok) "Settings exported" else "Export failed"
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { SettingsBackup.import(context, container, uri) }.getOrDefault(false)
            }
            error = !ok
            status = if (ok) "Settings imported" else "Import failed"
            if (ok) onImported()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (allowExport) {
                TextButton(onClick = { export.launch("${ClientOrigin.ID}-settings.json") }) {
                    Text("Export settings")
                }
            }
            TextButton(onClick = { importer.launch(IMPORT_TYPES) }) {
                Text("Import settings")
            }
        }
        Text(
            if (allowExport) {
                "Export includes the API key, GitHub token, theme color, inbox tabs, notify, approval alerts, hide thinking/tools, default model, MCP, chat names, favorites, drafts, and cached transcripts. Keep the file private."
            } else {
                "Import a previous export to restore the API key and local settings."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status != null) {
            Text(
                status!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val IMPORT_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream",
)
