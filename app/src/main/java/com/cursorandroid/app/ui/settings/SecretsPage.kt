package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.api.CloudEnvironment
import com.cursorandroid.app.data.api.SECRET_ENV_VAR
import com.cursorandroid.app.data.api.SECRET_RUNTIME
import com.cursorandroid.app.data.api.SecretVersion
import com.cursorandroid.app.data.api.secretCanSet
import com.cursorandroid.app.data.api.secretNameError
import com.cursorandroid.app.data.api.secretTypeLabel
import com.cursorandroid.app.data.api.secretValueError
import com.cursorandroid.app.data.repo.SecretAmbiguousException
import kotlinx.coroutines.launch

private data class SecretWrite(
    val delete: Boolean,
    val name: String,
    val value: String = "",
    val type: String? = null,
    val repos: List<String>? = null,
)

@Composable
fun SecretsPage(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf("team") }
    var environments by remember { mutableStateOf<List<CloudEnvironment>>(emptyList()) }
    var envId by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<SecretVersion>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SECRET_RUNTIME) }
    var reposText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var ambiguous by remember { mutableStateOf<List<SecretVersion>>(emptyList()) }
    var pending by remember { mutableStateOf<SecretWrite?>(null) }

    LaunchedEffect(Unit) {
        val listed = runCatching { container.repo.listCloudEnvironments() }.getOrDefault(emptyList())
        environments = listed
        if (envId.isBlank()) envId = listed.firstOrNull { it.id.isNotBlank() }?.id.orEmpty()
    }

    suspend fun reload() {
        items = when (target) {
            "all" -> container.repo.listAccountSecrets()
            "environment" -> {
                val id = envId.trim()
                if (id.isEmpty()) emptyList() else container.repo.listEnvironmentSecrets(id)
            }
            else -> container.repo.listTeamSecrets()
        }
    }

    LaunchedEffect(target, envId) {
        busy = true
        error = null
        try {
            reload()
        } catch (e: Exception) {
            error = e.message ?: "Could not load secrets"
        } finally {
            busy = false
        }
    }

    fun repos(): List<String>? {
        val raw = reposText.trim()
        if (raw.isEmpty()) return null
        if (raw == "*") return emptyList()
        return raw.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun runWrite(write: SecretWrite, versionId: String?) {
        scope.launch {
            busy = true
            error = null
            try {
                when (target) {
                    "environment" -> {
                        val id = envId.trim()
                        if (id.isEmpty()) {
                            error = "Pick an environment."
                            return@launch
                        }
                        if (write.delete) {
                            container.repo.deleteEnvironmentSecret(id, write.name, versionId)
                        } else {
                            container.repo.putEnvironmentSecret(id, write.name, write.value, write.type, write.repos, versionId)
                        }
                    }
                    "team" -> {
                        if (write.delete) {
                            container.repo.deleteTeamSecret(write.name, versionId)
                        } else {
                            container.repo.putTeamSecret(write.name, write.value, write.type, write.repos, versionId)
                        }
                    }
                    else -> {
                        error = "Pick Team or an environment to change a secret."
                        return@launch
                    }
                }
                value = ""
                pending = null
                ambiguous = emptyList()
                reload()
            } catch (e: SecretAmbiguousException) {
                pending = write
                ambiguous = e.versions
            } catch (e: Exception) {
                error = e.message ?: "Request failed"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Values are never shown. Build secrets cannot be set from the API. Delete still removes one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = target == "team", onClick = { target = "team" }, label = { Text("Team") })
            FilterChip(selected = target == "environment", onClick = { target = "environment" }, label = { Text("Environment") })
            FilterChip(selected = target == "all", onClick = { target = "all" }, label = { Text("All") })
        }
        if (target == "environment") {
            environments.filter { it.id.isNotBlank() }.forEach { env ->
                FilterChip(
                    selected = envId == env.id,
                    onClick = { envId = env.id },
                    label = { Text(env.name.ifBlank { env.id }) },
                )
            }
            if (environments.none { it.id.isNotBlank() }) {
                Text("No saved environments.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (items.isEmpty()) {
            Text(if (busy) "Loading…" else "No secrets.", style = MaterialTheme.typography.bodyMedium)
        }
        items.forEach { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(item.name.ifBlank { item.id.orEmpty() }, style = MaterialTheme.typography.bodyLarge)
                Text(
                    item.scopeLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    if (secretCanSet(item.type) && target != "all") {
                        TextButton(
                            onClick = {
                                name = item.name
                                type = item.type ?: SECRET_RUNTIME
                            },
                            enabled = !busy && item.name.isNotBlank(),
                        ) { Text("Rotate") }
                    }
                    if (target != "all") {
                        TextButton(
                            onClick = { runWrite(SecretWrite(delete = true, name = item.name), item.id) },
                            enabled = !busy && item.name.isNotBlank(),
                        ) { Text("Delete") }
                    }
                }
            }
        }
        if (target != "all") {
            Text("Set secret", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Value") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == SECRET_RUNTIME,
                    onClick = { type = SECRET_RUNTIME },
                    label = { Text(secretTypeLabel(SECRET_RUNTIME)) },
                )
                FilterChip(
                    selected = type == SECRET_ENV_VAR,
                    onClick = { type = SECRET_ENV_VAR },
                    label = { Text(secretTypeLabel(SECRET_ENV_VAR)) },
                )
            }
            OutlinedTextField(
                value = reposText,
                onValueChange = { reposText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Repos") },
                placeholder = { Text("Blank keeps current. * is every repo.") },
                singleLine = true,
            )
            Button(
                onClick = {
                    val nameError = secretNameError(name)
                    val valueError = secretValueError(value)
                    error = nameError ?: valueError
                    if (error != null) return@Button
                    runWrite(
                        SecretWrite(
                            delete = false,
                            name = name.trim(),
                            value = value,
                            type = type,
                            repos = repos(),
                        ),
                        null,
                    )
                },
                enabled = !busy,
            ) { Text(if (busy) "Saving…" else "Save") }
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }
    if (ambiguous.isNotEmpty() && pending != null) {
        val write = pending!!
        AlertDialog(
            onDismissRequest = {
                ambiguous = emptyList()
                pending = null
            },
            title = { Text("Which ${write.name}?") },
            text = {
                Column {
                    Text("That name has more than one version. Pick one.")
                    ambiguous.forEach { version ->
                        TextButton(
                            onClick = {
                                val id = version.id
                                ambiguous = emptyList()
                                if (!id.isNullOrBlank()) runWrite(write, id)
                            },
                        ) {
                            Text(version.id.orEmpty() + " · " + version.repos.ifEmpty { listOf("every repo") }.joinToString(", "))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        ambiguous = emptyList()
                        pending = null
                    },
                ) { Text("Cancel") }
            },
        )
    }
}
