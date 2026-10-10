package com.cursorandroid.app.data.auth

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.repo.ForgeKind
import com.cursorandroid.app.data.repo.LocalChatStore
import com.cursorandroid.app.data.repo.UiPrefsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UpgradeMigrationTest {
    private lateinit var context: Context
    private val ui = mutableListOf<UiPrefsStore>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf("cursor_secure", "cursor_secure_bak", "cursor_secure_fallback", "cursor_prefs", "local_chats")
            .forEach { context.deleteSharedPreferences(it) }
    }

    @After
    fun tearDown() {
        ui.forEach { runCatching { it.close() } }
    }

    // Robolectric has no AndroidKeyStore, so the two "encrypted" files are plain prefs here. The
    // migration reads and writes through the same store API either way; Tink itself is not exercised.
    private fun encrypted(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun seedOldSecrets(key: String? = "key-old", github: String? = "ghp_old") {
        listOf("cursor_secure", "cursor_secure_bak").forEach { name ->
            encrypted(name).edit(commit = true) {
                key?.let { putString("api_key", it) }
                github?.let { putString("github_token", it) }
            }
        }
    }

    private fun open(secure: Boolean = true): ApiKeyStore {
        ui.forEach { it.close() }
        ui.clear()
        val prefs = UiPrefsStore.open(context).also { ui += it }
        return ApiKeyStore(context, prefs) { c, name -> if (secure) c.getSharedPreferences(name, Context.MODE_PRIVATE) else null }
    }

    private fun githubTokenKeyFiles(): List<String?> =
        listOf("cursor_secure", "cursor_secure_bak").map { encrypted(it).getString("github_token", null) } +
            context.getSharedPreferences("cursor_secure_fallback", Context.MODE_PRIVATE).getString("github_token", null)

    @Test
    fun oldGithubTokenBecomesTheGithubConnectionAndLegacyCopyIsRemoved() {
        seedOldSecrets()
        val store = open()
        assertEquals("ghp_old", store.githubToken)
        val github = store.forges().single { it.provider == ForgeKind.GITHUB.id }
        assertEquals("ghp_old", github.token)
        assertEquals("key-old", store.apiKey)
        assertTrue(githubTokenKeyFiles().all { it == null })
    }

    @Test
    fun upgradeSurvivesRepeatedLaunches() {
        seedOldSecrets()
        open()
        val again = open()
        assertEquals("ghp_old", again.githubToken)
        assertEquals(1, again.forges().count { it.provider == ForgeKind.GITHUB.id })
        assertEquals("key-old", again.apiKey)
    }

    @Test
    fun existingForgeTokenIsNeverOverwrittenByTheLegacyOne() {
        val first = open()
        first.githubToken = "ghp_new"
        encrypted("cursor_secure").edit(commit = true) { putString("github_token", "ghp_stale") }
        context.getSharedPreferences("cursor_prefs", Context.MODE_PRIVATE).edit(commit = true) { remove("secrets_schema") }
        val second = open()
        assertEquals("ghp_new", second.githubToken)
        assertTrue(githubTokenKeyFiles().all { it == null })
    }

    @Test
    fun unreadableForgeListKeepsTheLegacyTokenAndIsNotOverwritten() {
        seedOldSecrets()
        encrypted("cursor_secure").edit(commit = true) { putString("forges", "{not json") }
        encrypted("cursor_secure_bak").edit(commit = true) { putString("forges", "{not json") }
        val store = open()
        assertEquals("ghp_old", store.githubToken)
        assertEquals("{not json", encrypted("cursor_secure").getString("forges", null))
        assertEquals("ghp_old", encrypted("cursor_secure").getString("github_token", null))
    }

    @Test
    fun oldPlaintextFallbackTokenIsMigratedToo() {
        context.getSharedPreferences("cursor_secure_fallback", Context.MODE_PRIVATE)
            .edit(commit = true) { putString("github_token", "ghp_fallback"); putString("api_key", "key-fallback") }
        val store = open(secure = false)
        assertEquals("ghp_fallback", store.githubToken)
        assertEquals("key-fallback", store.apiKey)
    }

    @Test
    fun clearingTheTokenDoesNotResurrectIt() {
        seedOldSecrets()
        val store = open()
        store.githubToken = null
        assertNull(store.githubToken)
        assertNull(open().githubToken)
    }

    @Test
    fun oldPlainSettingsSurviveTheUpgrade() {
        context.getSharedPreferences("cursor_prefs", Context.MODE_PRIVATE).edit(commit = true) {
            putInt("theme_color", 0xFF123456.toInt())
            putBoolean("show_inbox_envs", false)
            putBoolean("show_inbox_remote", false)
            putBoolean("inbox_working_only", true)
            putBoolean("inbox_archived_view", true)
            putBoolean("inbox_show_hidden", true)
            putBoolean("notify_on_complete", false)
            putBoolean("show_tool_calls", false)
            putString("default_model", "claude-opus-5-5")
            putBoolean("demo_mode", true)
            putBoolean("battery_asked", true)
            putBoolean("feedback_notice_seen", true)
            putString("mcp_name", "docs")
            putString("mcp_url", "https://example.com/mcp")
        }
        context.getSharedPreferences("local_chats", Context.MODE_PRIVATE).edit(commit = true) {
            putString(
                "meta",
                """{"a1":{"title":"Keep","favorite":true,"favoritedAt":5,"muted":true,"repoUrl":"https://github.com/o/r"}}""",
            )
        }
        val prefs = UiPrefsStore.open(context).also { ui += it }
        val store = ApiKeyStore(context, prefs) { c, name -> c.getSharedPreferences(name, Context.MODE_PRIVATE) }
        assertEquals(0xFF123456.toInt(), store.themeColor)
        assertFalse(store.showInboxEnvs)
        assertFalse(store.showInboxRemote)
        assertFalse(store.notifyOnComplete)
        assertFalse(store.showToolCalls)
        assertEquals("claude-opus-5-5", store.defaultModel)
        assertTrue(store.demoMode)
        assertTrue(store.batteryAsked)
        assertTrue(store.feedbackNoticeSeen)
        val mcp = store.storedMcps().single()
        assertEquals("docs", mcp.name)
        assertEquals("https://example.com/mcp", mcp.url)
        val prefsFile = context.getSharedPreferences("cursor_prefs", Context.MODE_PRIVATE)
        assertFalse(prefsFile.contains("mcp_name"))
        assertFalse(prefsFile.contains("mcp_url"))
        val chats = LocalChatStore(prefs)
        assertTrue(chats.inboxWorkingOnly)
        assertTrue(chats.inboxShowArchived)
        assertTrue(chats.inboxShowHidden)
        val meta = chats.snapshot().getValue("a1")
        assertEquals("Keep", meta.title)
        assertTrue(meta.favorite)
        assertTrue(meta.muted)
        assertEquals("https://github.com/o/r", meta.repoUrl)
    }

    @Test
    fun chatMetaFallsBackToTheMirrorWhenTheMainCopyIsCorrupt() {
        context.getSharedPreferences("local_chats", Context.MODE_PRIVATE).edit(commit = true) { putString("meta", "{oops") }
        context.getSharedPreferences("cursor_prefs", Context.MODE_PRIVATE).edit(commit = true) {
            putString("chat_meta", """{"a1":{"title":"Mirror","pinned":true,"pinnedAt":7}}""")
        }
        val prefs = UiPrefsStore.open(context).also { ui += it }
        val meta = LocalChatStore(prefs).snapshot().getValue("a1")
        assertEquals("Mirror", meta.title)
        assertTrue(meta.pinned)
    }
}
