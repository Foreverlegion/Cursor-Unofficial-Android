package com.cursorandroid.app.data.repo

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

object ArtifactSaver {
    fun enqueue(context: Context, url: String, name: String) {
        val https = SafeLinks.httpsUri(url) ?: return
        val safe = name.ifBlank { "artifact" }.replace('/', '_')
        val request = DownloadManager.Request(Uri.parse(https.toString()))
            .setTitle(safe)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safe)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
        context.getSystemService(DownloadManager::class.java).enqueue(request)
    }
}
