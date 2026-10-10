package com.cursorandroid.app.data.repo

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.AppContainer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SignInKeyBackupTest {
    private lateinit var context: Context
    private lateinit var container: AppContainer
    private val pass = "correct horse".toCharArray()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = AppContainer(context)
        container.store.demoMode = false
        container.store.apiKey = null
        container.stagedRestore.discard()
    }

    private val forge = ForgeConnection(id = "f1", provider = ForgeKind.GITHUB.id, name = "GitHub", token = "ghp_FORGE_TOKEN")

    private fun oldBackupJson(): String =
        """{"version":1,"apiKey":"key_OLD_REVOKED","defaultModel":"sonnet","notifyOnComplete":false}"""

    private fun exportTo(passphrase: CharArray?): String {
        val file = File.createTempFile("backup", ".json")
        SettingsBackup.export(context, container, Uri.fromFile(file), passphrase)
        return file.readText().also { file.delete() }
    }

    @Test
    fun aPlainExportNeverContainsTheSignInKey() {
        container.store.apiKey = "key_CURRENT_SESSION"
        container.store.saveForges(listOf(forge))
        val text = exportTo(null)
        assertFalse(text, text.contains("key_CURRENT_SESSION"))
        assertFalse(text, text.contains("ghp_FORGE_TOKEN"))
        assertNull(SettingsBackup.parse(text)!!.apiKey)
    }

    @Test
    fun anEncryptedExportNeverContainsTheSignInKeyEvenInsideTheSealedBlock() {
        container.store.apiKey = "key_CURRENT_SESSION"
        container.store.saveForges(listOf(forge))
        val text = exportTo(pass)
        assertFalse(text, text.contains("key_CURRENT_SESSION"))
        val loaded = SettingsBackup.parse(text)!!
        val body = SecretsVault.open(loaded.secrets!!, pass)!!.toString(Charsets.UTF_8)
        assertFalse(body, body.contains("key_CURRENT_SESSION"))
        assertTrue("forge tokens still travel in the encrypted block", body.contains("ghp_FORGE_TOKEN"))
        assertEquals("ghp_FORGE_TOKEN", openBackup(loaded, pass).snapshot.forges.single().token)
    }

    @Test
    fun restoringABackupThatHoldsAnOldKeyLeavesTheCurrentKeyAlone() {
        container.store.apiKey = "key_CURRENT_SESSION"
        val loaded = SettingsBackup.parse(oldBackupJson())!!
        assertNull(loaded.apiKey)
        assertEquals(BackupOpen.Ok, SettingsBackup.apply(container, loaded, null))
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
        assertEquals("sonnet", container.store.defaultModel)
        assertFalse(container.store.notifyOnComplete)
    }

    @Test
    fun restoringIntoASignedOutAppDoesNotSignItIn() {
        val loaded = SettingsBackup.parse(oldBackupJson())!!
        SettingsBackup.apply(container, loaded, null)
        assertFalse(container.store.hasKey())
    }

    @Test
    fun anOldSealedBlockWithAnApiKeyIsIgnoredButItsOtherSecretsApply() {
        container.store.apiKey = "key_CURRENT_SESSION"
        val json = Json { encodeDefaults = true }
        val secrets = BackupSecrets(apiKey = "key_OLD_SEALED", forgeTokens = mapOf("f1" to "ghp_FORGE_TOKEN"))
        val sealed = SecretsVault.seal(json.encodeToString(secrets).toByteArray(), pass, 100_000)
        val snap = SettingsSnapshot(forges = listOf(forge.copy(token = "")), secrets = sealed)
        assertEquals(BackupOpen.Ok, SettingsBackup.apply(container, snap, pass))
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
        assertEquals("ghp_FORGE_TOKEN", container.store.forges().single().token)
    }

    @Test
    fun anOldPlainFileWithTheKeyInsideItsSnapshotStillCannotOverwriteTheKey() {
        container.store.apiKey = "key_CURRENT_SESSION"
        val direct = SettingsSnapshot(apiKey = "key_OLD_REVOKED", defaultModel = "opus")
        SettingsBackup.apply(container, direct, null)
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
        assertEquals("opus", container.store.defaultModel)
    }

    @Test
    fun aStagedBackupChangesNothingUntilSignInThenAppliesWithTheNewSessionKey() {
        val loaded = SettingsBackup.parse(oldBackupJson())!!
        container.stagedRestore.stage(openBackup(loaded, null).snapshot)
        assertTrue(container.stagedRestore.pending())
        assertEquals("", container.store.defaultModel)
        assertFalse(container.store.hasKey())

        container.store.apiKey = "key_NEW_SESSION"
        assertTrue(SettingsBackup.applyStaged(container))

        assertEquals("sonnet", container.store.defaultModel)
        assertFalse(container.store.notifyOnComplete)
        assertEquals("key_NEW_SESSION", container.store.apiKey)
        assertFalse(container.stagedRestore.pending())
        assertFalse("applies once", SettingsBackup.applyStaged(container))
    }

    @Test
    fun aStagedBackupWithUnlockedSecretsAppliesThemAfterSignIn() {
        val snap = SettingsSnapshot(defaultModel = "gpt", forges = listOf(forge))
        container.stagedRestore.stage(snap)
        container.store.apiKey = "key_NEW_SESSION"
        SettingsBackup.applyStaged(container)
        assertEquals("ghp_FORGE_TOKEN", container.store.forges().single().token)
        assertEquals("key_NEW_SESSION", container.store.apiKey)
    }

    @Test
    fun aStagedBackupSurvivesTheAppBeingKilledButLosesItsSecretsOnDisk() {
        val file = File(context.filesDir, "staged-test.json")
        file.delete()
        StagedRestore(file).stage(SettingsSnapshot(defaultModel = "gpt", forges = listOf(forge)))
        val onDisk = file.readText()
        assertFalse(onDisk, onDisk.contains("ghp_FORGE_TOKEN"))
        val reborn = StagedRestore(file)
        assertTrue(reborn.pending())
        val taken = reborn.take()
        assertNotNull(taken)
        assertEquals("gpt", taken!!.defaultModel)
        assertEquals("", taken.forges.single().token)
        assertFalse(file.exists())
    }

    @Test
    fun discardingAStagedBackupAppliesNothing() {
        container.stagedRestore.stage(SettingsSnapshot(defaultModel = "gpt"))
        container.stagedRestore.discard()
        container.store.apiKey = "key_NEW_SESSION"
        assertFalse(SettingsBackup.applyStaged(container))
        assertEquals("", container.store.defaultModel)
    }
}
