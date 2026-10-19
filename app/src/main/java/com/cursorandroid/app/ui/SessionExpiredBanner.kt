package com.cursorandroid.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val SESSION_EXPIRED_TITLE = "Your Cursor sign-in expired"
const val SESSION_EXPIRED_ACTION = "Sign in again"

/** Shown over every screen once the Cursor API refuses the saved key. Replaces the raw "Invalid User API Key" line. */
@Composable
fun SessionExpiredBanner(onSignInAgain: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth().testTag("session-expired-banner"),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                SESSION_EXPIRED_TITLE,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSignInAgain) { Text(SESSION_EXPIRED_ACTION) }
        }
    }
}
