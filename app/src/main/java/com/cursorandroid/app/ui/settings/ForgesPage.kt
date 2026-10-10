package com.cursorandroid.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.cursorandroid.app.data.repo.ForgeConnection
import com.cursorandroid.app.data.repo.ForgeKind
import com.cursorandroid.app.data.repo.normalizedForge
import com.cursorandroid.app.data.repo.upsertForge
import com.cursorandroid.app.data.repo.ForgeClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgesPage(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    var forges by remember { mutableStateOf(container.store.forges()) }
    var editing by remember { mutableStateOf<ForgeConnection?>(null) }
    var providerMenu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val draft = editing
    BackHandler(enabled = draft != null) {
        editing = null
        status = ""
    }

    // Hosted inside SettingsScreen's scrolling column; a second verticalScroll here crashes.
    Column(modifier) {
        Text(
            "Tokens stay encrypted on this phone. Branch lists and new repos use the matching forge.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (draft == null) {
            if (forges.isEmpty()) {
                SettingsStaticRow("No forges yet", "Add GitHub or another host")
            }
            forges.forEach { forge ->
                SettingsLinkRow(
                    title = forge.displayName(),
                    summary = forge.kind().label + if (forge.token.isBlank()) " · no token" else " · token saved",
                    onClick = {
                        status = ""
                        editing = forge
                    },
                )
            }
            SettingsLinkRow(
                title = "Add forge",
                summary = "GitHub, GitLab, Bitbucket, and others",
                onClick = {
                    status = ""
                    editing = normalizedForge(ForgeConnection(provider = ForgeKind.GITHUB.id))
                },
            )
            return@Column
        }
        val kind = draft.kind()
        ExposedDropdownMenuBox(expanded = providerMenu, onExpandedChange = { providerMenu = it }) {
            OutlinedTextField(
                value = kind.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenu) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ExposedDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                ForgeKind.entries.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = {
                            editing = normalizedForge(draft.copy(provider = item.id, baseUrl = "", apiUrl = ""))
                            providerMenu = false
                        },
                    )
                }
            }
        }
        ForgeField("Name", draft.name) { editing = draft.copy(name = it) }
        if (kind.needsUrls || kind == ForgeKind.AZURE || kind == ForgeKind.GITHUB_ENTERPRISE) {
            ForgeField("Base URL", draft.baseUrl, "https://git.example.com") {
                editing = draft.copy(baseUrl = it)
            }
            ForgeField("API URL", draft.apiUrl, kind.apiUrl.ifBlank { "Filled from the base URL when it can be" }) {
                editing = draft.copy(apiUrl = it)
            }
        }
        ForgeField("Username", draft.username, "Optional. Required as the Bitbucket workspace.") {
            editing = draft.copy(username = it)
        }
        OutlinedTextField(
            value = draft.token,
            onValueChange = { editing = draft.copy(token = it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Token / personal access token") },
            placeholder = { Text("Auth type: token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        if (status.isNotBlank()) {
            Text(
                status,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(
                enabled = !testing,
                onClick = {
                    val current = normalizedForge(draft)
                    testing = true
                    status = "Testing…"
                    scope.launch {
                        status = ForgeClient.test(current)
                        testing = false
                    }
                },
            ) { Text(if (testing) "Testing…" else "Test connection") }
            TextButton(
                onClick = {
                    val saved = normalizedForge(draft)
                    if (saved.kind().needsUrls && saved.baseUrl.isBlank() && saved.apiUrl.isBlank()) {
                        status = "Add a base URL or an API URL."
                        return@TextButton
                    }
                    val next = upsertForge(container.store.forges(), saved)
                    container.store.saveForges(next)
                    forges = container.store.forges()
                    editing = null
                    status = ""
                },
            ) { Text("Save") }
        }
        if (draft.id.isNotBlank()) {
            TextButton(
                onClick = {
                    val next = container.store.forges().filterNot { it.id == draft.id }
                    container.store.saveForges(next)
                    forges = container.store.forges()
                    editing = null
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("Remove forge", color = MaterialTheme.colorScheme.error) }
        }
        TextButton(onClick = { editing = null }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Back to forges")
        }
        HorizontalDivider()
    }
}

@Composable
private fun ForgeField(
    label: String,
    value: String,
    placeholder: String = "",
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
    )
}
