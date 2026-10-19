package com.cursorandroid.app.ui.settings

import android.app.Application
import android.content.Context
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.repo.BackupOpen
import com.cursorandroid.app.data.repo.BackupSecrets
import com.cursorandroid.app.data.repo.ForgeConnection
import com.cursorandroid.app.data.repo.ForgeKind
import com.cursorandroid.app.data.repo.SecretsVault
import com.cursorandroid.app.data.repo.SettingsBackup
import com.cursorandroid.app.data.repo.SettingsSnapshot
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsTransferRestoreTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer
    private val pass = "correct horse".toCharArray()
    private val forge = ForgeConnection(
        id = "f1",
        provider = ForgeKind.GITHUB.id,
        name = "GitHub",
        token = "",
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = AppContainer(context)
        container.store.demoMode = false
        container.store.apiKey = "key_CURRENT_SESSION"
        container.store.defaultModel = "opus"
        container.stagedRestore.discard()
    }

    private fun oldSealedBackup(): SettingsSnapshot {
        val json = Json { encodeDefaults = true }
        val secrets = BackupSecrets(
            apiKey = "key_OLD_SEALED",
            forgeTokens = mapOf("f1" to "ghp_FORGE_TOKEN"),
        )
        val sealed = SecretsVault.seal(json.encodeToString(secrets).toByteArray(), pass, 100_000)
        return SettingsSnapshot(
            defaultModel = "sonnet",
            notifyOnComplete = false,
            forges = listOf(forge),
            secrets = sealed,
        )
    }

    @Test
    fun strippingAnOldSignInKeyDuringApplyDoesNotRecurse() {
        val loaded = oldSealedBackup()
        assertEquals(BackupOpen.Ok, SettingsBackup.apply(container, loaded, pass))
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
        assertEquals("sonnet", container.store.defaultModel)
        assertFalse(container.store.notifyOnComplete)
        assertEquals("ghp_FORGE_TOKEN", container.store.forges().single().token)
        assertFalse(container.stagedRestore.pending())
    }

    @Test
    fun stagingABackupOnTheSignInScreenDoesNotRecurse() {
        val loaded = oldSealedBackup()
        val result = invokeCommit(stageOnly = true, loaded = loaded, passphrase = pass)
        assertEquals(BackupOpen.Ok, result)
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
        assertEquals("opus", container.store.defaultModel)
        assertTrue(container.stagedRestore.pending())
        assertEquals("", container.store.forges().singleOrNull()?.token.orEmpty())
    }

    @Test
    fun importingEncryptedBackupAfterPassphraseKeepsTheCurrentSignInKey() {
        val loaded = oldSealedBackup()
        val result = invokeCommit(stageOnly = false, loaded = loaded, passphrase = pass)
        assertEquals(BackupOpen.Ok, result)
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
        assertEquals("sonnet", container.store.defaultModel)
        assertEquals("ghp_FORGE_TOKEN", container.store.forges().single().token)
        assertFalse(container.stagedRestore.pending())
    }

    private fun invokeCommit(stageOnly: Boolean, loaded: SettingsSnapshot, passphrase: CharArray?): BackupOpen =
        SettingsBackup.restoreOrStage(container, loaded, passphrase, stage = stageOnly)
}
