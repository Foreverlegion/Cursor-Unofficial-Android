package com.cursorandroid.app.data.repo

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object ArtifactSaver {
    fun enqueue(context: Context, url: String, name: String) {
        val https = SafeLinks.httpsUri(url) ?: return
        val safe = fileName(name)
        val request = DownloadManager.Request(Uri.parse(https.toString()))
            .setTitle(safe)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safe)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
        context.getSystemService(DownloadManager::class.java).enqueue(request)
    }

    // Artifact paths are server-supplied; keep only the last segment so the file stays in Downloads.
    fun fileName(name: String): String {
        val leaf = name.substringAfterLast('/').substringAfterLast('\\')
        val clean = leaf.filter { it >= ' ' && it !in RESERVED }.trim().trimStart('.')
        return clean.take(MAX_NAME).ifBlank { "artifact" }
    }

    private const val RESERVED = "<>:\"|?*"
    private const val MAX_NAME = 120
}
