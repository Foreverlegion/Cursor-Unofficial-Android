package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.repo.AUTO_HIDE_CHOICES
import com.cursorandroid.app.data.repo.MACHINE_DELETE_NOTE
import com.cursorandroid.app.data.repo.MachineRow
import com.cursorandroid.app.data.repo.MachineVisibility
import com.cursorandroid.app.data.repo.autoHideLabel
import com.cursorandroid.app.data.repo.machineRows
import com.cursorandroid.app.data.repo.statusLine

@Composable
fun MachinesPage(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    var prefs by remember { mutableStateOf(container.machines.prefs()) }
    var current by remember { mutableStateOf(container.catalog.computers()) }
    var loading by remember { mutableStateOf(true) }
    var autoMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MachineRow?>(null) }
    val now = container.machines.now()
    val rows = machineRows(current, prefs, now)

    LaunchedEffect(Unit) {
        val listed = runCatching { container.repo.listComputers(container.catalog.agents()) }.getOrNull()
        if (listed != null) current = listed
        prefs = container.machines.prefs()
        loading = false
    }

    fun mark(change: () -> Unit) {
        change()
        prefs = container.machines.prefs()
    }

    Column(modifier) {
        Text(
            "Every machine and worker this phone has seen. Online ones come from your Cursor account. " +
                "Offline ones are remembered from earlier listings and from agents that used them. " +
                "Hidden machines stay out of New agent and the Remote list. $MACHINE_DELETE_NOTE",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsChoiceRow(
            title = "Auto-hide offline machines",
            summary = if (prefs.autoHideDays == 0) {
                "Off"
            } else {
                "Not seen for ${autoHideLabel(prefs.autoHideDays)}"
            },
            onClick = { autoMenu = true },
        )
        if (rows.isEmpty()) {
            SettingsStaticRow(
                if (loading) "Loading machines" else "No machines yet",
                "Connect a PC with Remote Control or a My Machines worker",
            )
        }
        val shown = rows.filter { it.visibility == MachineVisibility.Visible }
        val hidden = rows.filter { it.visibility == MachineVisibility.Hidden || it.visibility == MachineVisibility.AutoHidden }
        val forgotten = rows.filter { it.visibility == MachineVisibility.Forgotten }
        if (shown.isNotEmpty()) MachineSection("Shown")
        shown.forEach { row ->
            MachineRowItem(
                row = row,
                now = now,
                primary = "Hide" to { mark { container.machines.hide(row.key, row.name) } },
                secondary = "Delete" to { deleting = row },
            )
        }
        if (hidden.isNotEmpty()) MachineSection("Hidden")
        hidden.forEach { row ->
            MachineRowItem(
                row = row,
                now = now,
                note = if (row.visibility == MachineVisibility.AutoHidden) "Hidden automatically" else null,
                primary = "Unhide" to { mark { container.machines.show(row.key, row.name) } },
                secondary = "Delete" to { deleting = row },
            )
        }
        if (forgotten.isNotEmpty()) MachineSection("Forgotten on this phone")
        forgotten.forEach { row ->
            MachineRowItem(
                row = row,
                now = now,
                primary = "Restore" to { mark { container.machines.show(row.key, row.name) } },
            )
        }
    }

    if (autoMenu) {
        ChoiceDialog(
            title = "Auto-hide offline machines",
            selected = prefs.autoHideDays.toString(),
            options = AUTO_HIDE_CHOICES.map { (days, label) ->
                days.toString() to if (days == 0) label else "Not seen for $label"
            },
            onDismiss = { autoMenu = false },
            onPick = { value ->
                mark { container.machines.setAutoHideDays(value.toIntOrNull() ?: 0) }
                autoMenu = false
            },
        )
    }
    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${row.name}?") },
            text = { Text(MACHINE_DELETE_NOTE) },
            confirmButton = {
                TextButton(onClick = {
                    mark { container.machines.forget(row.key, row.name) }
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MachineSection(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MachineRowItem(
    row: MachineRow,
    now: Long,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null,
    note: String? = null,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
        Text(row.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            row.statusLine(now) + (note?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = if (row.online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = primary.second) { Text(primary.first) }
            secondary?.let { TextButton(onClick = it.second) { Text(it.first, color = MaterialTheme.colorScheme.error) } }
        }
        HorizontalDivider()
    }
}
