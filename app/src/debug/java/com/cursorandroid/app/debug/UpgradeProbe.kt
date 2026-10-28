package com.cursorandroid.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cursorandroid.app.BuildConfig
import com.cursorandroid.app.CursorAndroidApp

/**
 * Debug-only hook for the upgrade check. Release builds do not include this receiver.
 * It prints the sign-in state and local folders, and can seed one folder, so an install
 * over an older build can show that the key and the folders are still there.
 */
class UpgradeProbe : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? CursorAndroidApp ?: return
        when (intent?.action) {
            SEED -> seed(app)
            else -> dump(app)
        }
    }

    private fun dump(app: CursorAndroidApp) {
        val store = app.container.store
        val folders = app.container.folders.snapshot()
        val names = folders.folders.joinToString(",") { it.name }
        Log.i(
            TAG,
            "hasKey=${store.hasKey()} key=${store.apiKey.orEmpty()} demo=${store.demoMode} " +
                "session=${store.hasSession()} version=${BuildConfig.VERSION_NAME} code=${BuildConfig.VERSION_CODE} " +
                "folders=$names groups=${folders.groups.size} chats=${folders.chats.size}",
        )
    }

    private fun seed(app: CursorAndroidApp) {
        val folders = app.container.folders
        val existing = folders.snapshot().folders.firstOrNull { it.name.equals(FOLDER, ignoreCase = true) }
        val folder = existing ?: folders.create(FOLDER)
        if (folder != null) {
            folders.moveGroup(GROUP, folder.id)
            folders.moveChats(listOf(CHAT), folder.id)
        }
        dump(app)
    }

    companion object {
        const val TAG = "UpgradeProbe"
        const val DUMP = "com.cursorandroid.app.PROBE_DUMP"
        const val SEED = "com.cursorandroid.app.PROBE_SEED_FOLDER"
        private const val FOLDER = "Thermal Nexus"
        private const val GROUP = "acme/thermal-nexus"
        private const val CHAT = "chat-thermal"
    }
}
