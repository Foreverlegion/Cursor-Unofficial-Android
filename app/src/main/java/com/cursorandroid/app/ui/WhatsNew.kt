package com.cursorandroid.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object WhatsNewPolicy {
    /**
     * Show the popup once per versionCode after an update.
     * [seenCode] is -1 when nothing was recorded (fresh install, or an update from a build before the popup existed).
     * A fresh install ([freshInstall]) records the version without showing anything.
     */
    fun shouldShow(seenCode: Int, currentCode: Int, freshInstall: Boolean, notes: String): Boolean {
        if (notes.isBlank()) return false
        if (seenCode >= currentCode) return false
        if (seenCode < 0 && freshInstall) return false
        return true
    }

    /** First install and last update times match only on a fresh install that was never updated. */
    fun isFreshInstall(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)

    /** Turn "- item" markdown lines into bullets and strip backticks. */
    fun displayText(notes: String): String =
        notes.lines().joinToString("\n") { line ->
            val t = line.trimEnd()
            if (t.startsWith("- ")) "• " + t.removePrefix("- ") else t
        }.replace("`", "").trim()
}

@Composable
fun WhatsNewDialog(versionName: String, notes: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's new in $versionName") },
        text = {
            Text(
                WhatsNewPolicy.displayText(notes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("whats-new-body"),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("whats-new-ok")) { Text("Got it") }
        },
    )
}
