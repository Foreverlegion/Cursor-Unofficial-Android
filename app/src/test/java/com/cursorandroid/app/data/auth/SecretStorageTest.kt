package com.cursorandroid.app.data.auth

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.cursorandroid.app.data.repo.ForgeConnection
import com.cursorandroid.app.data.repo.ForgeKind
import com.cursorandroid.app.data.repo.StoredMcpAuth
import com.cursorandroid.app.data.repo.StoredMcpServer
import com.cursorandroid.app.data.repo.UiPrefsStore
import com.cursorandroid.app.data.repo.decodeStoredMcps
import com.cursorandroid.app.data.repo.sourceLabels
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
class SecretStorageTest {
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

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun open(secure: Boolean = true): ApiKeyStore {
        ui.forEach { it.close() }
        ui.clear()
        val p = UiPrefsStore.open(context).also { ui += it }
        return ApiKeyStore(context, p) { c, name -> if (secure) c.getSharedPreferences(name, Context.MODE_PRIVATE) else null }
    }

    private fun fallbackKeys(): Set<String> = prefs("cursor_secure_fallback").all.keys

    @Test
    fun whenTheKeystoreFailsSecretsStayInMemoryAndNothingIsWrittenToDisk() {
        val store = open(secure = false)
        assertFalse(store.secureStorage)
        store.apiKey = "key-1"
        store.saveStoredMcps(listOf(StoredMcpServer(name = "a", url = "https://a.example/mcp", headers = mapOf("Authorization" to "Bearer t"))))
        assertEquals("key-1", store.apiKey)
        assertEquals("Bearer t", store.storedMcps().single().headers["Authorization"])
        assertTrue(fallbackKeys().isEmpty())
        assertFalse(prefs("cursor_prefs").contains("mcp_url"))
        assertNull("memory only, gone after the process", open(secure = false).apiKey)
    }

    @Test
    fun anOldPlaintextFallbackStaysReadableButIsNeverWrittenAgain() {
        prefs("cursor_secure_fallback").edit(commit = true) { putString("api_key", "key-old") }
        val store = open(secure = false)
        assertEquals("key-old", store.apiKey)
        store.apiKey = "key-new"
        assertEquals("key-new", store.apiKey)
        assertEquals("the plaintext file is not rewritten", "key-old", prefs("cursor_secure_fallback").getString("api_key", null))
        store.apiKey = null
        assertNull(store.apiKey)
        assertTrue("an explicit sign-out clears it", fallbackKeys().isEmpty())
    }

    @Test
    fun fallbackDataMovesIntoTheEncryptedStoreAsSoonAsItWorks() {
        prefs("cursor_secure_fallback").edit(commit = true) {
            putString("api_key", "key-old")
            putString("mcp_list", """[{"id":"m","name":"a","url":"https://a.example/mcp","headers":{"X":"y"}}]""")
        }
        val store = open(secure = true)
        assertEquals("key-old", store.apiKey)
        assertEquals("y", store.storedMcps().single().headers["X"])
        assertEquals("key-old", prefs("cursor_secure").getString("api_key", null))
        assertEquals("key-old", prefs("cursor_secure_bak").getString("api_key", null))
        assertTrue(fallbackKeys().isEmpty())
    }

    @Test
    fun legacyPlaintextMcpMirrorMovesIntoTheEncryptedListAndIsDeleted() {
        prefs("cursor_prefs").edit(commit = true) {
            putString("mcp_name", "docs")
            putString("mcp_url", "https://example.com/mcp?token=abc")
        }
        val store = open()
        val saved = store.storedMcps().single()
        assertEquals("docs", saved.name)
        assertEquals("https://example.com/mcp?token=abc", saved.url)
        assertFalse(prefs("cursor_prefs").contains("mcp_name"))
        assertFalse(prefs("cursor_prefs").contains("mcp_url"))
        assertEquals(2, prefs("cursor_prefs").getInt("secrets_schema", 0))
        assertEquals(1, open().storedMcps().size)
    }

    @Test
    fun legacyMirrorNeverOverwritesAnExistingList() {
        val secure = prefs("cursor_secure")
        secure.edit(commit = true) { putString("mcp_list", """[{"id":"keep","name":"mine","url":"https://mine.example/mcp"}]""") }
        prefs("cursor_prefs").edit(commit = true) {
            putString("mcp_name", "docs")
            putString("mcp_url", "https://example.com/mcp")
        }
        val store = open()
        assertEquals(listOf("mine"), store.storedMcps().map { it.name })
        assertFalse(prefs("cursor_prefs").contains("mcp_url"))
    }

    @Test
    fun legacyMirrorIsKeptWhileSecureStorageIsDown() {
        prefs("cursor_prefs").edit(commit = true) {
            putString("mcp_name", "docs")
            putString("mcp_url", "https://example.com/mcp")
        }
        val store = open(secure = false)
        assertTrue(store.storedMcps().isEmpty())
        assertEquals("https://example.com/mcp", prefs("cursor_prefs").getString("mcp_url", null))
        assertTrue("retries on the next launch", prefs("cursor_prefs").getInt("secrets_schema", 0) < 2)

        val later = open(secure = true)
        assertEquals("docs", later.storedMcps().single().name)
        assertFalse(prefs("cursor_prefs").contains("mcp_url"))
    }

    @Test
    fun legacyGithubTokenSurvivesWhileSecureStorageIsDown() {
        prefs("cursor_secure_fallback").edit(commit = true) { putString("github_token", "ghp_old") }
        val down = open(secure = false)
        assertEquals("ghp_old", down.githubToken)
        assertEquals("ghp_old", prefs("cursor_secure_fallback").getString("github_token", null))
        val up = open(secure = true)
        assertEquals("ghp_old", up.githubToken)
        assertNull(prefs("cursor_secure").getString("github_token", null))
    }

    @Test
    fun savingTheListNoLongerMirrorsAUrlIntoPlainPrefs() {
        val store = open()
        store.saveStoredMcps(listOf(StoredMcpServer(name = "a", url = "https://a.example/mcp?k=secret")))
        assertFalse(prefs("cursor_prefs").contains("mcp_url"))
        assertFalse(prefs("cursor_prefs").contains("mcp_name"))
    }

    @Test
    fun oauthAndSseSurviveTheEncryptedListAndOlderEntriesStillLoad() {
        val store = open()
        store.saveStoredMcps(
            listOf(
                StoredMcpServer(
                    name = "o",
                    type = "sse",
                    url = "https://o.example/sse",
                    auth = StoredMcpAuth("cid", "sec", listOf("a")),
                ),
            ),
        )
        val back = open().storedMcps().single()
        assertEquals("sse", back.type)
        assertEquals("sec", back.auth?.clientSecret)
        assertEquals(listOf("a"), back.auth?.scopes)

        val old = decodeStoredMcps("""[{"id":"x","enabled":false,"name":"old","type":"http","url":"https://old.example/mcp","headers":{"A":"b"}}]""")
        assertEquals(false, old.single().enabled)
        assertNull(old.single().auth)
    }

    @Test
    fun savedForgesSurviveReopenWithEachTokenAndFeedTheSourceList() {
        val store = open()
        store.saveForges(
            listOf(
                ForgeConnection(id = "gh", provider = ForgeKind.GITHUB.id, token = "gh-token"),
                ForgeConnection(id = "gl", provider = ForgeKind.GITLAB.id, token = "gl-token"),
            ),
        )
        val back = open()
        assertEquals(listOf("gh-token", "gl-token"), back.forges().map { it.token })
        assertEquals("gl-token", back.forgeForLabel("GitLab")?.token)
        assertEquals(listOf("GitHub", "GitLab"), sourceLabels(emptyList(), back.forges()))
        assertEquals(listOf("gh-token", "gl-token"), open().forges().map { it.token })
    }
}

