package com.cursorandroid.app.ui.composeAgent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.repo.ForgeConnection
import com.cursorandroid.app.data.repo.ForgeSyncState
import com.cursorandroid.app.data.repo.forgeSyncSupported
import com.cursorandroid.app.data.repo.normalizedForge
import com.cursorandroid.app.data.repo.sourceLabel
import com.cursorandroid.app.data.repo.syncLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePicker(
    sources: List<String>,
    selected: String,
    loading: Boolean,
    emptyLabel: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.ifBlank { if (loading) "Loading…" else emptyLabel },
            onValueChange = {},
            readOnly = true,
            label = { Text("Source") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (sources.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No connected sources yet") },
                    onClick = { menu = false },
                    enabled = false,
                )
            }
            sources.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onPick(name)
                        menu = false
                    },
                )
            }
        }
    }
}

@Composable
fun ForgeSyncNotes(
    forges: List<ForgeConnection>,
    provider: String,
    sync: ForgeSyncState,
    modifier: Modifier = Modifier,
) {
    val shown = forges.map { normalizedForge(it) }.filter { forge ->
        forgeSyncSupported(forge) && (provider.isBlank() || forge.sourceLabel() == provider)
    }
    if (shown.isEmpty()) return
    val now = System.currentTimeMillis()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.forEach { forge ->
            val status = sync.statuses[forge.id]
            val syncing = forge.id in sync.running
            val failed = !syncing && status?.error != null
            Text(
                "${forge.displayName()} · " + if (syncing) "Syncing…" else status.syncLine(now),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Pull down to refresh repos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
