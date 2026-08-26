package com.cursorandroid.app.data.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cursorandroid.app.CursorAndroidApp

class NoticeDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val id = intent.getStringExtra(EXTRA_ID)?.takeIf { it.isNotBlank() } ?: return
        val app = context.applicationContext as? CursorAndroidApp ?: return
        app.container.notices.dismiss(id, cancelShade = false)
        app.container.notifier.rememberDismissed(id)
    }

    companion object {
        const val ACTION = "com.cursorandroid.app.NOTICE_DISMISS"
        const val EXTRA_ID = "notice_id"
    }
}
