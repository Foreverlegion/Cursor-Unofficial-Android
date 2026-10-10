package com.cursorandroid.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.repo.ClientOrigin
import com.cursorandroid.app.data.repo.BackupOpen
import com.cursorandroid.app.data.repo.SecretsVault
import com.cursorandroid.app.data.repo.SettingsBackup
import com.cursorandroid.app.data.repo.SettingsSnapshot
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
    var exportOptions by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var pendingImport by remember { mutableStateOf<SettingsSnapshot?>(null) }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val passphrase = exportPassphrase
        exportPassphrase = null
        if (uri == null) {
            passphrase?.fill(' ')
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { SettingsBackup.export(context, container, uri, passphrase) }.isSuccess
            }
            passphrase?.fill(' ')
            error = !ok
            status = when {
                !ok -> "Export failed"
                passphrase != null -> "Settings exported, secrets sealed with your passphrase"
                else -> "Settings exported without secrets"
            }
        }
    }

    fun finish(result: BackupOpen, withSecrets: Boolean) {
        error = result != BackupOpen.Ok
        status = when {
            result == BackupOpen.WrongPassphrase -> "Wrong passphrase. Nothing was imported."
            withSecrets -> "Settings imported"
            else -> "Settings imported, secrets skipped"
        }
        if (!error) onImported()
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { runCatching { SettingsBackup.load(context, uri) }.getOrNull() }
            when {
                loaded == null -> {
                    error = true
                    status = "Import failed"
                }
                loaded.secrets != null -> pendingImport = loaded
                else -> {
                    val result = withContext(Dispatchers.IO) { SettingsBackup.apply(container, loaded, null) }
                    finish(result, withSecrets = true)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (allowExport) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { exportOptions = true }
                    .padding(vertical = 14.dp),
            ) {
                Text("Export settings", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Contains your API key and forge tokens in plain text. Keep the file private.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "Import settings",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { importer.launch(IMPORT_TYPES) }
                .padding(vertical = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (status != null) {
            Text(
                status!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (exportOptions) {
        ExportOptionsDialog(
            onDismiss = { exportOptions = false },
            onChoose = { passphrase ->
                exportOptions = false
                exportPassphrase = passphrase
                export.launch("${ClientOrigin.ID}-settings.json")
            },
        )
    }

    pendingImport?.let { loaded ->
        ImportPassphraseDialog(
            onDismiss = { pendingImport = null },
            onWithout = {
                pendingImport = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { SettingsBackup.apply(container, loaded, null) }
                    finish(result, withSecrets = false)
                }
            },
            tryPassphrase = { passphrase ->
                val result = withContext(Dispatchers.IO) { SettingsBackup.apply(container, loaded, passphrase) }
                if (result == BackupOpen.Ok) {
                    pendingImport = null
                    finish(result, withSecrets = true)
                }
                result == BackupOpen.Ok
            },
        )
    }
}

@Composable
private fun ExportOptionsDialog(onDismiss: () -> Unit, onChoose: (CharArray?) -> Unit) {
    var secrets by remember { mutableStateOf(false) }
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val tooShort = pass.length < SecretsVault.MIN_PASSPHRASE
    val mismatch = pass != again
    val ok = !secrets || (!tooShort && !mismatch)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The API key, forge tokens, and MCP header, env, and OAuth values are left out by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = secrets, onCheckedChange = { secrets = it })
                    Text("Include secrets (encrypted)")
                }
                if (secrets) {
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pass.isNotEmpty() && tooShort,
                        supportingText = { Text("At least ${SecretsVault.MIN_PASSPHRASE} characters") },
                    )
                    OutlinedTextField(
                        value = again,
                        onValueChange = { again = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Repeat passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = again.isNotEmpty() && mismatch,
                    )
                    Text(
                        "There is no way to recover a lost passphrase. Without it the secrets in the file cannot be opened.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onChoose(if (secrets) pass.toCharArray() else null) }) { Text("Choose file") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImportPassphraseDialog(
    onDismiss: () -> Unit,
    onWithout: () -> Unit,
    tryPassphrase: suspend (CharArray) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var pass by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Passphrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This file has encrypted secrets. Enter its passphrase to import them, or import without them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it; wrong = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = wrong,
                    supportingText = { if (wrong) Text("Wrong passphrase") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pass.isNotEmpty() && !busy,
                onClick = {
                    val chars = pass.toCharArray()
                    busy = true
                    scope.launch {
                        val ok = tryPassphrase(chars)
                        chars.fill(' ')
                        busy = false
                        if (!ok) wrong = true
                    }
                },
            ) { Text("Import") }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = onWithout) { Text("Without secrets") }
                TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private val IMPORT_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream",
)
