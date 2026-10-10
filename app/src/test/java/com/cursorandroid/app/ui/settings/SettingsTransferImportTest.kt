package com.cursorandroid.app.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.AppContainer
import com.cursorandroid.app.data.repo.ForgeConnection
import com.cursorandroid.app.data.repo.SecretsVault
import com.cursorandroid.app.data.repo.SettingsBackup
import com.cursorandroid.app.data.repo.SettingsSnapshot
import com.cursorandroid.app.data.repo.StoredMcpAuth
import com.cursorandroid.app.data.repo.StoredMcpServer
import com.cursorandroid.app.data.repo.buildBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Drives the real Import / Restore row through a fake file picker. A self-call in the import path once
 * recursed until the main thread died with a StackOverflowError.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsTransferImportTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var container: AppContainer
    private lateinit var backup: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        container = AppContainer(context)
        container.store.demoMode = false
        container.store.apiKey = "key_CURRENT_SESSION"
        container.store.defaultModel = ""
        container.stagedRestore.discard()
        backup = File.createTempFile("backup", ".json").apply {
            writeText("""{"version":1,"apiKey":"key_OLD_REVOKED","defaultModel":"sonnet"}""")
        }
    }

    private fun picker(file: File) = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                dispatchResult(requestCode, android.app.Activity.RESULT_OK, android.content.Intent().setData(Uri.fromFile(file)))
            }
        }
    }

    private fun show(stageOnly: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker(backup)) {
                MaterialTheme {
                    SettingsTransfer(container = container, allowExport = false, stageOnly = stageOnly)
                }
            }
        }
    }

    @Test
    fun importingFromSettingsAppliesTheFileWithoutRecursingAndKeepsTheSessionKey() {
        show(stageOnly = false)
        compose.onNodeWithTag("restore-settings").performClick()
        compose.waitUntil(5_000) { container.store.defaultModel == "sonnet" }
        assertEquals("sonnet", container.store.defaultModel)
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
    }

    @Test
    fun restoringFromTheSignInScreenOnlyStagesTheFile() {
        container.store.apiKey = null
        show(stageOnly = true)
        compose.onNodeWithTag("restore-settings").performClick()
        compose.waitUntil(5_000) { container.stagedRestore.pending() }
        assertTrue(container.stagedRestore.pending())
        assertEquals("nothing is applied before sign-in", "", container.store.defaultModel)
        assertFalse(container.store.hasKey())
    }

    private val pass = "test-passphrase-1"
    private val kdfRounds = 100_000

    private val forge = ForgeConnection(id = "f1", provider = "github", name = "Work", baseUrl = "https://github.com", token = "")
    private val mcp = StoredMcpServer(
        id = "m1",
        name = "docs",
        url = "https://mcp.example.test/sse",
        headers = mapOf("Authorization" to ""),
        auth = StoredMcpAuth(clientId = "cid", clientSecret = ""),
    )

    /** A file as an older build wrote it: secrets sealed, and the sealed block carries the sign-in key. */
    private fun olderSealedFile(): File {
        val body = """{"apiKey":"key_OLD_REVOKED","githubToken":"ghp_old_token","forgeTokens":{"f1":"forge_token_old"},""" +
            """"mcp":{"m1":{"headers":{"Authorization":"Bearer mcp_old"},"env":{},"clientSecret":"mcp_client_old"}}}"""
        val sealed = SecretsVault.seal(body.toByteArray(Charsets.UTF_8), pass.toCharArray(), kdfRounds)
        val outer = SettingsSnapshot(
            version = 1,
            apiKey = null,
            defaultModel = "sonnet",
            forges = listOf(forge),
            mcpServers = listOf(mcp),
            secrets = sealed,
        )
        return File.createTempFile("older-sealed", ".json").apply { writeText(SettingsBackup.encode(outer)) }
    }

    private fun currentSealedFile(): File {
        val full = SettingsSnapshot(
            defaultModel = "sonnet",
            githubToken = "ghp_cur_token",
            forges = listOf(forge.copy(token = "forge_token_cur")),
            mcpServers = listOf(
                mcp.copy(
                    headers = mapOf("Authorization" to "Bearer mcp_cur"),
                    auth = StoredMcpAuth(clientId = "cid", clientSecret = "mcp_client_cur"),
                ),
            ),
        )
        return File.createTempFile("current-sealed", ".json").apply {
            writeText(SettingsBackup.encode(buildBackup(full, pass.toCharArray(), kdfRounds)))
        }
    }

    private fun importThroughDialog(file: File, passphrase: String) {
        backup = file
        show(stageOnly = false)
        compose.onNodeWithTag("restore-settings").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput(passphrase)
        compose.onNodeWithText("Import").performClick()
    }

    private fun assertSecretsRestored(forgeToken: String, header: String, clientSecret: String) {
        compose.waitUntil(30_000) { container.store.forges().singleOrNull()?.token == forgeToken }
        assertEquals(forgeToken, container.store.forges().single().token)
        assertEquals("github token is the GitHub forge's token", forgeToken, container.store.githubToken)
        val server = container.store.storedMcps().single()
        assertEquals(header, server.headers["Authorization"])
        assertEquals(clientSecret, server.auth?.clientSecret)
        assertEquals("sonnet", container.store.defaultModel)
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
    }

    @Test
    fun olderEncryptedBackupWithSignInKeyInSealedBlockRestoresForgeTokensAndDropsTheKey() {
        importThroughDialog(olderSealedFile(), pass)
        assertSecretsRestored("forge_token_old", "Bearer mcp_old", "mcp_client_old")
    }

    @Test
    fun currentEncryptedBackupRestoresForgeTokens() {
        importThroughDialog(currentSealedFile(), pass)
        assertSecretsRestored("forge_token_cur", "Bearer mcp_cur", "mcp_client_cur")
    }

    @Test
    fun wrongPassphraseAppliesNothing() {
        importThroughDialog(currentSealedFile(), "not-the-passphrase")
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Wrong passphrase").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(container.store.forges().isEmpty())
        assertEquals("", container.store.defaultModel)
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
    }

    @Test
    fun withoutSecretsAppliesSettingsAndLeavesTokensEmpty() {
        backup = currentSealedFile()
        show(stageOnly = false)
        compose.onNodeWithTag("restore-settings").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Without secrets").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Without secrets").performClick()
        compose.waitUntil(30_000) { container.store.defaultModel == "sonnet" && container.store.forges().isNotEmpty() }
        assertEquals("", container.store.forges().single().token)
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
    }

    @Test
    fun currentPlainBackupApplies() {
        backup = File.createTempFile("current-plain", ".json").apply {
            writeText(SettingsBackup.encode(buildBackup(SettingsSnapshot(defaultModel = "opus", forges = listOf(forge)), null)))
        }
        show(stageOnly = false)
        compose.onNodeWithTag("restore-settings").performClick()
        compose.waitUntil(5_000) { container.store.defaultModel == "opus" }
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
    }

    /** Sanitized copies of two real exports (structure only). Their sealed block is rebuilt here with a test passphrase. */
    private fun fixtureSealedFile(name: String): Pair<File, SettingsSnapshot> {
        val text = checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }.readText()
        val shape = checkNotNull(SettingsBackup.parse(text)) { "fixture $name does not parse" }
        val forgeTokens = shape.forges.associate { it.id to "forge_token_${it.provider}" }
        val mcp = shape.mcpServers.filter { it.headers.isNotEmpty() }
            .joinToString(",") { """"${it.id}":{"headers":{"Authorization":"Bearer mcp_fixture"},"env":{},"clientSecret":""}""" }
        val body = """{"apiKey":"key_OLD_REVOKED","githubToken":null,"forgeTokens":{${
            forgeTokens.entries.joinToString(",") { """"${it.key}":"${it.value}"""" }
        }},"mcp":{$mcp}}"""
        val sealed = SecretsVault.seal(body.toByteArray(Charsets.UTF_8), pass.toCharArray())
        val file = File.createTempFile("fixture", ".json").apply { writeText(SettingsBackup.encode(shape.copy(secrets = sealed))) }
        return file to shape
    }

    private fun assertFixtureRestored(shape: SettingsSnapshot) {
        compose.waitUntil(60_000) { container.store.forges().all { it.token.isNotBlank() } && container.store.forges().size == shape.forges.size }
        container.store.forges().forEach { assertEquals("forge_token_${it.provider}", it.token) }
        val withHeader = shape.mcpServers.first { it.headers.isNotEmpty() }.id
        assertEquals("Bearer mcp_fixture", container.store.storedMcps().first { it.id == withHeader }.headers["Authorization"])
        assertEquals(shape.mcpServers.size, container.store.storedMcps().size)
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
        assertEquals(shape.conversations.isNotEmpty(), container.conversations.exportAll().isNotEmpty())
    }

    @Test
    fun smallOlderExportShapeRestoresForgeTokens() {
        val (file, shape) = fixtureSealedFile("op-backup-small.json")
        importThroughDialog(file, pass)
        assertFixtureRestored(shape)
    }

    @Test
    fun largeOlderExportShapeWithChatsRestoresForgeTokens() {
        val (file, shape) = fixtureSealedFile("op-backup-large.json")
        assertTrue(shape.conversations.values.sumOf { it.size } > 1000)
        importThroughDialog(file, pass)
        assertFixtureRestored(shape)
    }

    @Test
    fun fixtureShapesImportWithoutSecretsAndKeepTokensBlank() {
        val (file, shape) = fixtureSealedFile("op-backup-large.json")
        backup = file
        show(stageOnly = false)
        compose.onNodeWithTag("restore-settings").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Without secrets").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Without secrets").performClick()
        compose.waitUntil(60_000) {
            container.store.storedMcps().size == shape.mcpServers.size && container.store.forges().size == shape.forges.size
        }
        assertTrue(container.store.forges().all { it.token.isBlank() })
        assertEquals("key_CURRENT_SESSION", container.store.apiKey)
    }
}
