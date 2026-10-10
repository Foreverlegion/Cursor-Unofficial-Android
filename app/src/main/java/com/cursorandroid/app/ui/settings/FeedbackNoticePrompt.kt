package com.cursorandroid.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cursorandroid.app.data.repo.FeedbackPolicy

@Composable
fun FeedbackNoticePrompt(
    onContinue: () -> Unit,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    PromptScaffold {
        Text(
            "Before you start",
            style = MaterialTheme.typography.headlineMedium,
            color = onBackground,
        )
        Text(
            FeedbackPolicy.NOTICE,
            style = MaterialTheme.typography.bodyLarge,
            color = onBackground,
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}
