package com.cursorandroid.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSecretsTest {
    private val server = StoredMcpServer(
        id = "m1",
        name = "github",
        url = "https://api.githubcopilot.com/mcp/",
        headers = mapOf("Authorization" to "Bearer ghp_SECRET_HEADER"),
        auth = StoredMcpAuth(clientId = "cid", clientSecret = "SECRET_OAUTH"),
    )
    private val stdio = StoredMcpServer(
        id = "m2",
        name = "fs",
        type = TYPE_STDIO,
        command = "npx",
        env = mapOf("TOKEN" to "SECRET_ENV"),
    )
    private val full = SettingsSnapshot(
        apiKey = "key_SECRET_API",
        githubToken = "ghp_SECRET_LEGACY",
        forges = listOf(ForgeConnection(id = "f1", provider = ForgeKind.GITHUB.id, name = "GitHub", token = "ghp_SECRET_FORGE")),
        mcpServers = listOf(server, stdio),
        defaultModel = "sonnet",
    )
    private val pass = "correct horse".toCharArray()
    private val fast = 100_000

    private fun file(snap: SettingsSnapshot) = SettingsBackup.encode(snap)

    @Test
    fun defaultExportHasNoSecretsAnywhere() {
        val text = file(buildBackup(full, passphrase = null))
        assertFalse(text, text.contains("SECRET"))
        assertTrue(text.contains("sonnet"))
        val back = SettingsBackup.parse(text)!!
        assertNull(back.apiKey)
        assertEquals("", back.forges.single().token)
        assertEquals("", back.mcpServers.first { it.id == "m1" }.headers["Authorization"])
        assertEquals("", back.mcpServers.first { it.id == "m2" }.env["TOKEN"])
        assertEquals("cid", back.mcpServers.first { it.id == "m1" }.auth?.clientId)
        assertNull(back.secrets)
    }

    @Test
    fun sealedExportHidesSecretsAndRoundTripsWithThePassphrase() {
        val text = file(buildBackup(full, pass, fast))
        assertFalse(text, text.contains("SECRET"))
        assertTrue(text.contains(SecretsVault.FORMAT))
        val loaded = SettingsBackup.parse(text)!!
        assertNotNull(loaded.secrets)

        val opened = openBackup(loaded, pass)
        assertEquals(BackupOpen.Ok, opened.result)
        val snap = opened.snapshot
        assertEquals("key_SECRET_API", snap.apiKey)
        assertEquals("ghp_SECRET_LEGACY", snap.githubToken)
        assertEquals("ghp_SECRET_FORGE", snap.forges.single().token)
        assertEquals("Bearer ghp_SECRET_HEADER", snap.mcpServers.first { it.id == "m1" }.headers["Authorization"])
        assertEquals("SECRET_OAUTH", snap.mcpServers.first { it.id == "m1" }.auth?.clientSecret)
        assertEquals("SECRET_ENV", snap.mcpServers.first { it.id == "m2" }.env["TOKEN"])
        assertEquals("sonnet", snap.defaultModel)
        assertNull(snap.secrets)
    }

    @Test
    fun wrongPassphraseAppliesNothing() {
        val loaded = SettingsBackup.parse(file(buildBackup(full, pass, fast)))!!
        val opened = openBackup(loaded, "not it at all".toCharArray())
        assertEquals(BackupOpen.WrongPassphrase, opened.result)
        assertNull(opened.snapshot.apiKey)
    }

    @Test
    fun importWithoutAPassphraseDropsTheSecretsOnly() {
        val loaded = SettingsBackup.parse(file(buildBackup(full, pass, fast)))!!
        val opened = openBackup(loaded, null)
        assertEquals(BackupOpen.Ok, opened.result)
        assertNull(opened.snapshot.apiKey)
        assertEquals("sonnet", opened.snapshot.defaultModel)
        assertNull(opened.snapshot.secrets)
    }

    @Test
    fun aTamperedFileIsRejected() {
        val sealed = buildBackup(full, pass, fast).secrets!!
        assertNull(SecretsVault.open(sealed.copy(iterations = sealed.iterations + 1), pass))
        val flipped = sealed.data.toCharArray().also { it[4] = if (it[4] == 'A') 'B' else 'A' }.concatToString()
        assertNull(SecretsVault.open(sealed.copy(data = flipped), pass))
        assertNull(SecretsVault.open(sealed.copy(iterations = 10), pass))
        assertNull(SecretsVault.open(sealed.copy(format = "other"), pass))
        assertNull(SecretsVault.open(sealed.copy(salt = "!!!"), pass))
    }

    @Test
    fun saltAndIvDifferEveryTime() {
        val a = SecretsVault.seal("x".toByteArray(), pass, fast)
        val b = SecretsVault.seal("x".toByteArray(), pass, fast)
        assertFalse(a.salt == b.salt)
        assertFalse(a.iv == b.iv)
        assertFalse(a.data == b.data)
        assertEquals("x", SecretsVault.open(a, pass)?.decodeToString())
    }

    @Test
    fun anOldPlainFileStillImportsItsSecrets() {
        val old = SettingsBackup.parse(file(full))!!
        val opened = openBackup(old, null)
        assertEquals("key_SECRET_API", opened.snapshot.apiKey)
        assertEquals("ghp_SECRET_FORGE", opened.snapshot.forges.single().token)
    }

    @Test
    fun secretlessImportKeepsTokensAlreadySavedOnThisPhone() {
        val savedForges = full.forges
        val fromFile = buildBackup(full.copy(forges = full.forges.map { it.copy(name = "Renamed") }), null).forges
        val merged = mergeForgeSecrets(savedForges, fromFile).single()
        assertEquals("Renamed", merged.name)
        assertEquals("ghp_SECRET_FORGE", merged.token)

        val fileMcp = buildBackup(full, null).mcpServers.map { it.copy(url = if (it.id == "m1") "https://new.example/mcp" else it.url) }
        val mcp = mergeMcpSecrets(full.mcpServers, fileMcp)
        val github = mcp.first { it.id == "m1" }
        assertEquals("https://new.example/mcp", github.url)
        assertEquals("Bearer ghp_SECRET_HEADER", github.headers["Authorization"])
        assertEquals("SECRET_OAUTH", github.auth?.clientSecret)
        assertEquals("SECRET_ENV", mcp.first { it.id == "m2" }.env["TOKEN"])
    }

    @Test
    fun secretlessImportMatchesByNameWhenIdsDiffer() {
        val fileMcp = buildBackup(full, null).mcpServers.map { it.copy(id = "other-" + it.id) }
        val merged = mergeMcpSecrets(full.mcpServers, fileMcp)
        assertEquals("Bearer ghp_SECRET_HEADER", merged.first { it.name == "github" }.headers["Authorization"])
    }
}
