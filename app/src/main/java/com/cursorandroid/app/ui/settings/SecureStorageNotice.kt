package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cursorandroid.app.data.auth.ApiKeyStore

const val SECURE_STORAGE_WARNING =
    "Secure storage is unavailable on this phone. The API key, tokens, and MCP secrets are kept in memory only " +
        "and are lost when the app closes. Background alerts cannot sign in without them."

@Composable
fun SecureStorageNotice(store: ApiKeyStore, modifier: Modifier = Modifier) {
    if (store.secureStorage) return
    Text(
        SECURE_STORAGE_WARNING,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
