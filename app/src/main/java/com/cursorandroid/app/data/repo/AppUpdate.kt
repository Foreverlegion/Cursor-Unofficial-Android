package com.cursorandroid.app.data.repo

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build

object AppUpdate {
    data class Installed(
        val versionName: String,
        val versionCode: Long,
    )

    fun installed(context: Context): Installed {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return Installed(info.versionName.orEmpty(), info.longVersionCodeCompat())
    }
}

private fun PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= 28) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()
