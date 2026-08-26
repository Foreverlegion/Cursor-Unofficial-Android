package com.cursorandroid.app.data.repo

import android.content.Context
import android.content.Intent

object ChatShare {
    fun url(agentId: String, remote: String?): String {
        return SafeLinks.httpsUri(remote)?.toString() ?: "https://cursor.com/agents/$agentId"
    }

    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    fun send(context: Context, title: String, agentId: String, remote: String?) {
        val link = url(agentId, remote)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$link")
        }
        val chooser = Intent.createChooser(send, "Share chat").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (chooser.resolveActivity(context.packageManager) == null) return
        context.startActivity(chooser)
    }
}
