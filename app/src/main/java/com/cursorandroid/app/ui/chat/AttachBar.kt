package com.cursorandroid.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.repo.AttachItem
import com.cursorandroid.app.data.repo.Attachments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AttachButton(
    items: List<AttachItem>,
    onItems: (List<AttachItem>) -> Unit,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val photos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Attachments.MAX_IMAGES),
    ) { uris ->
        add(context, uris, items, onItems, scope)
    }
    val files = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        add(context, uris, items, onItems, scope)
    }
    IconButton(onClick = { menu = true }, enabled = enabled) {
        Icon(Icons.Outlined.Add, contentDescription = "Attach")
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(
            text = { Text("Photo") },
            onClick = {
                menu = false
                photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
        DropdownMenuItem(
            text = { Text("File") },
            onClick = {
                menu = false
                files.launch(arrayOf("*/*"))
            },
        )
    }
}

@Composable
fun VoiceButton(
    enabled: Boolean = true,
    onText: (String) -> Unit,
) {
    val speech = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        if (!spoken.isNullOrBlank()) onText(spoken)
    }
    IconButton(
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak")
            }
            runCatching { speech.launch(intent) }
        },
        enabled = enabled,
    ) {
        Icon(Icons.Outlined.Mic, contentDescription = "Voice")
    }
}

@Composable
fun AttachChips(
    items: List<AttachItem>,
    onItems: (List<AttachItem>) -> Unit,
) {
    if (items.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (item.ok) item.name else item.error ?: item.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.ok) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                IconButton(onClick = { onItems(items.filter { it.id != item.id }) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Remove")
                }
            }
        }
    }
}

private fun add(
    context: android.content.Context,
    uris: List<Uri>,
    current: List<AttachItem>,
    onItems: (List<AttachItem>) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (uris.isEmpty()) return
    scope.launch {
        val loaded = withContext(Dispatchers.IO) {
            Attachments.read(context, uris, alreadyImages = current.count { it.isImage })
        }
        onItems(current + loaded)
    }
}
