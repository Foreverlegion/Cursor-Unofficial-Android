package com.cursorandroid.app.data.notify

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object PromotedNotificationSettings {
    const val MIN_API = 36

    fun settingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < MIN_API) return null
        return Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}
