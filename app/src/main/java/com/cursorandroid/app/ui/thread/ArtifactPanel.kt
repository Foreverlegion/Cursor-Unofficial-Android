package com.cursorandroid.app.ui.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.api.ArtifactItem

@Composable
fun LatestArtifactCard(
    item: ArtifactItem,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            "Artifact",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        ArtifactRow(item = item, onOpen = onOpen, onSave = onSave)
    }
}

@Composable
fun ArtifactHistoryDialog(
    items: List<ArtifactItem>,
    onOpen: (ArtifactItem) -> Unit,
    onSave: (ArtifactItem) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artifacts history") },
        text = {
            if (items.isEmpty()) {
                Text("None saved in the last few days.")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { item ->
                        ArtifactRow(
                            item = item,
                            onOpen = { onOpen(item) },
                            onSave = { onSave(item) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun ArtifactRow(
    item: ArtifactItem,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.fileName(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(end = 8.dp),
        )
        TextButton(onClick = onSave) { Text("Save") }
    }
}
