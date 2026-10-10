package com.cursorandroid.app.data.repo

import java.io.File

/**
 * A backup picked on the sign-in screen, waiting for sign-in to finish. The full snapshot (secrets included
 * when the user unlocked them) lives in memory only. A copy without secrets is kept on disk so the choice
 * survives the app being killed while the browser is open for sign-in.
 */
class StagedRestore(private val file: File) {
    @Volatile
    private var memory: SettingsSnapshot? = null

    fun stage(snapshot: SettingsSnapshot) {
        val clean = snapshot.copy(apiKey = null)
        memory = clean
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(SettingsBackup.encode(clean.withoutSecrets()), Charsets.UTF_8)
        }
    }

    fun pending(): Boolean = memory != null || file.exists()

    /** Returns the staged backup and forgets it. A snapshot read back from disk has no secrets. */
    fun take(): SettingsSnapshot? {
        val snapshot = memory ?: runCatching { SettingsBackup.parse(file.readText(Charsets.UTF_8)) }.getOrNull()
        memory = null
        file.delete()
        return snapshot
    }

    fun discard() {
        memory = null
        file.delete()
    }
}
