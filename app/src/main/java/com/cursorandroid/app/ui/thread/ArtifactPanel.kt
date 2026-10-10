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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.api.ArtifactItem
import com.cursorandroid.app.data.repo.ArtifactHistoryLogic
import com.cursorandroid.app.data.repo.ArtifactTime

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
                val ordered = items.sortedWith(
                    compareByDescending { ArtifactHistoryLogic.sortMillis(it.whenIso()) },
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ordered.forEachIndexed { index, item ->
                        ArtifactHistoryRow(
                            item = item,
                            onOpen = { onOpen(item) },
                            onSave = { onSave(item) },
                        )
                        if (index < ordered.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

internal const val MAX_HISTORY_NAME = 120

internal fun middleEllipsize(name: String, max: Int = MAX_HISTORY_NAME): String {
    if (name.length <= max || max < 8) return name
    val keep = max - 1
    val tail = keep / 2
    val head = keep - tail
    return name.take(head) + "…" + name.takeLast(tail)
}

@Composable
private fun ArtifactHistoryRow(
    item: ArtifactItem,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            middleEllipsize(item.fileName()),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                ArtifactTime.format(item.whenIso()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onSave) { Text("Save") }
        }
    }
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
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                item.fileName(),
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val whenLabel = ArtifactTime.format(item.whenIso())
            if (whenLabel.isNotEmpty()) {
                Text(
                    whenLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        TextButton(onClick = onSave) { Text("Save") }
    }
}
