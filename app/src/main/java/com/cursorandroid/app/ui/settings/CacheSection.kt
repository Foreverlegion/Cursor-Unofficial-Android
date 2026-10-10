package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.repo.CachePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun CacheSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableLongStateOf(-1L) }
    var cap by remember { mutableIntStateOf(container.cache.capMb) }
    var capMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    suspend fun measure() {
        bytes = withContext(Dispatchers.IO) { container.cache.sizeBytes() }
    }
    LaunchedEffect(Unit) { measure() }

    Column {
        SettingsLinkRow(
            title = "Clear cache",
            summary = if (bytes < 0) "Measuring..." else "${formatMb(bytes)} of $cap MB used",
            onClick = { confirm = true },
        )
        SettingsLinkRow(
            title = "Cache limit",
            summary = "$cap MB. Finished chats not opened in 30 days are dropped.",
            onClick = { capMenu = true },
        )
    }
    if (capMenu) {
        ChoiceDialog(
            title = "Cache limit",
            selected = cap.toString(),
            options = CachePolicy.CAP_CHOICES_MB.map { it.toString() to "$it MB" },
            onDismiss = { capMenu = false },
            onPick = { id ->
                capMenu = false
                container.cache.capMb = id.toIntOrNull() ?: CachePolicy.DEFAULT_CAP_MB
                cap = container.cache.capMb
                scope.launch {
                    withContext(Dispatchers.IO) { container.cache.prune() }
                    measure()
                }
            },
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear cache?") },
            text = {
                Text(
                    "Drops cached lists and finished chat transcripts. They reload from Cursor when opened. " +
                        "Running chats, favorites, drafts, queued messages, settings and sign-in are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        withContext(Dispatchers.IO) { container.cache.clear() }
                        measure()
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

private fun formatMb(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f MB", bytes / CachePolicy.MB.toDouble())
