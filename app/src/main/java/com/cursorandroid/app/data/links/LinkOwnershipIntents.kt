package com.cursorandroid.app.data.links

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object LinkOwnershipIntents {
    fun openGrokOpenByDefault(context: Context) {
        openAppOpenByDefault(context, LinkOwnershipProbe.GROK_PACKAGE)
    }

    fun openOwnOpenByDefault(context: Context) {
        openAppOpenByDefault(context, context.packageName)
    }

    private fun openAppOpenByDefault(context: Context, packageName: String) {
        val uri = Uri.parse("package:$packageName")
        val openByDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, uri)
        } else {
            null
        }
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        val primary = (openByDefault ?: details).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(primary) }.isSuccess) return
        runCatching { context.startActivity(details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
