package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cursorandroid.app.data.notify.BatteryPromptPolicy

@Composable
fun BatteryPrompt(
    onAllow: () -> Unit,
    onSkip: () -> Unit,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    PromptScaffold {
        Text(
            BatteryPromptPolicy.TITLE,
            style = MaterialTheme.typography.headlineMedium,
            color = onBackground,
        )
        Text(
            BatteryPromptPolicy.BODY,
            style = MaterialTheme.typography.bodyLarge,
            color = onBackground,
        )
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
            Text(BatteryPromptPolicy.ALLOW)
        }
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text(BatteryPromptPolicy.SKIP)
        }
    }
}
