package com.cursorandroid.app.data.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat

object NotifyShade {
    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, notification: Notification) {
        if (!NotifyPermission.granted(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    @SuppressLint("MissingPermission")
    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }
}
