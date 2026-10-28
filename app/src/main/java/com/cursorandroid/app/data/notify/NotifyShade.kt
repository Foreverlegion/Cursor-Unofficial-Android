package com.cursorandroid.app.data.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat

object NotifyShade {
    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, notification: Notification, tag: String? = null) {
        if (!NotifyPermission.granted(context)) return
        val manager = NotificationManagerCompat.from(context)
        runCatching {
            if (tag.isNullOrBlank()) {
                manager.notify(id, notification)
            } else {
                manager.notify(tag, id, notification)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun cancel(context: Context, id: Int, tag: String? = null) {
        val manager = NotificationManagerCompat.from(context)
        runCatching {
            if (tag.isNullOrBlank()) {
                manager.cancel(id)
            } else {
                manager.cancel(tag, id)
            }
        }
    }
}
